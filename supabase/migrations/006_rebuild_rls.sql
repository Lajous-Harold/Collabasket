-- ================================================================
-- Migration 006 — Rebuild RLS complet (fixes + consolidation)
-- Collabasket v2
--
-- Consolide les corrections RLS des migrations 002–005 en un seul
-- drop & rebuild propre.
--
-- Bugs corrigés :
--   1. chicken-and-egg sur memberships_insert (création de groupe)
--   2. groups_select bloqué entre INSERT groupe et INSERT membership
--   3. lists_select faux-négatif via double indirection can_access_list
--   4. Trigger set_list_owner pour listes personnelles sans owner_user_id
--
-- Note : on_group_created trigger et handle_new_group() sont conservés.
--        Le frontend s'appuie dessus pour la création automatique du
--        membership owner. Les fixes de policy coexistent sans conflit.
-- ================================================================


-- ────────────────────────────────────────────────────────────────
-- ÉTAPE 1 — DROP toutes les policies existantes
-- ────────────────────────────────────────────────────────────────

-- profiles
DROP POLICY IF EXISTS "profiles_select"              ON public.profiles;
DROP POLICY IF EXISTS "profiles_update"              ON public.profiles;

-- devices
DROP POLICY IF EXISTS "devices_select"              ON public.devices;
DROP POLICY IF EXISTS "devices_insert"              ON public.devices;
DROP POLICY IF EXISTS "devices_update"              ON public.devices;
DROP POLICY IF EXISTS "devices_delete"              ON public.devices;

-- groups
DROP POLICY IF EXISTS "groups_select"               ON public.groups;
DROP POLICY IF EXISTS "groups_insert"               ON public.groups;
DROP POLICY IF EXISTS "groups_update"               ON public.groups;
DROP POLICY IF EXISTS "groups_delete"               ON public.groups;

-- memberships
DROP POLICY IF EXISTS "memberships_select"          ON public.memberships;
DROP POLICY IF EXISTS "memberships_insert"          ON public.memberships;
DROP POLICY IF EXISTS "memberships_update"          ON public.memberships;
DROP POLICY IF EXISTS "memberships_delete"          ON public.memberships;

-- lists
DROP POLICY IF EXISTS "lists_select"                ON public.lists;
DROP POLICY IF EXISTS "lists_insert"                ON public.lists;
DROP POLICY IF EXISTS "lists_update"                ON public.lists;
DROP POLICY IF EXISTS "lists_delete"                ON public.lists;

-- items
DROP POLICY IF EXISTS "items_select"                ON public.items;
DROP POLICY IF EXISTS "items_insert"                ON public.items;
DROP POLICY IF EXISTS "items_update"                ON public.items;
DROP POLICY IF EXISTS "items_delete"                ON public.items;

-- invitations
DROP POLICY IF EXISTS "invitations_select_admin"    ON public.invitations;
DROP POLICY IF EXISTS "invitations_select_token"    ON public.invitations;
DROP POLICY IF EXISTS "invitations_insert"          ON public.invitations;
DROP POLICY IF EXISTS "invitations_update"          ON public.invitations;
DROP POLICY IF EXISTS "invitations_delete"          ON public.invitations;

-- categories (toutes variantes connues)
DROP POLICY IF EXISTS "categories_personal_select"  ON public.categories;
DROP POLICY IF EXISTS "categories_group_select"     ON public.categories;
DROP POLICY IF EXISTS "categories_personal_insert"  ON public.categories;
DROP POLICY IF EXISTS "categories_group_insert"     ON public.categories;
DROP POLICY IF EXISTS "categories_insert"           ON public.categories;
DROP POLICY IF EXISTS "categories_personal_update"  ON public.categories;
DROP POLICY IF EXISTS "categories_group_update"     ON public.categories;
DROP POLICY IF EXISTS "categories_personal_delete"  ON public.categories;
DROP POLICY IF EXISTS "categories_group_delete"     ON public.categories;
DROP POLICY IF EXISTS "categories_delete"           ON public.categories;

-- item_history
DROP POLICY IF EXISTS "item_history_select"         ON public.item_history;
DROP POLICY IF EXISTS "item_history_insert"         ON public.item_history;
DROP POLICY IF EXISTS "item_history_update"         ON public.item_history;
DROP POLICY IF EXISTS "item_history_delete"         ON public.item_history;

-- legacy policies migration 002 (noms avec accents / format ancien)
DROP POLICY IF EXISTS "Lecture invitation par token"            ON public.invitations;
DROP POLICY IF EXISTS "Suppression liste perso ou admin groupe" ON public.lists;
DROP POLICY IF EXISTS "Modification role par admin"             ON public.memberships;


