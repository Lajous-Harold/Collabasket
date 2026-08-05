-- ============================================================
-- 015 — Realtime sur groups + assouplissement items_delete
-- ============================================================
--
-- PROBLÈME 1 : useRealtimeGroups écoute la table groups
-- (postgres_changes) mais 001 n'a ajouté que lists, items et
-- memberships à la publication supabase_realtime. Le listener est
-- donc inopérant : un renommage/suppression de groupe par un autre
-- membre ne se propage jamais.
--
-- PROBLÈME 2 : la policy items_delete (006) restreint le DELETE à
-- l'auteur de l'article (ou un admin du groupe). Or le bouton
-- « Vider les cochés » d'une liste de groupe fait un DELETE en masse :
-- pour un membre simple, seuls SES articles sont supprimés et
-- PostgREST ne renvoie aucune erreur pour les lignes filtrées par
-- RLS → l'utilisateur croit avoir vidé la liste alors que non.
-- C'est incohérent avec items_update, qui autorise déjà tout membre
-- à modifier n'importe quel article de la liste (cocher/décocher).
--
-- FIX :
--   1. Ajout idempotent de groups à la publication realtime.
--   2. items_delete = can_access_list(list_id), aligné sur
--      items_update : tout membre de la liste peut supprimer un
--      article (comportement attendu d'une liste de courses
--      collaborative).
--
-- VÉRIFICATION :
--   select * from pg_publication_tables
--     where pubname = 'supabase_realtime' and tablename = 'groups';
--   → 1 ligne attendue.
--   Avec deux comptes membres d'un même groupe : A ajoute un article,
--   B coche puis « vide les cochés » → l'article de A disparaît.
-- ============================================================

-- 1) Realtime sur groups (idempotent)
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'groups'
  ) then
    alter publication supabase_realtime add table public.groups;
  end if;
end $$;

-- 2) items_delete aligné sur items_update
drop policy if exists "items_delete" on public.items;

create policy "items_delete"
  on public.items for delete to authenticated
  using (public.can_access_list(list_id));

notify pgrst, 'reload schema';
