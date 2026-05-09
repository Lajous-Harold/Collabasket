-- ============================================================
-- Collabasket v2 — Migration 013
-- pg_cron : flush periodique de push_outbox
-- ============================================================
-- Fonction flush_push_outbox() :
--   - Lit les rows pending eligibles (sent_at IS NULL, ready_at <= now,
--     attempts < 5).
--   - Pour chaque, marque sent_at + attempts++, puis emet un
--     net.http_post asynchrone vers la edge function.
--   - Verrou per-row via FOR UPDATE SKIP LOCKED pour eviter le double
--     traitement si deux ticks cron se chevauchent.
--
-- Resilience v1 :
--   - Fire-and-forget (pas d'attente de la reponse HTTP).
--   - Pas de retry automatique. Si la function retourne une erreur,
--     la row reste avec sent_at non-null = "considere envoye".
--   - Operateur peut requeue manuellement :
--       update push_outbox set sent_at = null where id = '<uuid>';
--   - Trace HTTP disponible dans net._http_response (debug).
--
-- Schedule : toutes les 30 secondes (validation produit).
-- Pour passer a 10s plus tard, modifier le schedule sans changer le code :
--   select cron.alter_job(
--     (select jobid from cron.job where jobname = 'flush-push-outbox'),
--     schedule := '10 seconds'
--   );
-- ============================================================


-- ─── Fonction de flush ────────────────────────────────────────

create or replace function public.flush_push_outbox()
returns void language plpgsql security definer as $$
declare
  v_url   text;
  v_key   text;
  v_row   public.push_outbox%rowtype;
begin
  -- Recupere les secrets depuis Vault.
  -- Ces secrets doivent etre crees manuellement (cf. migration 010).
  select decrypted_secret into v_url
    from vault.decrypted_secrets
   where name = 'supabase_url'
   limit 1;

  select decrypted_secret into v_key
    from vault.decrypted_secrets
   where name = 'service_role_key'
   limit 1;

  if v_url is null or v_key is null then
    raise warning
      '[flush_push_outbox] Vault secrets supabase_url et/ou service_role_key absents. '
      'Voir migration 010 pour les creer. Cron skip ce tick.';
    return;
  end if;

  -- Boucle sur les rows pretes. SKIP LOCKED garantit qu'on ne double-traite
  -- pas si un autre tick cron tournait encore.
  for v_row in
    select *
      from public.push_outbox
     where sent_at is null
       and ready_at <= now()
       and attempts < 5
     order by ready_at asc
     limit 50
     for update skip locked
  loop
    -- Marquage optimiste : si l'http_post echoue silencieusement,
    -- la row sera consideree envoyee. Voir doc en tete de migration
    -- pour le requeue manuel.
    update public.push_outbox
       set sent_at  = now(),
           attempts = v_row.attempts + 1
     where id = v_row.id;

    -- Appel asynchrone : pg_net retourne un request_id, ne bloque pas.
    perform net.http_post(
      url     := v_url || '/functions/v1/send-push-notification',
      headers := jsonb_build_object(
        'Authorization', 'Bearer ' || v_key,
        'Content-Type',  'application/json'
      ),
      body    := jsonb_build_object(
        'outbox_id', v_row.id,
        'user_ids',  v_row.target_user_ids,
        'title',     v_row.title,
        'body',      v_row.body,
        'data',      v_row.data
      )
    );
  end loop;
end;
$$;

revoke execute on function public.flush_push_outbox() from public;
revoke execute on function public.flush_push_outbox() from anon;
revoke execute on function public.flush_push_outbox() from authenticated;


-- ─── Schedule pg_cron ─────────────────────────────────────────
-- Idempotent : unschedule si deja present, puis re-schedule.

do $$
begin
  if exists (select 1 from cron.job where jobname = 'flush-push-outbox') then
    perform cron.unschedule('flush-push-outbox');
  end if;
end
$$;

select cron.schedule(
  'flush-push-outbox',
  '30 seconds',
  $job$ select public.flush_push_outbox(); $job$
);


-- ============================================================
-- VERIFICATION POST-MIGRATION
-- ============================================================
-- 1) Job present :
--   select jobname, schedule, command, active
--   from cron.job
--   where jobname = 'flush-push-outbox';
--   --> 1 row, schedule '30 seconds', active = true.
--
-- 2) Premiere execution dans les 30s :
--   select runid, jobid, status, return_message, end_time
--   from cron.job_run_details
--   where jobid = (select jobid from cron.job where jobname = 'flush-push-outbox')
--   order by start_time desc
--   limit 5;
--
-- 3) Test e2e (apres deploiement de la edge function refactored) :
--   - Inserer manuellement une row :
--       insert into push_outbox (event_type, target_user_ids, title, body, data, ready_at)
--       values ('test', array['<your-uid>']::uuid[], 'Test', 'Hello world', '{}'::jsonb, now());
--   - Attendre 30s. Verifier :
--       select id, sent_at, attempts from push_outbox order by created_at desc limit 1;
--   --> sent_at non-null, attempts = 1.
--   - Verifier la trace HTTP :
--       select status_code, content_type, error_msg
--       from net._http_response
--       order by created desc limit 5;
--   --> 200 si tout OK.
--
-- 4) Si necessaire, baisser la frequence a 10s :
--   select cron.alter_job(
--     (select jobid from cron.job where jobname = 'flush-push-outbox'),
--     schedule := '10 seconds'
--   );
--
-- 5) Pour stopper temporairement :
--   select cron.alter_job(
--     (select jobid from cron.job where jobname = 'flush-push-outbox'),
--     active := false
--   );
