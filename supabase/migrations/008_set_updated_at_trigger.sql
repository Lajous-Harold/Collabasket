-- ============================================================
-- Collabasket v2 — Migration 008
-- Trigger generique set_updated_at + propagation items -> lists
-- ============================================================
-- Probleme :
--   1) Les colonnes updated_at sur lists/items/groups/devices ont un
--      default now() a la creation, mais aucun trigger BEFORE UPDATE
--      ne les actualise. Resultat : updated_at == created_at a vie.
--   2) useLists et useGroupLists trient les listes par updated_at desc
--      (cf. src/hooks/useLists.ts:20, src/hooks/useGroupLists.ts:17).
--      Sans propagation depuis items, l'activite intra-liste ne fait
--      jamais remonter la liste en tete.
--
-- Fix :
--   - Fonction generique set_updated_at() + triggers BEFORE UPDATE
--     sur lists, items, groups, devices.
--   - Fonction touch_list_updated_at() + trigger AFTER INSERT/UPDATE
--     /DELETE sur items qui propage l'activite a lists.updated_at.
--
-- Tables NON couvertes (volontaire) :
--   - profiles, memberships, invitations, categories, item_history :
--     pas de colonne updated_at. Si besoin plus tard : ajouter colonne
--     puis trigger dans une migration dediee.
--
-- Effet de bord important a connaitre :
--   Un toggle is_checked sur un item declenche maintenant un UPDATE
--   sur lists, donc un event realtime sur la subscription lists.
--   Voir CLAUDE.md "Points d'attention" pour la documentation produit.
--
-- Idempotent : DROP TRIGGER IF EXISTS + CREATE OR REPLACE FUNCTION.
-- ============================================================


-- ─── Fonction generique pour BEFORE UPDATE ────────────────────
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;


-- ─── Triggers BEFORE UPDATE par table ─────────────────────────

drop trigger if exists set_updated_at_lists on public.lists;
create trigger set_updated_at_lists
  before update on public.lists
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_items on public.items;
create trigger set_updated_at_items
  before update on public.items
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_groups on public.groups;
create trigger set_updated_at_groups
  before update on public.groups
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at_devices on public.devices;
create trigger set_updated_at_devices
  before update on public.devices
  for each row execute function public.set_updated_at();


-- ─── Propagation items -> lists ───────────────────────────────
-- A chaque INSERT/UPDATE/DELETE sur items, on touche la liste parente
-- pour qu'elle remonte dans le tri par updated_at desc.
--
-- Securite : SECURITY DEFINER pas necessaire ici. Le trigger s'execute
-- dans le contexte du caller, qui a deja l'autorisation RLS lists_update
-- (toute personne pouvant editer un item peut acceder a sa liste, donc
-- la peut updater). Le trigger ne peut pas etre exploite pour toucher
-- une liste a laquelle l'utilisateur n'a pas acces parce qu'il a fallu
-- d'abord passer la policy items_*.
--
-- Note : on UPDATE meme si NEW.updated_at === OLD.updated_at, c'est
-- delibere (toggle is_checked compte comme activite).

create or replace function public.touch_list_updated_at()
returns trigger language plpgsql as $$
declare
  v_list_id uuid;
begin
  -- DELETE : on lit OLD.list_id. INSERT/UPDATE : NEW.list_id.
  if tg_op = 'DELETE' then
    v_list_id := old.list_id;
  else
    v_list_id := new.list_id;
  end if;

  update public.lists
     set updated_at = now()
   where id = v_list_id;

  return null;  -- AFTER trigger : valeur de retour ignoree
end;
$$;

drop trigger if exists touch_list_on_item_change on public.items;
create trigger touch_list_on_item_change
  after insert or update or delete on public.items
  for each row execute function public.touch_list_updated_at();


-- ============================================================
-- VERIFICATION POST-MIGRATION (a executer separement)
-- ============================================================
-- 1) Presence des 5 triggers :
--
-- select tgname, tgrelid::regclass
-- from pg_trigger
-- where tgname in (
--   'set_updated_at_lists','set_updated_at_items',
--   'set_updated_at_groups','set_updated_at_devices',
--   'touch_list_on_item_change'
-- )
-- order by tgrelid::regclass, tgname;
--
-- 2) Test BEFORE UPDATE sur lists (remplace <list_id>) :
--   select created_at, updated_at from lists where id = '<list_id>';
--   update lists set name = name where id = '<list_id>';
--   select created_at, updated_at from lists where id = '<list_id>';
--   --> updated_at doit avoir avance, created_at inchange.
--
-- 3) Test propagation items -> lists :
--   select updated_at from lists where id = '<list_id>';
--   insert into items (list_id, name, added_by) values ('<list_id>','test',auth.uid());
--   select updated_at from lists where id = '<list_id>';
--   --> updated_at lists doit avoir avance.
--
-- 4) Verification UI :
--   - Cocher un item dans une liste ancienne -> la liste remonte au top
--     de useLists/useGroupLists au prochain refresh.
