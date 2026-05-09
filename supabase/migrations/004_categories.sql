-- ============================================================
-- Collabasket v2 — Migration 004
-- Categories structurées
-- ============================================================
-- 1. Crée la table categories (owner = user OU groupe)
-- 2. Ajoute items.category_id FK → categories (nullable)
-- 3. Supprime items.category (text)
-- 4. Met à jour handle_new_item (retire la ref items.category)
-- 5. RLS categories
-- Idempotent : peut être re-exécuté sans erreur.
-- ============================================================


-- ============================================================
-- SECTION 1 — TABLE categories
-- ============================================================

create table if not exists public.categories (
  id             uuid primary key default gen_random_uuid(),
  name           text not null,
  color          text,
  owner_user_id  uuid references public.profiles(id)  on delete cascade,
  owner_group_id uuid references public.groups(id)    on delete cascade,
  created_by     uuid not null references public.profiles(id),
  created_at     timestamptz default now(),
  constraint categories_single_owner check (
    (owner_user_id is not null)::int + (owner_group_id is not null)::int = 1
  )
);

-- Unicité du nom par propriétaire (case-insensitive)
create unique index if not exists categories_name_user_unique
  on public.categories (lower(name), owner_user_id)
  where owner_user_id is not null;

create unique index if not exists categories_name_group_unique
  on public.categories (lower(name), owner_group_id)
  where owner_group_id is not null;


-- ============================================================
-- SECTION 2 — MIGRATION items : category text → category_id
-- ============================================================

alter table public.items
  add column if not exists category_id uuid references public.categories(id) on delete set null;

-- Note : les items existants auront category_id = null (attendu).
alter table public.items
  drop column if exists category;


-- ============================================================
-- SECTION 3 — MISE À JOUR DU TRIGGER handle_new_item
-- ============================================================
-- items.category n'existe plus. Le trigger ne la référence plus.
-- item_history.category reste présent mais n'est plus alimenté
-- par ce trigger (valeurs historiques conservées, nouvelles = null).

create or replace function public.handle_new_item()
returns trigger language plpgsql security definer as $$
begin
  insert into public.item_history (
    user_id, name, unit, default_quantity, last_used_at, use_count
  )
  values (
    new.added_by,
    new.name,
    new.unit,
    coalesce(new.quantity, 1),
    now(),
    1
  )
  on conflict (user_id, lower(name)) do update
    set last_used_at     = now(),
        use_count        = public.item_history.use_count + 1,
        unit             = coalesce(excluded.unit,             public.item_history.unit),
        default_quantity = coalesce(excluded.default_quantity, public.item_history.default_quantity);

  return new;
end;
$$;


-- ============================================================
-- SECTION 4 — RLS categories
-- ============================================================

alter table public.categories enable row level security;

drop policy if exists categories_personal_select on public.categories;
drop policy if exists categories_personal_insert on public.categories;
drop policy if exists categories_personal_update on public.categories;
drop policy if exists categories_personal_delete on public.categories;
drop policy if exists categories_group_select    on public.categories;
drop policy if exists categories_group_insert    on public.categories;
drop policy if exists categories_group_update    on public.categories;
drop policy if exists categories_group_delete    on public.categories;

-- Catégories personnelles : propriétaire uniquement
create policy categories_personal_select
  on public.categories for select to authenticated
  using (owner_user_id = auth.uid());

create policy categories_personal_insert
  on public.categories for insert to authenticated
  with check (owner_user_id = auth.uid() and created_by = auth.uid());

create policy categories_personal_update
  on public.categories for update to authenticated
  using  (owner_user_id = auth.uid())
  with check (owner_user_id = auth.uid());

create policy categories_personal_delete
  on public.categories for delete to authenticated
  using (owner_user_id = auth.uid());

-- Catégories de groupe : lecture/création membres, modif/suppr admins
create policy categories_group_select
  on public.categories for select to authenticated
  using (owner_group_id is not null and public.is_group_member(owner_group_id));

create policy categories_group_insert
  on public.categories for insert to authenticated
  with check (
    owner_group_id is not null
    and public.is_group_member(owner_group_id)
    and created_by = auth.uid()
  );

create policy categories_group_update
  on public.categories for update to authenticated
  using  (owner_group_id is not null and public.is_group_admin(owner_group_id))
  with check (owner_group_id is not null and public.is_group_admin(owner_group_id));

create policy categories_group_delete
  on public.categories for delete to authenticated
  using (owner_group_id is not null and public.is_group_admin(owner_group_id));


-- ============================================================
-- SECTION 5 — RELOAD SCHEMA CACHE
-- ============================================================

notify pgrst, 'reload schema';
