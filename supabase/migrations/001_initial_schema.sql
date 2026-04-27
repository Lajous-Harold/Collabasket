-- ============================================================
-- Collabasket v2 — Schéma Supabase / PostgreSQL
-- ============================================================
-- À exécuter dans Supabase > SQL Editor
-- L'extension pgcrypto est activée par défaut sur Supabase


-- ─── PROFILES ────────────────────────────────────────────────
-- Étend auth.users de Supabase (créé automatiquement à l'inscription)
create table public.profiles (
  id           uuid primary key references auth.users on delete cascade,
  display_name text not null,
  phone_number text unique,
  photo_url    text,
  created_at   timestamptz default now()
);

-- Déclenché automatiquement à chaque nouvel utilisateur Auth
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'display_name', 'Utilisateur'));
  return new;
end;
$$;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute procedure public.handle_new_user();


-- ─── DEVICES (tokens push multi-appareils) ───────────────────
create table public.devices (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles on delete cascade,
  fcm_token  text not null,
  platform   text check (platform in ('android', 'ios')) not null,
  updated_at timestamptz default now(),
  unique (user_id, fcm_token)
);
-- Index pour retrouver rapidement les tokens d'un user
create index idx_devices_user_id on public.devices(user_id);


-- ─── GROUPS ──────────────────────────────────────────────────
create table public.groups (
  id          uuid primary key default gen_random_uuid(),
  name        text not null,
  description text,
  created_by  uuid not null references public.profiles,
  created_at  timestamptz default now(),
  updated_at  timestamptz default now()
);


-- ─── MEMBERSHIPS ─────────────────────────────────────────────
-- Table pivot users ↔ groups avec rôle
create type membership_role as enum ('owner', 'admin', 'member');

create table public.memberships (
  id        uuid primary key default gen_random_uuid(),
  user_id   uuid not null references public.profiles on delete cascade,
  group_id  uuid not null references public.groups on delete cascade,
  role      membership_role not null default 'member',
  joined_at timestamptz default now(),
  unique (user_id, group_id)               -- un user = un rôle par groupe
);
create index idx_memberships_user_id  on public.memberships(user_id);
create index idx_memberships_group_id on public.memberships(group_id);


-- ─── LISTS ───────────────────────────────────────────────────
-- Une liste appartient soit à un user (perso) soit à un groupe
create type list_type as enum ('personal', 'group');

create table public.lists (
  id             uuid primary key default gen_random_uuid(),
  name           text not null default 'Ma liste',
  type           list_type not null,
  owner_user_id  uuid references public.profiles on delete cascade,
  owner_group_id uuid references public.groups on delete cascade,
  is_archived    boolean not null default false,
  created_at     timestamptz default now(),
  updated_at     timestamptz default now(),
  -- Contrainte : exactement un owner selon le type
  constraint lists_owner_check check (
    (type = 'personal' and owner_user_id is not null and owner_group_id is null) or
    (type = 'group'    and owner_group_id is not null and owner_user_id is null)
  )
);
create index idx_lists_owner_user  on public.lists(owner_user_id);
create index idx_lists_owner_group on public.lists(owner_group_id);


-- ─── ITEMS ───────────────────────────────────────────────────
create type storage_location as enum ('pantry', 'fridge', 'freezer');

create table public.items (
  id               uuid primary key default gen_random_uuid(),
  list_id          uuid not null references public.lists on delete cascade,
  name             text not null,
  category         text,
  unit             text,
  quantity         numeric(10,2) not null default 1,
  added_by         uuid not null references public.profiles,
  checked_by       uuid references public.profiles,
  is_checked       boolean not null default false,
  storage_location storage_location,
  created_at       timestamptz default now(),
  updated_at       timestamptz default now()
);
create index idx_items_list_id on public.items(list_id);
-- Useful pour retrouver les items cochés vs non-cochés rapidement
create index idx_items_checked  on public.items(list_id, is_checked);


-- ─── INVITATIONS ─────────────────────────────────────────────
create type invitation_status as enum ('pending', 'accepted', 'declined', 'expired');

create table public.invitations (
  id                 uuid primary key default gen_random_uuid(),
  group_id           uuid not null references public.groups on delete cascade,
  invited_by         uuid not null references public.profiles,
  contact_identifier text not null,    -- email ou numéro de téléphone
  token              text unique default encode(gen_random_bytes(32), 'hex'),
  status             invitation_status not null default 'pending',
  expires_at         timestamptz default now() + interval '7 days',
  created_at         timestamptz default now()
);
create index idx_invitations_token    on public.invitations(token);
create index idx_invitations_group_id on public.invitations(group_id);


-- ============================================================
-- ROW LEVEL SECURITY (RLS)
-- Remplace les Security Rules de Firestore — mais en SQL pur
-- ============================================================

-- Helper : est-ce que l'user est membre du groupe ?
create or replace function public.is_group_member(p_group_id uuid)
returns boolean language sql security definer stable as $$
  select exists (
    select 1 from public.memberships
    where group_id = p_group_id and user_id = auth.uid()
  );
$$;

