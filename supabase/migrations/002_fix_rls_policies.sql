-- ============================================================
-- Collabasket v2 — Fix RLS policies
-- ============================================================
-- Corrige 3 bugs decouverts a l'usage :
--   1. Creation de groupe bloquee (INSERT membership owner)
--   2. Creation de liste de groupe bloquee par effet de chaine
--   3. Acceptation d'invitation impossible (lecture + insert + update)


-- ─── 1. Auto-membership 'owner' a la creation d'un groupe ────
-- Resout : "new row violates row-level security policy for memberships"
-- au moment de la creation d'un groupe.
-- Le trigger tourne en security definer donc bypass les RLS.

create or replace function public.handle_new_group()
returns trigger language plpgsql security definer as $$
begin
  insert into public.memberships (user_id, group_id, role)
  values (new.created_by, new.id, 'owner');
  return new;
end;
$$;

drop trigger if exists on_group_created on public.groups;
create trigger on_group_created
  after insert on public.groups
  for each row execute procedure public.handle_new_group();


-- ─── 2. Acceptation d'invitation via RPC ─────────────────────
-- Une fonction security definer qui :
--   - lit l'invitation par token (bypass RLS)
--   - verifie expiry et statut
--   - cree la membership
--   - marque l'invitation acceptee
-- Renvoie le group_id et le group_name pour redirection cote client.

create or replace function public.accept_invitation(p_token text)
returns table (group_id uuid, group_name text)
language plpgsql security definer as $$
declare
  v_invitation public.invitations%rowtype;
  v_user_id uuid := auth.uid();
  v_group_name text;
begin
  if v_user_id is null then
    raise exception 'Authentification requise';
  end if;

  select * into v_invitation
  from public.invitations
  where token = p_token and status = 'pending';

  if not found then
    raise exception 'Invitation invalide ou deja utilisee';
  end if;

  if v_invitation.expires_at < now() then
    update public.invitations
      set status = 'expired'
      where id = v_invitation.id;
    raise exception 'Cette invitation a expire';
  end if;

  if exists (
    select 1 from public.memberships
    where user_id = v_user_id and group_id = v_invitation.group_id
  ) then
    raise exception 'Vous etes deja membre de ce groupe';
  end if;

  insert into public.memberships (user_id, group_id, role)
  values (v_user_id, v_invitation.group_id, 'member');

  update public.invitations
    set status = 'accepted'
    where id = v_invitation.id;

  select name into v_group_name from public.groups where id = v_invitation.group_id;

  group_id := v_invitation.group_id;
  group_name := v_group_name;
  return next;
end;
$$;


-- ─── 3. Lecture invitation par token (avant acceptation) ─────
-- Permet au UI d'afficher les infos du groupe avant d'accepter.
-- Limitee aux invitations pending et non expirees pour eviter les leaks.

create policy "Lecture invitation par token"
  on public.invitations for select using (
    status = 'pending' and expires_at > now()
  );


-- ─── 4. Suppression de listes ─────────────────────────────────
-- La policy DELETE sur lists etait absente : useDeleteList echouait.
-- Personnel : owner. Groupe : admin/owner du groupe.

drop policy if exists "Suppression liste perso ou admin groupe" on public.lists;
create policy "Suppression liste perso ou admin groupe"
  on public.lists for delete using (
    (type = 'personal' and owner_user_id = auth.uid())
    or (type = 'group' and public.is_group_admin(owner_group_id))
  );


-- ─── 5. Modification du role d'un membre ──────────────────────
-- Necessaire pour la Phase 4 (gestion roles : promouvoir admin).
-- Limite aux admins/owners du groupe.

drop policy if exists "Modification role par admin" on public.memberships;
create policy "Modification role par admin"
  on public.memberships for update using (public.is_group_admin(group_id));


-- ─── 6. Backfill : assurer une membership owner pour les groupes existants ──
-- Au cas ou des groupes auraient ete crees avant ce fix.
insert into public.memberships (user_id, group_id, role)
select g.created_by, g.id, 'owner'
from public.groups g
where not exists (
  select 1 from public.memberships m
  where m.group_id = g.id and m.user_id = g.created_by
);