-- ────────────────────────────────────────────────────────────────
-- ÉTAPE 2 — DROP trigger set_list_owner (recréé proprement étape 5)
-- ────────────────────────────────────────────────────────────────

DROP TRIGGER IF EXISTS before_list_insert ON public.lists;


-- ────────────────────────────────────────────────────────────────
-- ÉTAPE 3 — DROP fonctions helper (ordre inverse des dépendances)
-- handle_new_user / handle_new_group / on_group_created : conservés
-- ────────────────────────────────────────────────────────────────

DROP FUNCTION IF EXISTS public.can_access_list(uuid);
DROP FUNCTION IF EXISTS public.is_group_admin(uuid);
DROP FUNCTION IF EXISTS public.is_group_member(uuid);
DROP FUNCTION IF EXISTS public.requesting_user_id();
DROP FUNCTION IF EXISTS public.set_list_owner();


-- ────────────────────────────────────────────────────────────────
-- ÉTAPE 4 — RECRÉER les fonctions helper
-- SECURITY DEFINER garantit la lecture correcte du GUC de session
-- (contexte : bug PostgREST où auth.uid() direct dans WITH CHECK
-- ne voyait pas request.jwt.claims — cf. migration 005)
-- ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.requesting_user_id()
RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER AS $$
  SELECT auth.uid();
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.memberships
    WHERE group_id = p_group_id AND user_id = auth.uid()
  );
$$;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.memberships
    WHERE group_id = p_group_id
      AND user_id  = auth.uid()
      AND role IN ('owner', 'admin')
  );
$$;

CREATE OR REPLACE FUNCTION public.can_access_list(p_list_id uuid)
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.lists l
    WHERE l.id = p_list_id
      AND (
        l.owner_user_id = auth.uid()
        OR (
          l.owner_group_id IS NOT NULL
          AND public.is_group_member(l.owner_group_id)
        )
      )
  );
$$;


-- ────────────────────────────────────────────────────────────────
-- ÉTAPE 5 — TRIGGER set_list_owner
--
-- Auto-remplit owner_user_id pour les listes personnelles si absent.
-- S'exécute BEFORE INSERT (avant évaluation RLS WITH CHECK).
-- Ne touche pas aux listes de type 'group'.
--
-- ⚠️ Ne pas supprimer — filet de sécurité intentionnel.
-- Le frontend devrait toujours envoyer owner_user_id explicitement,
-- mais le trigger couvre les cas où il est omis.
-- ────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.set_list_owner()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
  IF NEW.type = 'personal' AND NEW.owner_user_id IS NULL THEN
    NEW.owner_user_id := auth.uid();
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER before_list_insert
  BEFORE INSERT ON public.lists
  FOR EACH ROW EXECUTE FUNCTION public.set_list_owner();


-- ────────────────────────────────────────────────────────────────
-- ÉTAPE 6 — RECRÉER toutes les policies
-- requesting_user_id() utilisé partout à la place de auth.uid() direct
-- ────────────────────────────────────────────────────────────────


-- ── PROFILES ────────────────────────────────────────────────────

CREATE POLICY "profiles_select"
  ON public.profiles FOR SELECT TO authenticated
  USING (
    id = public.requesting_user_id()
    OR EXISTS (
      SELECT 1
      FROM public.memberships m1
      JOIN public.memberships m2 ON m1.group_id = m2.group_id
      WHERE m1.user_id = public.requesting_user_id()
        AND m2.user_id = profiles.id
    )
  );

CREATE POLICY "profiles_update"
  ON public.profiles FOR UPDATE TO authenticated
  USING    (id = public.requesting_user_id())
  WITH CHECK (id = public.requesting_user_id());


-- ── DEVICES ─────────────────────────────────────────────────────

CREATE POLICY "devices_select"
  ON public.devices FOR SELECT TO authenticated
  USING (user_id = public.requesting_user_id());

CREATE POLICY "devices_insert"
  ON public.devices FOR INSERT TO authenticated
  WITH CHECK (user_id = public.requesting_user_id());

CREATE POLICY "devices_update"
  ON public.devices FOR UPDATE TO authenticated
  USING    (user_id = public.requesting_user_id())
  WITH CHECK (user_id = public.requesting_user_id());

CREATE POLICY "devices_delete"
  ON public.devices FOR DELETE TO authenticated
  USING (user_id = public.requesting_user_id());


-- ── GROUPS ──────────────────────────────────────────────────────
-- FIX groups_select : OR created_by = requesting_user_id()
-- Couvre la fenêtre entre INSERT groupe et INSERT membership owner.
-- (le trigger on_group_created crée la membership en AFTER INSERT,
-- mais la policy SELECT est évaluée sur .select() dans la même requête)

CREATE POLICY "groups_select"
  ON public.groups FOR SELECT TO authenticated
  USING (
    public.is_group_member(id)
    OR created_by = public.requesting_user_id()
  );

