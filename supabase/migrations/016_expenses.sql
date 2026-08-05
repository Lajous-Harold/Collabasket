-- ============================================================
-- 016 — Partage des dépenses (façon Tricount)
-- ============================================================
--
-- NOUVEAU DOMAINE : dépenses partagées par groupe.
--
--   expenses        : une dépense (qui a payé, combien, quand)
--   expense_shares  : la répartition en euros entre participants
--                     (somme des parts = montant de la dépense,
--                     garantie par la RPC create/update_expense)
--   settlements     : les remboursements enregistrés (X a remboursé
--                     Y de Z €)
--
-- Solde d'un membre =
--     + total payé (expenses.paid_by)
--     - total dû   (expense_shares.user_id)
--     + remboursements versés (settlements.from_user)
--     - remboursements reçus  (settlements.to_user)
-- Un solde positif signifie que le groupe lui doit de l'argent.
-- Le calcul est fait côté client (src/utils/balances.ts) à partir
-- des trois tables, toutes filtrées par la RLS du groupe.
--
-- ÉCRITURE ATOMIQUE : une dépense et ses parts doivent être insérées
-- ensemble (sinon une dépense sans parts fausse tous les soldes).
-- D'où les RPC create_expense / update_expense en SECURITY INVOKER :
-- la RLS des tables s'applique intégralement, la RPC n'ajoute que
-- l'atomicité + la validation somme(parts) = montant.
--
-- RLS :
--   expenses        SELECT membre / INSERT membre (created_by = soi,
--                   paid_by membre du groupe) / UPDATE-DELETE auteur
--                   ou admin
--   expense_shares  SELECT membre / écritures auteur de la dépense
--                   ou admin (en pratique : via les RPC)
--   settlements     SELECT membre / INSERT membre (created_by = soi,
--                   from/to membres) / DELETE auteur ou admin
--
-- VÉRIFICATION :
--   select create_expense('<gid>','Courses',30,'<uid>',current_date,
--     '[{"user_id":"<uid>","amount":15},{"user_id":"<uid2>","amount":15}]'::jsonb);
--   → somme des parts ≠ montant doit lever une exception.
--   Un non-membre du groupe ne doit rien voir dans les 3 tables.
-- ============================================================

-- ── TABLES ──────────────────────────────────────────────────────

