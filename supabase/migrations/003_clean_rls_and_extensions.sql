-- ============================================================
-- Collabasket v2 — Migration 003
-- Clean RLS policies + extensions metier
-- ============================================================
-- Cette migration :
--   1. Supprime toutes les policies actuelles (qui ont ete remplacees
--      par des policies permissives `for all to public using (true)` lors
--      du debug) et les recree proprement avec SELECT explicite par table.
--   2. Ajoute les nouvelles colonnes : memberships.nickname, items.notes,
--      items.price.
--   3. Cree la table item_history + son trigger d'auto-feed depuis items.
--   4. Idempotent : peut etre re-execute sans erreur.
--
-- Lecons appliquees :
--   - SELECT explicite pour toute table avec INSERT/UPDATE retournant des
--     rows (sinon 42501 sur `.insert(...).select()`).
--   - Noms de policies sans accents, format `<table>_<cmd>[_<contexte>]`.
--   - to authenticated partout, sauf invitations par token (public).
--   - notify pgrst en fin pour reload du cache schema.
-- ============================================================


-- ============================================================
-- SECTION 1 — EXTENSIONS DE COLONNES
-- ============================================================

-- ─── memberships.nickname ───────────────────────────────────
alter table public.memberships
  add column if not exists nickname text;

-- ─── items.notes & items.price ──────────────────────────────
alter table public.items
  add column if not exists notes text;

alter table public.items
  add column if not exists price numeric(10,2);


-- ============================================================
-- SECTION 2 — NOUVELLE TABLE item_history
-- ============================================================
-- Historique d'articles deja ajoutes par un user, pour autocomplete
-- et reproposer des items frequents. Insensible a la casse via
-- l'index unique sur lower(name).

create table if not exists public.item_history (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references public.profiles on delete cascade,
  name             text not null,
  category         text,
  unit             text,
  default_quantity numeric(10,2) default 1,
  last_used_at     timestamptz default now(),
  use_count        integer default 1,
  created_at       timestamptz default now()
);

-- Unicite : un nom canonique par user (case-insensitive)
create unique index if not exists item_history_user_name_unique
  on public.item_history (user_id, lower(name));

-- Index pour ordering autocomplete
create index if not exists idx_item_history_user_recent
  on public.item_history (user_id, last_used_at desc);


-- ============================================================
-- SECTION 3 — TRIGGER auto-feed item_history depuis items
-- ============================================================
-- A chaque INSERT sur items :
--   - si (added_by, lower(name)) n'existe pas dans item_history → INSERT
--   - sinon → UPDATE last_used_at = now(), use_count = use_count + 1
--
-- security definer : bypass des RLS de item_history (le user n'a
-- normalement pas a se soucier du feed automatique).

create or replace function public.handle_new_item()
returns trigger language plpgsql security definer as $$
begin
  insert into public.item_history (
    user_id, name, category, unit, default_quantity, last_used_at, use_count
  )
  values (
    new.added_by,
    new.name,
    new.category,
    new.unit,
    coalesce(new.quantity, 1),
    now(),
    1
  )
  on conflict (user_id, lower(name)) do update
    set last_used_at     = now(),
        use_count        = public.item_history.use_count + 1,
        category         = coalesce(excluded.category, public.item_history.category),
        unit             = coalesce(excluded.unit, public.item_history.unit),
        default_quantity = coalesce(excluded.default_quantity, public.item_history.default_quantity);

  return new;
end;
$$;

drop trigger if exists on_item_created on public.items;
create trigger on_item_created
  after insert on public.items
  for each row execute procedure public.handle_new_item();


-- ============================================================
-- SECTION 4 — RESET DES POLICIES (cleanup permissif debug)
-- ============================================================
-- On supprime toutes les policies existantes sur les tables concernees
-- pour repartir d'une base propre. DROP POLICY IF EXISTS evite les
-- erreurs en cas de re-execution.

do $$
declare
  pol record;
begin
  for pol in
    select schemaname, tablename, policyname
    from pg_policies
    where schemaname = 'public'
      and tablename in (
        'profiles', 'devices', 'groups', 'memberships',
        'lists', 'items', 'invitations', 'item_history'
      )
  loop
    execute format(
      'drop policy if exists %I on %I.%I',
      pol.policyname, pol.schemaname, pol.tablename
    );
  end loop;
