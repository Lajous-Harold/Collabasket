-- ============================================================
-- Collabasket v2 — Migration 012
-- RPC accept_invitation : ajout des pushes 2.1
-- ============================================================
-- Extension de la RPC accept_invitation (cree dans 002) :
--   - Apres l'INSERT membership, ajout de 2 rows dans push_outbox :
--     a) Notification a l'inviteur : "<acceptor> a rejoint <groupe>"
--     b) Welcome a l'accepteur     : "Tu as rejoint <groupe>"
--   - Aucun changement de signature ni de comportement existant.
--
-- Securite : la RPC reste SECURITY DEFINER. push_outbox a RLS deny-all,
-- la RPC y INSERT en bypass.
--
-- Note : pas de dedupe_key sur ces 2 rows (event ponctuel).
-- ============================================================

create or replace function public.accept_invitation(p_token text)
returns table (group_id uuid, group_name text)
language plpgsql security definer as $$
declare
  v_invitation   public.invitations%rowtype;
  v_user_id      uuid := auth.uid();
  v_group_name   text;
  v_acceptor     text;
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

  select name into v_group_name
    from public.groups
   where id = v_invitation.group_id;

  -- Nom d'affichage de l'accepteur dans le groupe (apres INSERT membership
  -- pour que l'eventuel nickname soit pris en compte ; sinon fallback
  -- sur display_name).
  v_acceptor := public.push_author_name(v_user_id, v_invitation.group_id);

  -- Push 2.1.a : notifier l'inviteur (uniquement)
  insert into public.push_outbox (
    event_type, target_user_ids, title, body, data, ready_at
  )
  values (
    'member_joined',
    array[v_invitation.invited_by],
    'Nouveau membre',
    v_acceptor || ' a rejoint ' || v_group_name,
    jsonb_build_object(
      'type',     'member_joined',
      'group_id', v_invitation.group_id
    ),
    now()
  );

  -- Push 2.1.b : welcome a l'accepteur lui-meme
  insert into public.push_outbox (
    event_type, target_user_ids, title, body, data, ready_at
  )
  values (
    'welcome',
    array[v_user_id],
    'Bienvenue !',
    'Tu as rejoint ' || v_group_name,
    jsonb_build_object(
      'type',     'welcome',
      'group_id', v_invitation.group_id
    ),
    now()
  );

  group_id   := v_invitation.group_id;
  group_name := v_group_name;
  return next;
end;
$$;

notify pgrst, 'reload schema';


-- ============================================================
-- VERIFICATION POST-MIGRATION
-- ============================================================
-- Test e2e :
--   - Compte A cree un groupe, genere un lien d'invitation.
--   - Compte B accepte le lien (RPC accept_invitation).
--   - select event_type, target_user_ids[1] = '<A>' as to_inviter, body
--     from push_outbox
--     where event_type in ('member_joined','welcome')
--     order by created_at desc limit 2;
--   --> 1 row member_joined target = A, body "<B> a rejoint <groupe>"
--   --> 1 row welcome       target = B, body "Tu as rejoint <groupe>"
