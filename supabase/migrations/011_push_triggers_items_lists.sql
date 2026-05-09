-- ============================================================
-- Collabasket v2 — Migration 011
-- Triggers push : item ajoute (1.1) + liste groupe supprimee (1.6)
-- ============================================================
-- Periemetre v1 valide :
--   1.1  Item ajoute dans une liste de groupe
--        - Destinataires : tous les membres sauf added_by
--        - Debounce 60s par (list_id, added_by)
--        - Body batch : "<auteur> a ajoute N articles : a, b, c"
--          (jusqu'a 5 noms cites, ensuite "... a ajoute N articles")
--   1.6  Liste de groupe supprimee
--        - Destinataires : tous les membres sauf l'auteur de la suppression
--        - Push immediat (ready_at = now())
--
-- Nom d'auteur affiche : nickname (memberships) > display_name
-- (profiles) > 'Quelqu''un' (fallback).
--
-- Securite : les deux trigger functions sont SECURITY DEFINER pour
-- pouvoir INSERT dans push_outbox (RLS deny-all). auth.uid() reste
-- correctement disponible dans le contexte SECURITY DEFINER chez
-- Supabase (lecture du GUC request.jwt.claims).
--
-- Idempotent : DROP TRIGGER IF EXISTS + CREATE OR REPLACE FUNCTION.
-- ============================================================


-- ─── Helper interne : nom d'affichage d'un user dans un groupe ─
-- Centralise la logique nickname > display_name > fallback.
-- Pas expose comme RPC publique (cf. revoke en fin de migration).

create or replace function public.push_author_name(
  p_user_id uuid,
  p_group_id uuid
)
returns text language sql stable security definer as $$
  select coalesce(
    (select nickname
       from public.memberships
      where user_id = p_user_id and group_id = p_group_id),
    (select display_name from public.profiles where id = p_user_id),
    'Quelqu''un'
  );
$$;


-- ============================================================
-- 1.1  Item ajoute dans liste de groupe
-- ============================================================

create or replace function public.notify_item_added()
returns trigger language plpgsql security definer as $$
declare
  v_list          public.lists%rowtype;
  v_author        text;
  v_targets       uuid[];
  v_dedupe        text;
  v_window_secs   constant int := 60;
  v_max_cited     constant int := 5;
begin
  -- Charger la liste pour decider si push pertinent
  select * into v_list from public.lists where id = new.list_id;
  if not found
     or v_list.type <> 'group'::list_type
     or v_list.owner_group_id is null
  then
    return null;
  end if;

  -- Destinataires : membres du groupe sauf l'auteur
  select array_agg(user_id)
    into v_targets
    from public.memberships
   where group_id = v_list.owner_group_id
     and user_id <> new.added_by;

  if v_targets is null or cardinality(v_targets) = 0 then
    return null;
  end if;

  v_author := public.push_author_name(new.added_by, v_list.owner_group_id);
  v_dedupe := 'item_added:' || v_list.id::text || ':' || new.added_by::text;

  -- UPSERT : si une row pending existe pour (liste, auteur), aggregate.
  -- Sinon creer une nouvelle row avec ready_at = now() + 60s.
  insert into public.push_outbox (
    event_type, target_user_ids, title, body, data,
    dedupe_key, aggregate_count, aggregate_items, ready_at
  )
  values (
    'item_added',
    v_targets,
    v_list.name,
    v_author || ' a ajoute ' || new.name,
    jsonb_build_object(
      'type',     'item_added',
      'list_id',  v_list.id,
      'group_id', v_list.owner_group_id
    ),
    v_dedupe,
    1,
    array[new.name],
    now() + make_interval(secs => v_window_secs)
  )
  on conflict (dedupe_key) where sent_at is null and dedupe_key is not null
  do update set
    aggregate_count = push_outbox.aggregate_count + 1,
    aggregate_items = case
      when array_length(push_outbox.aggregate_items, 1) >= v_max_cited
        then push_outbox.aggregate_items
      else push_outbox.aggregate_items || excluded.aggregate_items
    end,
    body = case
      when push_outbox.aggregate_count + 1 <= v_max_cited then
        v_author
        || ' a ajoute '
        || (push_outbox.aggregate_count + 1)::text
        || ' articles : '
        || array_to_string(
             push_outbox.aggregate_items || excluded.aggregate_items,
             ', '
           )
      else
        v_author
        || ' a ajoute '
        || (push_outbox.aggregate_count + 1)::text
        || ' articles'
    end;
    -- ready_at NON modifie : la fenetre reste ancree au 1er item

  return null;
end;
$$;

drop trigger if exists notify_item_added_trg on public.items;
create trigger notify_item_added_trg
  after insert on public.items
  for each row execute function public.notify_item_added();


-- ============================================================
-- 1.6  Liste de groupe supprimee
-- ============================================================

create or replace function public.notify_list_deleted()
returns trigger language plpgsql security definer as $$
declare
  v_actor   uuid := auth.uid();
  v_name    text;
  v_targets uuid[];
begin
  if old.type <> 'group'::list_type
     or old.owner_group_id is null
  then
    return null;
  end if;

  -- Destinataires : membres du groupe sauf le supprimeur
  -- (les memberships sont encore presentes, on ne supprime que la liste)
  select array_agg(user_id)
    into v_targets
    from public.memberships
   where group_id = old.owner_group_id
     and user_id <> v_actor;

  if v_targets is null or cardinality(v_targets) = 0 then
    return null;
  end if;

  v_name := public.push_author_name(v_actor, old.owner_group_id);

  insert into public.push_outbox (
    event_type, target_user_ids, title, body, data, ready_at
  )
  values (
    'list_deleted',
    v_targets,
    'Liste supprimee',
    v_name || ' a supprime la liste « ' || old.name || ' »',
    jsonb_build_object(
      'type',     'list_deleted',
      'group_id', old.owner_group_id,
      'list_id',  old.id
    ),
    now()
  );

  return null;
end;
$$;

drop trigger if exists notify_list_deleted_trg on public.lists;
create trigger notify_list_deleted_trg
  after delete on public.lists
  for each row execute function public.notify_list_deleted();


-- ─── REVOKE expositions PostgREST inutiles ────────────────────
-- push_author_name est un helper interne. On revoke EXECUTE pour
-- anon/authenticated afin qu'il n'apparaisse pas en RPC publique.

revoke execute on function public.push_author_name(uuid, uuid) from public;
revoke execute on function public.push_author_name(uuid, uuid) from anon;
revoke execute on function public.push_author_name(uuid, uuid) from authenticated;

revoke execute on function public.notify_item_added()   from public;
revoke execute on function public.notify_list_deleted() from public;

notify pgrst, 'reload schema';


-- ============================================================
-- VERIFICATION POST-MIGRATION
-- ============================================================
-- 1) Triggers en place :
--   select tgname, tgrelid::regclass
--   from pg_trigger
--   where tgname in ('notify_item_added_trg','notify_list_deleted_trg');
--
-- 2) Test fonctionnel 1.1 (en tant qu'auteur connecte) :
--   - Avoir 2 comptes membres d'un groupe avec une liste de groupe.
--   - Compte A insere 3 items en moins de 60s.
--   - Verifier dans push_outbox :
--       select event_type, aggregate_count, aggregate_items, body, ready_at
--       from push_outbox where event_type = 'item_added' order by created_at desc;
--   --> 1 seule row, aggregate_count = 3, body = "X a ajoute 3 articles : ..."
--
-- 3) Test fonctionnel 1.6 :
--   - Compte A supprime une liste de groupe.
--   - Verifier dans push_outbox :
--       select event_type, target_user_ids, body
--       from push_outbox where event_type = 'list_deleted' order by created_at desc limit 1;
--   --> body = "X a supprime la liste « ... »", target_user_ids = autres membres.
--
-- 4) Edge case : liste perso (non-group) -> aucune row dans push_outbox.