end
$$;

-- S'assurer que RLS est bien activee sur toutes les tables.
alter table public.profiles     enable row level security;
alter table public.devices      enable row level security;
alter table public.groups       enable row level security;
alter table public.memberships  enable row level security;
alter table public.lists        enable row level security;
alter table public.items        enable row level security;
alter table public.invitations  enable row level security;
alter table public.item_history enable row level security;


-- ============================================================
-- SECTION 5 — POLICIES PROPRES
-- ============================================================

-- ─── profiles ───────────────────────────────────────────────
-- SELECT : soi-meme + tous les membres des memes groupes que soi
create policy profiles_select
  on public.profiles for select to authenticated
  using (
    id = auth.uid()
    or exists (
      select 1
      from public.memberships m1
      join public.memberships m2 on m1.group_id = m2.group_id
      where m1.user_id = auth.uid()
        and m2.user_id = profiles.id
    )
  );

-- UPDATE : son propre profil
create policy profiles_update
  on public.profiles for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());


-- ─── devices ────────────────────────────────────────────────
-- ALL : un user gere uniquement ses propres devices
create policy devices_select
  on public.devices for select to authenticated
  using (user_id = auth.uid());

create policy devices_insert
  on public.devices for insert to authenticated
  with check (user_id = auth.uid());

create policy devices_update
  on public.devices for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy devices_delete
  on public.devices for delete to authenticated
  using (user_id = auth.uid());


-- ─── groups ─────────────────────────────────────────────────
-- SELECT : membre du groupe
create policy groups_select
  on public.groups for select to authenticated
  using (public.is_group_member(id));

-- INSERT : on cree un groupe au nom de soi-meme
-- (le trigger handle_new_group cree la membership owner)
create policy groups_insert
  on public.groups for insert to authenticated
  with check (created_by = auth.uid());

-- UPDATE : admin / owner du groupe
create policy groups_update
  on public.groups for update to authenticated
  using (public.is_group_admin(id))
  with check (public.is_group_admin(id));

-- DELETE : owner uniquement
create policy groups_delete
  on public.groups for delete to authenticated
  using (
    exists (
      select 1 from public.memberships
      where group_id = groups.id
        and user_id  = auth.uid()
        and role     = 'owner'
    )
  );


-- ─── memberships ────────────────────────────────────────────
-- SELECT : tous les membres d'un groupe se voient mutuellement
create policy memberships_select
  on public.memberships for select to authenticated
  using (public.is_group_member(group_id));

-- INSERT : ajout de membre par admin du groupe.
-- (la creation de la membership owner initiale est faite par
--  trigger handle_new_group en security definer → bypass RLS)
create policy memberships_insert
  on public.memberships for insert to authenticated
  with check (public.is_group_admin(group_id));

-- UPDATE : modification du role uniquement par admin du groupe.
-- Pour le nickname, l'utilisateur passe par la RPC update_my_nickname
-- (cf. section 7) pour eviter qu'il puisse promouvoir son propre role.
create policy memberships_update
  on public.memberships for update to authenticated
  using (public.is_group_admin(group_id))
  with check (public.is_group_admin(group_id));

-- DELETE : se retirer soi-meme OU etre admin du groupe
create policy memberships_delete
  on public.memberships for delete to authenticated
  using (
    user_id = auth.uid()
    or public.is_group_admin(group_id)
  );


-- ─── lists ──────────────────────────────────────────────────
-- SELECT : proprietaire perso ou membre du groupe proprietaire
create policy lists_select
  on public.lists for select to authenticated
  using (public.can_access_list(id));

-- INSERT : perso = soi-meme, groupe = membre
create policy lists_insert
  on public.lists for insert to authenticated
  with check (
    (type = 'personal' and owner_user_id = auth.uid())
    or (type = 'group' and public.is_group_member(owner_group_id))
  );

-- UPDATE : tout user ayant acces a la liste
create policy lists_update
  on public.lists for update to authenticated
  using (public.can_access_list(id))
  with check (public.can_access_list(id));