create table if not exists public.expenses (
  id           uuid primary key default gen_random_uuid(),
  group_id     uuid not null references public.groups(id) on delete cascade,
  paid_by      uuid not null references public.profiles(id) on delete cascade,
  title        text not null check (char_length(title) between 1 and 200),
  amount       numeric(10,2) not null check (amount > 0),
  expense_date date not null default current_date,
  created_by   uuid not null references public.profiles(id) on delete cascade,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create index if not exists idx_expenses_group
  on public.expenses (group_id, expense_date desc, created_at desc);

create table if not exists public.expense_shares (
  expense_id uuid not null references public.expenses(id) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  amount     numeric(10,2) not null check (amount >= 0),
  primary key (expense_id, user_id)
);

create index if not exists idx_expense_shares_user
  on public.expense_shares (user_id);

create table if not exists public.settlements (
  id         uuid primary key default gen_random_uuid(),
  group_id   uuid not null references public.groups(id) on delete cascade,
  from_user  uuid not null references public.profiles(id) on delete cascade,
  to_user    uuid not null references public.profiles(id) on delete cascade,
  amount     numeric(10,2) not null check (amount > 0),
  created_by uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint settlements_distinct_users check (from_user <> to_user)
);

create index if not exists idx_settlements_group
  on public.settlements (group_id, created_at desc);

-- updated_at automatique (réutilise set_updated_at de 008)
drop trigger if exists set_updated_at_expenses on public.expenses;
create trigger set_updated_at_expenses
  before update on public.expenses
  for each row execute function public.set_updated_at();

-- ── RLS ─────────────────────────────────────────────────────────

alter table public.expenses enable row level security;
alter table public.expense_shares enable row level security;
alter table public.settlements enable row level security;

drop policy if exists "expenses_select" on public.expenses;
create policy "expenses_select"
  on public.expenses for select to authenticated
  using (public.is_group_member(group_id));

drop policy if exists "expenses_insert" on public.expenses;
create policy "expenses_insert"
  on public.expenses for insert to authenticated
  with check (
    public.is_group_member(group_id)
    and created_by = public.requesting_user_id()
    and exists (
      select 1 from public.memberships m
      where m.group_id = expenses.group_id
        and m.user_id = expenses.paid_by
    )
  );

drop policy if exists "expenses_update" on public.expenses;
create policy "expenses_update"
  on public.expenses for update to authenticated
  using (
    created_by = public.requesting_user_id()
    or public.is_group_admin(group_id)
  )
  with check (
    public.is_group_member(group_id)
    and exists (
      select 1 from public.memberships m
      where m.group_id = expenses.group_id
        and m.user_id = expenses.paid_by
    )
  );

drop policy if exists "expenses_delete" on public.expenses;
create policy "expenses_delete"
  on public.expenses for delete to authenticated
  using (
    created_by = public.requesting_user_id()
    or public.is_group_admin(group_id)
  );

drop policy if exists "expense_shares_select" on public.expense_shares;
create policy "expense_shares_select"
  on public.expense_shares for select to authenticated
  using (
    exists (
      select 1 from public.expenses e
      where e.id = expense_shares.expense_id
        and public.is_group_member(e.group_id)
    )
  );

drop policy if exists "expense_shares_insert" on public.expense_shares;
create policy "expense_shares_insert"
  on public.expense_shares for insert to authenticated
  with check (
    exists (
      select 1 from public.expenses e
      where e.id = expense_shares.expense_id
        and (
          e.created_by = public.requesting_user_id()
          or public.is_group_admin(e.group_id)
        )
    )
  );

drop policy if exists "expense_shares_update" on public.expense_shares;
create policy "expense_shares_update"
  on public.expense_shares for update to authenticated
  using (
    exists (
      select 1 from public.expenses e
      where e.id = expense_shares.expense_id
        and (
          e.created_by = public.requesting_user_id()
          or public.is_group_admin(e.group_id)
        )
    )
  );

drop policy if exists "expense_shares_delete" on public.expense_shares;
create policy "expense_shares_delete"
  on public.expense_shares for delete to authenticated
  using (
    exists (
      select 1 from public.expenses e
      where e.id = expense_shares.expense_id
        and (
          e.created_by = public.requesting_user_id()
          or public.is_group_admin(e.group_id)
        )
    )
  );

drop policy if exists "settlements_select" on public.settlements;
create policy "settlements_select"
  on public.settlements for select to authenticated
  using (public.is_group_member(group_id));

drop policy if exists "settlements_insert" on public.settlements;
create policy "settlements_insert"
  on public.settlements for insert to authenticated
  with check (
    public.is_group_member(group_id)
    and created_by = public.requesting_user_id()
    and exists (
      select 1 from public.memberships m
      where m.group_id = settlements.group_id
        and m.user_id = settlements.from_user
    )
    and exists (
      select 1 from public.memberships m
      where m.group_id = settlements.group_id
        and m.user_id = settlements.to_user
    )
  );

drop policy if exists "settlements_delete" on public.settlements;
create policy "settlements_delete"
  on public.settlements for delete to authenticated
  using (
    created_by = public.requesting_user_id()
    or public.is_group_admin(group_id)
  );

-- ── RPC : écriture atomique dépense + parts ─────────────────────

create or replace function public.create_expense(
  p_group_id uuid,
  p_title text,
  p_amount numeric,
  p_paid_by uuid,
  p_expense_date date,
  p_shares jsonb
)
returns uuid
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_expense_id uuid;
  v_sum numeric;
begin
  select coalesce(sum((s->>'amount')::numeric), 0)
    into v_sum
  from jsonb_array_elements(p_shares) s;

  if round(v_sum, 2) is distinct from round(p_amount, 2) then
    raise exception
      'La somme des parts (%) ne correspond pas au montant (%)',
      round(v_sum, 2), round(p_amount, 2)
      using errcode = 'check_violation';
  end if;

  insert into expenses (group_id, title, amount, paid_by, expense_date, created_by)
  values (
    p_group_id,
    p_title,
    round(p_amount, 2),
    p_paid_by,
    coalesce(p_expense_date, current_date),
    requesting_user_id()
  )
  returning id into v_expense_id;

  insert into expense_shares (expense_id, user_id, amount)
  select v_expense_id, (s->>'user_id')::uuid, round((s->>'amount')::numeric, 2)
  from jsonb_array_elements(p_shares) s
  where (s->>'amount')::numeric > 0;

  return v_expense_id;
end;
$$;

create or replace function public.update_expense(
  p_expense_id uuid,
  p_title text,
  p_amount numeric,
  p_paid_by uuid,
  p_expense_date date,
  p_shares jsonb
)
returns void
language plpgsql
security invoker
set search_path = public
as $$
declare
  v_sum numeric;
begin
  select coalesce(sum((s->>'amount')::numeric), 0)
    into v_sum
  from jsonb_array_elements(p_shares) s;

  if round(v_sum, 2) is distinct from round(p_amount, 2) then
    raise exception
      'La somme des parts (%) ne correspond pas au montant (%)',
      round(v_sum, 2), round(p_amount, 2)
      using errcode = 'check_violation';
  end if;

  update expenses
  set title = p_title,
      amount = round(p_amount, 2),
      paid_by = p_paid_by,
      expense_date = coalesce(p_expense_date, expense_date)
  where id = p_expense_id;

  if not found then
    -- Soit la dépense n'existe pas, soit la RLS (auteur ou admin)
    -- a filtré la ligne.
    raise exception 'Dépense introuvable ou modification non autorisée'
      using errcode = 'insufficient_privilege';
  end if;

  delete from expense_shares where expense_id = p_expense_id;

  insert into expense_shares (expense_id, user_id, amount)
  select p_expense_id, (s->>'user_id')::uuid, round((s->>'amount')::numeric, 2)
  from jsonb_array_elements(p_shares) s
  where (s->>'amount')::numeric > 0;
end;
$$;

revoke execute on function public.create_expense(uuid, text, numeric, uuid, date, jsonb) from anon;
revoke execute on function public.update_expense(uuid, text, numeric, uuid, date, jsonb) from anon;

-- ── REALTIME ────────────────────────────────────────────────────

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public' and tablename = 'expenses'
  ) then
    alter publication supabase_realtime add table public.expenses;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public' and tablename = 'settlements'
  ) then
    alter publication supabase_realtime add table public.settlements;
  end if;
end $$;

notify pgrst, 'reload schema';