CREATE POLICY "groups_insert"
  ON public.groups FOR INSERT TO authenticated
  WITH CHECK (created_by = public.requesting_user_id());

CREATE POLICY "groups_update"
  ON public.groups FOR UPDATE TO authenticated
  USING    (public.is_group_admin(id))
  WITH CHECK (public.is_group_admin(id));

CREATE POLICY "groups_delete"
  ON public.groups FOR DELETE TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.memberships
      WHERE group_id = groups.id
        AND user_id  = public.requesting_user_id()
        AND role = 'owner'
    )
  );


-- ── MEMBERSHIPS ─────────────────────────────────────────────────
-- FIX memberships_insert : chicken-and-egg résolu.
-- Autorise l'insertion d'un membership owner si l'user s'insère
-- lui-même sur un groupe dont il est created_by.
-- Coexiste sans conflit avec le trigger on_group_created
-- (qui insère via SECURITY DEFINER, bypass RLS de toute façon).

CREATE POLICY "memberships_select"
  ON public.memberships FOR SELECT TO authenticated
  USING (public.is_group_member(group_id));

CREATE POLICY "memberships_insert"
  ON public.memberships FOR INSERT TO authenticated
  WITH CHECK (
    public.is_group_admin(group_id)
    OR (
      user_id = public.requesting_user_id()
      AND role = 'owner'
      AND EXISTS (
        SELECT 1 FROM public.groups
        WHERE id         = group_id
          AND created_by = public.requesting_user_id()
      )
    )
  );

CREATE POLICY "memberships_update"
  ON public.memberships FOR UPDATE TO authenticated
  USING    (public.is_group_admin(group_id))
  WITH CHECK (public.is_group_admin(group_id));

CREATE POLICY "memberships_delete"
  ON public.memberships FOR DELETE TO authenticated
  USING (
    user_id = public.requesting_user_id()
    OR public.is_group_admin(group_id)
  );


-- ── LISTS ───────────────────────────────────────────────────────
-- FIX lists_select : vérification directe au lieu de can_access_list(id)
-- Élimine la double indirection et le risque de faux-négatif.
--
-- FIX lists_insert : WITH CHECK explicite + contraintes de nullité.
-- Le trigger set_list_owner gère le cas où owner_user_id est omis.

CREATE POLICY "lists_select"
  ON public.lists FOR SELECT TO authenticated
  USING (
    owner_user_id = public.requesting_user_id()
    OR (owner_group_id IS NOT NULL AND public.is_group_member(owner_group_id))
  );

CREATE POLICY "lists_insert"
  ON public.lists FOR INSERT TO authenticated
  WITH CHECK (
    (
      type = 'personal'::list_type
      AND owner_user_id = public.requesting_user_id()
      AND owner_group_id IS NULL
    )
    OR (
      type = 'group'::list_type
      AND owner_group_id IS NOT NULL
      AND public.is_group_member(owner_group_id)
      AND owner_user_id IS NULL
    )
  );

CREATE POLICY "lists_update"
  ON public.lists FOR UPDATE TO authenticated
  USING (
    owner_user_id = public.requesting_user_id()
    OR (owner_group_id IS NOT NULL AND public.is_group_member(owner_group_id))
  )
  WITH CHECK (
    owner_user_id = public.requesting_user_id()
    OR (owner_group_id IS NOT NULL AND public.is_group_member(owner_group_id))
  );

CREATE POLICY "lists_delete"
  ON public.lists FOR DELETE TO authenticated
  USING (
    (type = 'personal'::list_type AND owner_user_id = public.requesting_user_id())
    OR (type = 'group'::list_type AND public.is_group_admin(owner_group_id))
  );


-- ── ITEMS ───────────────────────────────────────────────────────

CREATE POLICY "items_select"
  ON public.items FOR SELECT TO authenticated
  USING (public.can_access_list(list_id));

CREATE POLICY "items_insert"
  ON public.items FOR INSERT TO authenticated
  WITH CHECK (
    public.can_access_list(list_id)
    AND added_by = public.requesting_user_id()
  );

CREATE POLICY "items_update"
  ON public.items FOR UPDATE TO authenticated
  USING    (public.can_access_list(list_id))
  WITH CHECK (public.can_access_list(list_id));

CREATE POLICY "items_delete"
  ON public.items FOR DELETE TO authenticated
  USING (
    added_by = public.requesting_user_id()
    OR EXISTS (
      SELECT 1 FROM public.lists l
      WHERE l.id = items.list_id
        AND l.type = 'group'::list_type
        AND public.is_group_admin(l.owner_group_id)
    )
  );


-- ── INVITATIONS ─────────────────────────────────────────────────

CREATE POLICY "invitations_select_admin"
  ON public.invitations FOR SELECT TO authenticated
  USING (public.is_group_admin(group_id));