-- Helper : est-ce que l'user est admin ou owner du groupe ?
create or replace function public.is_group_admin(p_group_id uuid)
returns boolean language sql security definer stable as $$
  select exists (
    select 1 from public.memberships
    where group_id = p_group_id
      and user_id  = auth.uid()
      and role in ('owner', 'admin')
  );
$$;

-- Helper : est-ce que l'user peut accéder à cette liste ?
create or replace function public.can_access_list(p_list_id uuid)
returns boolean language sql security definer stable as $$
  select exists (
    select 1 from public.lists l
    where l.id = p_list_id
      and (
        l.owner_user_id = auth.uid()
        or public.is_group_member(l.owner_group_id)
      )
  );
$$;

-- ── profiles ──────────────────────────────────────────────────
alter table public.profiles enable row level security;

create policy "Lecture profil propre et membres de ses groupes"
  on public.profiles for select using (
    id = auth.uid()
    or exists (
      select 1 from public.memberships m1
      join   public.memberships m2 on m1.group_id = m2.group_id
      where  m1.user_id = auth.uid() and m2.user_id = profiles.id
    )
  );

create policy "Mise à jour profil propre"
  on public.profiles for update using (id = auth.uid());

-- ── devices ───────────────────────────────────────────────────
alter table public.devices enable row level security;

create policy "CRUD devices propres"
  on public.devices for all using (user_id = auth.uid());

-- ── groups ────────────────────────────────────────────────────
alter table public.groups enable row level security;

create policy "Lecture groupes dont on est membre"
  on public.groups for select using (public.is_group_member(id));

create policy "Création libre"
  on public.groups for insert with check (created_by = auth.uid());

create policy "Mise à jour par admin"
  on public.groups for update using (public.is_group_admin(id));

create policy "Suppression par owner uniquement"
  on public.groups for delete using (
    exists (
      select 1 from public.memberships
      where group_id = groups.id and user_id = auth.uid() and role = 'owner'
    )
  );

-- ── memberships ───────────────────────────────────────────────
alter table public.memberships enable row level security;

create policy "Lecture membres du même groupe"
  on public.memberships for select using (public.is_group_member(group_id));

create policy "Ajout membre par admin"
  on public.memberships for insert with check (public.is_group_admin(group_id));

create policy "Quitter le groupe soi-même"
  on public.memberships for delete using (
    user_id = auth.uid()
    or public.is_group_admin(group_id)
  );

-- ── lists ─────────────────────────────────────────────────────
alter table public.lists enable row level security;

create policy "Lecture listes accessibles"
  on public.lists for select using (public.can_access_list(id));

create policy "Création liste perso"
  on public.lists for insert with check (
    (type = 'personal' and owner_user_id = auth.uid())
    or (type = 'group' and public.is_group_member(owner_group_id))
  );

create policy "Modification si accès"
  on public.lists for update using (public.can_access_list(id));

-- ── items ─────────────────────────────────────────────────────
alter table public.items enable row level security;

create policy "Lecture items si accès liste"
  on public.items for select using (public.can_access_list(list_id));

create policy "Ajout items si accès liste"
  on public.items for insert with check (
    public.can_access_list(list_id) and added_by = auth.uid()
  );

create policy "Modification items (cocher, éditer)"
  on public.items for update using (public.can_access_list(list_id));

create policy "Suppression par auteur ou admin groupe"
  on public.items for delete using (
    added_by = auth.uid()
    or exists (
      select 1 from public.lists l
      where l.id = items.list_id and public.is_group_admin(l.owner_group_id)
    )
  );

-- ── invitations ───────────────────────────────────────────────
alter table public.invitations enable row level security;

create policy "Lecture invitations de ses groupes"
  on public.invitations for select using (public.is_group_admin(group_id));

create policy "Créer invitation si admin"
  on public.invitations for insert with check (
    public.is_group_admin(group_id) and invited_by = auth.uid()
  );


-- ============================================================
-- REALTIME (activer les tables à écouter)
-- ============================================================
-- Dans Supabase Dashboard > Database > Replication,
-- activer le realtime sur : lists, items, memberships

-- Ou via SQL :
alter publication supabase_realtime add table public.lists;
alter publication supabase_realtime add table public.items;
alter publication supabase_realtime add table public.memberships;


-- ============================================================
-- REQUÊTES UTILES (exemples pour le frontend)
-- ============================================================

-- Toutes les listes accessibles à l'utilisateur connecté
-- (via RLS, aucun filtre supplémentaire nécessaire)
-- select * from lists order by updated_at desc;

-- Items d'une liste avec infos auteur
-- select i.*, p.display_name as added_by_name
-- from items i
-- join profiles p on p.id = i.added_by
-- where i.list_id = '<list_id>'
-- order by i.created_at;

-- Tous les membres d'un groupe avec leurs rôles
-- select p.display_name, p.photo_url, m.role, m.joined_at
-- from memberships m
-- join profiles p on p.id = m.user_id
-- where m.group_id = '<group_id>';

-- Groupes d'un utilisateur (lecture optimisée par index)
-- select g.* from groups g
-- join memberships m on m.group_id = g.id
-- where m.user_id = auth.uid();