-- DELETE : owner perso, ou admin du groupe proprietaire
create policy lists_delete
  on public.lists for delete to authenticated
  using (
    (type = 'personal' and owner_user_id = auth.uid())
    or (type = 'group' and public.is_group_admin(owner_group_id))
  );


-- ─── items ──────────────────────────────────────────────────
-- SELECT : acces a la liste parente
create policy items_select
  on public.items for select to authenticated
  using (public.can_access_list(list_id));

-- INSERT : acces liste + added_by = soi
create policy items_insert
  on public.items for insert to authenticated
  with check (
    public.can_access_list(list_id)
    and added_by = auth.uid()
  );

-- UPDATE : tout membre ayant acces a la liste (cocher, editer, etc.)
create policy items_update
  on public.items for update to authenticated
  using (public.can_access_list(list_id))
  with check (public.can_access_list(list_id));

-- DELETE : auteur OU admin du groupe proprietaire (si liste de groupe)
create policy items_delete
  on public.items for delete to authenticated
  using (
    added_by = auth.uid()
    or exists (
      select 1 from public.lists l
      where l.id = items.list_id
        and l.type = 'group'
        and public.is_group_admin(l.owner_group_id)
    )
  );


-- ─── invitations ────────────────────────────────────────────
-- SELECT 1 : admin du groupe peut voir toutes les invitations
create policy invitations_select_admin
  on public.invitations for select to authenticated
  using (public.is_group_admin(group_id));

-- SELECT 2 : lecture publique d'une invitation pending non expiree par token
-- (necessaire pour afficher le nom du groupe avant accept).
-- to public car le visiteur peut etre non authentifie au depart.
create policy invitations_select_token
  on public.invitations for select to public
  using (
    status = 'pending'
    and expires_at > now()
  );

-- INSERT : admin du groupe + invited_by = soi
create policy invitations_insert
  on public.invitations for insert to authenticated
  with check (
    public.is_group_admin(group_id)
    and invited_by = auth.uid()
  );

-- UPDATE : admin du groupe (pour annuler, marquer expirees, etc.)
-- L'acceptation passe par la RPC accept_invitation (security definer).
create policy invitations_update
  on public.invitations for update to authenticated
  using (public.is_group_admin(group_id))
  with check (public.is_group_admin(group_id));

-- DELETE : admin du groupe
create policy invitations_delete
  on public.invitations for delete to authenticated
  using (public.is_group_admin(group_id));


-- ─── item_history ───────────────────────────────────────────
-- Toutes les operations sont strictement limitees au user proprietaire.
-- Le trigger handle_new_item est security definer donc bypass ces
-- policies pour l'auto-feed, ce qui est voulu.

create policy item_history_select
  on public.item_history for select to authenticated
  using (user_id = auth.uid());

create policy item_history_insert
  on public.item_history for insert to authenticated
  with check (user_id = auth.uid());

create policy item_history_update
  on public.item_history for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy item_history_delete
  on public.item_history for delete to authenticated
  using (user_id = auth.uid());


-- ============================================================
-- SECTION 7 — RPC update_my_nickname
-- ============================================================
-- Permet a un user de modifier son propre surnom dans un groupe
-- sans pouvoir toucher a son role (qui reste sous controle admin).
-- Renvoie la membership mise a jour pour refresh cote client.

create or replace function public.update_my_nickname(
  p_group_id uuid,
  p_nickname text
)
returns public.memberships
language plpgsql security definer as $$
declare
  v_user_id uuid := auth.uid();
  v_membership public.memberships%rowtype;
begin
  if v_user_id is null then
    raise exception 'Authentification requise';
  end if;

  update public.memberships
     set nickname = nullif(trim(p_nickname), '')
   where user_id  = v_user_id
     and group_id = p_group_id
   returning * into v_membership;

  if not found then
    raise exception 'Vous n''etes pas membre de ce groupe';
  end if;

  return v_membership;
end;
$$;


-- ============================================================
-- SECTION 8 — RELOAD SCHEMA CACHE
-- ============================================================
-- Indique a PostgREST de recharger son cache pour exposer
-- les nouvelles colonnes / tables / policies.

notify pgrst, 'reload schema';
