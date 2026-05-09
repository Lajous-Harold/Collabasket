-- ============================================================
-- Collabasket v2 — Migration 009
-- Rename devices.fcm_token -> devices.push_token
-- ============================================================
-- Probleme :
--   La colonne devices.fcm_token est mal nommee. L'app stocke en
--   realite des ExponentPushToken[...] generes par
--   Notifications.getExpoPushTokenAsync (cf. src/lib/notifications.ts),
--   pas des FCM tokens natifs. Le filtre cote Edge Function fait
--   d'ailleurs t.startsWith("ExponentPushToken[").
--
-- Fix :
--   ALTER TABLE devices RENAME COLUMN fcm_token TO push_token.
--   PostgreSQL renomme automatiquement les references dans les
--   contraintes/index. Le nom de la contrainte UNIQUE
--   (devices_user_id_fcm_token_key) reste tel quel cote DB --
--   c'est purement cosmetique, on ne le renomme pas pour eviter
--   d'avoir a recreer l'index.
--
-- Fichiers a synchroniser apres cette migration (deja faits dans
-- la meme PR) :
--   - src/types/database.ts        : fcm_token -> push_token
--   - src/lib/notifications.ts     : upsert + onConflict
--   - supabase/functions/send-push-notification/index.ts
--                                  : refait dans 010+ (nouvelle archi)
--   - CLAUDE.md                    : doc schema devices
--
-- Idempotent : guard via information_schema.
-- ============================================================

do $$
begin
  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name   = 'devices'
      and column_name  = 'fcm_token'
  ) then
    alter table public.devices rename column fcm_token to push_token;
  end if;
end
$$;

-- Reload du cache schema PostgREST (la colonne change de nom)
notify pgrst, 'reload schema';


-- ============================================================
-- VERIFICATION POST-MIGRATION
-- ============================================================
-- 1) Colonne presente avec le nouveau nom :
--
--   select column_name from information_schema.columns
--   where table_schema = 'public' and table_name = 'devices'
--   order by ordinal_position;
--   --> doit lister push_token, plus de fcm_token.
--
-- 2) Index unique toujours present (nom = ancien) :
--
--   select indexname from pg_indexes
--   where schemaname = 'public' and tablename = 'devices';
--   --> devices_user_id_fcm_token_key (le nom est cosmetique)
--
-- 3) App test :
--   - Demarrer l'app, se connecter, accepter la permission notif.
--   - select user_id, push_token, platform, updated_at from devices
--     where user_id = '<uid>';
--   --> push_token = ExponentPushToken[...] et updated_at recent.
