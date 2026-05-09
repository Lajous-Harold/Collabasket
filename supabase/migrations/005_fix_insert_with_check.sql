-- ============================================================
-- Collabasket v2 — Migration 005
-- Fix : "new row violates row-level security policy"
--       sur INSERT groups et lists
-- ============================================================
-- Symptôme : lors de la création d'un groupe ou d'une liste,
--   PostgREST retourne 42501 "new row violates row-level
--   security policy".  Avec `with check (true)` le bug
--   disparaît → le problème est dans l'évaluation des WITH CHECK.
--
-- Cause : auth.uid() appelé directement dans une expression
--   de policy INSERT (contexte non-SECURITY DEFINER) peut
--   ne pas voir le GUC `request.jwt.claims` selon la version
--   PostgREST / Supabase utilisée.
--   Les helpers existants (is_group_member, is_group_admin,
--   can_access_list) sont SECURITY DEFINER → ils fonctionnent
--   car ils s'exécutent en tant que fonction-owner (postgres)
--   qui a accès au GUC de session.
--
-- Fix : ajouter requesting_user_id() SECURITY DEFINER comme
--   wrapper de auth.uid(), cohérent avec les autres helpers,
--   puis réécrire toutes les policies qui appellent auth.uid()
--   directement en WITH CHECK ou USING.
-- ============================================================


-- ─── Helper ────────────────────────────────────────────────
-- Même pattern que is_group_member / is_group_admin / can_access_list.
create or replace function public.requesting_user_id()
returns uuid
language sql
security definer
stable
as $$
  select auth.uid()
$$;


-- ─── groups ────────────────────────────────────────────────
drop policy if exists groups_insert on public.groups;
create policy groups_insert
  on public.groups for insert to authenticated
  with check (created_by = public.requesting_user_id());

drop policy if exists groups_delete on public.groups;
create policy groups_delete
  on public.groups for delete to authenticated
  using (
    exists (
      select 1 from public.memberships
      where group_id = groups.id
        and user_id  = public.requesting_user_id()
        and role     = 'owner'
    )
  );


-- ─── memberships ───────────────────────────────────────────
drop policy if exists memberships_delete on public.memberships;
create policy memberships_delete
  on public.memberships for delete to authenticated
  using (
    user_id = public.requesting_user_id()
    or public.is_group_admin(group_id)
  );


-- ─── lists ─────────────────────────────────────────────────
drop policy if exists lists_insert on public.lists;
create policy lists_insert
  on public.lists for insert to authenticated
  with check (
    (type = 'personal' and owner_user_id = public.requesting_user_id())
    or (type = 'group'  and public.is_group_member(owner_group_id))
  );

drop policy if exists lists_delete on public.lists;
create policy lists_delete
  on public.lists for delete to authenticated
  using (
    (type = 'personal' and owner_user_id = public.requesting_user_id())
    or (type = 'group'  and public.is_group_admin(owner_group_id))
  );


-- ─── items ─────────────────────────────────────────────────
drop policy if exists items_insert on public.items;
create policy items_insert
  on public.items for insert to authenticated
  with check (
    public.can_access_list(list_id)
    and added_by = public.requesting_user_id()
  );

drop policy if exists items_delete on public.items;
create policy items_delete
  on public.items for delete to authenticated
  using (
    added_by = public.requesting_user_id()
    or exists (
      select 1 from public.lists l
      where l.id = items.list_id
        and l.type = 'group'
        and public.is_group_admin(l.owner_group_id)
    )
  );


-- ─── profiles ──────────────────────────────────────────────
drop policy if exists profiles_select on public.profiles;
create policy profiles_select
  on public.profiles for select to authenticated
  using (
    id = public.requesting_user_id()
    or exists (
      select 1
      from public.memberships m1
      join public.memberships m2 on m1.group_id = m2.group_id
      where m1.user_id = public.requesting_user_id()
        and m2.user_id = profiles.id
    )
  );

drop policy if exists profiles_update on public.profiles;
create policy profiles_update
  on public.profiles for update to authenticated
  using   (id = public.requesting_user_id())
  with check (id = public.requesting_user_id());


-- ─── devices ───────────────────────────────────────────────
drop policy if exists devices_select on public.devices;
create policy devices_select
  on public.devices for select to authenticated
  using (user_id = public.requesting_user_id());

drop policy if exists devices_insert on public.devices;
create policy devices_insert
  on public.devices for insert to authenticated
  with check (user_id = public.requesting_user_id());

drop policy if exists devices_update on public.devices;
create policy devices_update
  on public.devices for update to authenticated
  using   (user_id = public.requesting_user_id())
  with check (user_id = public.requesting_user_id());

drop policy if exists devices_delete on public.devices;
create policy devices_delete
  on public.devices for delete to authenticated
  using (user_id = public.requesting_user_id());


-- ─── invitations ───────────────────────────────────────────
drop policy if exists invitations_insert on public.invitations;
create policy invitations_insert
  on public.invitations for insert to authenticated
  with check (
    public.is_group_admin(group_id)
    and invited_by = public.requesting_user_id()
  );


-- ─── item_history ──────────────────────────────────────────
drop policy if exists item_history_select on public.item_history;
create policy item_history_select
  on public.item_history for select to authenticated
  using (user_id = public.requesting_user_id());

drop policy if exists item_history_insert on public.item_history;
create policy item_history_insert
  on public.item_history for insert to authenticated
  with check (user_id = public.requesting_user_id());

drop policy if exists item_history_update on public.item_history;
create policy item_history_update
  on public.item_history for update to authenticated
  using   (user_id = public.requesting_user_id())
  with check (user_id = public.requesting_user_id());

drop policy if exists item_history_delete on public.item_history;
create policy item_history_delete
  on public.item_history for delete to authenticated
  using (user_id = public.requesting_user_id());


-- ─── categories (cohérence avec migration 004) ─────────────
-- Les policies INSERT de categories utilisent auth.uid() dans
-- 004_categories.sql. On les réécrit ici pour cohérence.
-- Si 004 n'a pas encore été appliquée, ces DROP sont no-ops.
do $$
begin
  -- Seulement si la table categories existe
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'public' and table_name = 'categories'
  ) then
    drop policy if exists categories_insert on public.categories;
    execute $pol$
      create policy categories_insert
        on public.categories for insert to authenticated
        with check (
          (owner_user_id  is not null and owner_user_id  = public.requesting_user_id() and owner_group_id is null)
          or
          (owner_group_id is not null and owner_group_id in (
            select group_id from public.memberships where user_id = public.requesting_user_id()
          ) and owner_user_id is null)
        )
    $pol$;

    drop policy if exists categories_delete on public.categories;
    execute $pol$
      create policy categories_delete
        on public.categories for delete to authenticated
        using (
          (owner_user_id  = public.requesting_user_id())
          or
          (owner_group_id is not null and public.is_group_admin(owner_group_id))
        )
    $pol$;
  end if;
end
$$;


-- ─── Reload cache PostgREST ─────────────────────────────────
notify pgrst, 'reload schema';