-- Lecture publique pour valider un lien avant connexion
-- anon = visiteur non authentifié qui ouvre le lien d'invitation
CREATE POLICY "invitations_select_token"
  ON public.invitations FOR SELECT TO anon, authenticated
  USING (
    status = 'pending'::invitation_status
    AND expires_at > now()
  );

CREATE POLICY "invitations_insert"
  ON public.invitations FOR INSERT TO authenticated
  WITH CHECK (
    public.is_group_admin(group_id)
    AND invited_by = public.requesting_user_id()
  );

CREATE POLICY "invitations_update"
  ON public.invitations FOR UPDATE TO authenticated
  USING    (public.is_group_admin(group_id))
  WITH CHECK (public.is_group_admin(group_id));

CREATE POLICY "invitations_delete"
  ON public.invitations FOR DELETE TO authenticated
  USING (public.is_group_admin(group_id));


-- ── CATEGORIES ──────────────────────────────────────────────────
-- Deux policies par opération (personal + group).
-- Consolidées depuis les doublons/triplicatas de l'ancienne version.

CREATE POLICY "categories_personal_select"
  ON public.categories FOR SELECT TO authenticated
  USING (owner_user_id = public.requesting_user_id());

CREATE POLICY "categories_group_select"
  ON public.categories FOR SELECT TO authenticated
  USING (
    owner_group_id IS NOT NULL
    AND public.is_group_member(owner_group_id)
  );

CREATE POLICY "categories_personal_insert"
  ON public.categories FOR INSERT TO authenticated
  WITH CHECK (
    owner_user_id  = public.requesting_user_id()
    AND created_by = public.requesting_user_id()
    AND owner_group_id IS NULL
  );

CREATE POLICY "categories_group_insert"
  ON public.categories FOR INSERT TO authenticated
  WITH CHECK (
    owner_group_id IS NOT NULL
    AND public.is_group_member(owner_group_id)
    AND created_by    = public.requesting_user_id()
    AND owner_user_id IS NULL
  );

CREATE POLICY "categories_personal_update"
  ON public.categories FOR UPDATE TO authenticated
  USING    (owner_user_id = public.requesting_user_id())
  WITH CHECK (owner_user_id = public.requesting_user_id());

CREATE POLICY "categories_group_update"
  ON public.categories FOR UPDATE TO authenticated
  USING    (owner_group_id IS NOT NULL AND public.is_group_admin(owner_group_id))
  WITH CHECK (owner_group_id IS NOT NULL AND public.is_group_admin(owner_group_id));

CREATE POLICY "categories_personal_delete"
  ON public.categories FOR DELETE TO authenticated
  USING (owner_user_id = public.requesting_user_id());

CREATE POLICY "categories_group_delete"
  ON public.categories FOR DELETE TO authenticated
  USING (
    owner_group_id IS NOT NULL
    AND public.is_group_admin(owner_group_id)
  );


-- ── ITEM_HISTORY ────────────────────────────────────────────────
-- Le trigger handle_new_item est SECURITY DEFINER → bypass ces policies
-- pour le feed automatique (comportement voulu).

CREATE POLICY "item_history_select"
  ON public.item_history FOR SELECT TO authenticated
  USING (user_id = public.requesting_user_id());

CREATE POLICY "item_history_insert"
  ON public.item_history FOR INSERT TO authenticated
  WITH CHECK (user_id = public.requesting_user_id());

CREATE POLICY "item_history_update"
  ON public.item_history FOR UPDATE TO authenticated
  USING    (user_id = public.requesting_user_id())
  WITH CHECK (user_id = public.requesting_user_id());

CREATE POLICY "item_history_delete"
  ON public.item_history FOR DELETE TO authenticated
  USING (user_id = public.requesting_user_id());


-- ────────────────────────────────────────────────────────────────
-- RELOAD SCHEMA CACHE
-- ────────────────────────────────────────────────────────────────

NOTIFY pgrst, 'reload schema';


-- ────────────────────────────────────────────────────────────────
-- VÉRIFICATION POST-MIGRATION (exécuter séparément)
-- ────────────────────────────────────────────────────────────────

-- SELECT tablename, policyname, cmd, with_check IS NOT NULL AS has_with_check
-- FROM pg_policies
-- WHERE schemaname = 'public'
-- ORDER BY tablename, cmd;

-- SELECT proname, prosecdef FROM pg_proc
-- WHERE pronamespace = 'public'::regnamespace
--   AND proname IN ('is_group_member','is_group_admin','can_access_list',
--                   'requesting_user_id','set_list_owner');

-- SELECT tgname, tgrelid::regclass FROM pg_trigger
-- WHERE tgname IN ('before_list_insert', 'on_group_created');
