-- ============================================================
-- Collabasket v2 — Migration 010
-- Push notifications : table outbox + extensions
-- ============================================================
-- Architecture cible (validee Wave 1 Point C, option C2) :
--
--   [Trigger DB / RPC] --INSERT--> push_outbox --pg_cron(30s)-->
--   pg_net.http_post -> Edge Function send-push-notification
--                            (stateless, batch, dead-token purge)
--
-- Une seule source de verite (push_outbox), tout versionne dans les
-- migrations, retry possible en remettant sent_at = NULL.
--
-- Cette migration :
--   1. Active les extensions pg_net, pg_cron, vault
--   2. Cree la table push_outbox + index + RLS deny-all
--   3. Aucun trigger ni cron job ici : ils arrivent en 011/012/013
--
-- Idempotent : create extension if not exists, create table if not
-- exists, create index if not exists.
-- ============================================================


-- ============================================================
-- ⚠️  ACTIONS MANUELLES PREALABLES ⚠️
-- ============================================================
-- Dans le Dashboard Supabase, AVANT de jouer cette migration :
--
-- 1. Database -> Extensions : activer
--    - pg_net    (HTTP client cote Postgres)
--    - pg_cron   (scheduler natif PG)
--    - vault     (secrets chiffres ; deja installe sur Supabase mais
--                 verifier qu'il est "Enabled" sinon l'activer)
--
-- 2. Apres execution de cette migration, creer 2 secrets dans Vault.
--    Au choix :
--
--    a) Via SQL Editor (recommande, reproductible) :
--
--       select vault.create_secret(
--         'https://<project-ref>.supabase.co',
--         'supabase_url',
--         'URL publique du projet, utilisee par le cron pour appeler
--          la edge function send-push-notification'
--       );
--
--       select vault.create_secret(
--         '<service-role-key-du-projet>',
--         'service_role_key',
--         'Service role JWT, utilise par le cron pour invoquer la
--          edge function en bypassant RLS et verify_jwt'
--       );
--
--    b) Via Dashboard : Project Settings -> Vault -> New secret
--
--    Le service_role_key est dans Project Settings -> API -> "service_role".
--    NE PAS le commit dans le repo. NE PAS le coller dans .env.local non
--    plus -- il reste cote Postgres uniquement.
--
-- 3. Si l'environnement change (clone, restore, nouveau projet) :
--    rejouer les vault.create_secret avec les nouvelles valeurs.
--    Sans ces secrets, flush_push_outbox() (migration 013) emet un
--    warning et ne fait rien.
-- ============================================================


-- ─── Extensions ────────────────────────────────────────────────
-- Schema "extensions" est la convention Supabase pour les extensions
-- non-systeme. pg_cron est traditionnellement dans son propre schema
-- "cron" (cree automatiquement). vault aussi (schema "vault").

create extension if not exists pg_net    with schema extensions;
create extension if not exists pg_cron;
create extension if not exists supabase_vault;


-- ─── Table push_outbox ────────────────────────────────────────
-- Une row = un push a envoyer a un ou plusieurs destinataires.
-- Le batching de l'event item_added (debounce 60s par liste/auteur)
-- est realise par UPSERT sur dedupe_key tant que sent_at IS NULL.

create table if not exists public.push_outbox (
  id              uuid primary key default gen_random_uuid(),

  -- Type semantique de l'event (item_added, list_deleted, member_joined,
  -- welcome, ...). Utile pour debug et metrics.
  event_type      text not null,

  -- Destinataires (ids profiles). Resolus en push_token cote function.
  target_user_ids uuid[] not null,

  -- Contenu du push
  title           text not null,
  body            text not null,
  data            jsonb not null default '{}'::jsonb,

  -- Cle de deduplication pour le batching (ex: 'item_added:<list>:<auteur>').
  -- NULL pour les events sans batching (list_deleted, member_joined, welcome).
  dedupe_key      text,
  aggregate_count integer not null default 1,
  aggregate_items text[]  not null default '{}'::text[],

  -- Quand cette row devient eligible au flush.
  -- Pour item_added : now() + 60s a la creation, fixe (anchored sur 1er item).
  -- Pour les autres : now() = envoi immediat au prochain tick cron.
  ready_at        timestamptz not null default now(),

  -- NULL = en attente. Non-null = deja flushee (timestamp d'envoi).
  -- Le mecanisme de retry consiste a remettre cette colonne a NULL
  -- (manuel pour l'instant -- pas d'automatisme en v1).
  sent_at         timestamptz,

  attempts        integer not null default 0,
  last_error      text,
  created_at      timestamptz not null default now()
);

-- Index pour le scan du cron : scan rapide des rows pretes a flusher
create index if not exists push_outbox_pending_idx
  on public.push_outbox (ready_at)
  where sent_at is null;

-- Index unique partiel pour le batching : tant que sent_at IS NULL,
-- une seule row par dedupe_key. Une fois sent_at non-null, la cle est
-- liberee et un nouvel event recree une row.
create unique index if not exists push_outbox_dedupe_pending_idx
  on public.push_outbox (dedupe_key)
  where sent_at is null and dedupe_key is not null;


-- ─── Securite : RLS deny-all ──────────────────────────────────
-- push_outbox ne doit JAMAIS etre accessible aux clients
-- (anon, authenticated). Seules les fonctions SECURITY DEFINER
-- (triggers, RPC accept_invitation, flush_push_outbox) ecrivent.
-- Le service_role bypass RLS de toute facon -- pas besoin de policy.

alter table public.push_outbox enable row level security;

-- Pas de policy creee = pas d'acces pour anon/authenticated.
-- C'est intentionnel.

-- Reload PostgREST schema (la table existe maintenant)
notify pgrst, 'reload schema';


-- ============================================================
-- VERIFICATION POST-MIGRATION
-- ============================================================
-- 1) Extensions actives :
--   select extname, extversion from pg_extension
--   where extname in ('pg_net','pg_cron','supabase_vault');
--
-- 2) Table + index :
--   select indexname from pg_indexes
--   where tablename = 'push_outbox';
--   --> push_outbox_pkey, push_outbox_pending_idx,
--       push_outbox_dedupe_pending_idx
--
-- 3) RLS active sans policy (deny-all) :
--   select relrowsecurity from pg_class where relname = 'push_outbox';
--   --> t (true)
--   select count(*) from pg_policies where tablename = 'push_outbox';
--   --> 0
--
-- 4) Test manuel (en tant qu'authenticated) :
--   set role authenticated;
--   select count(*) from public.push_outbox;
--   --> 0 rows ou erreur RLS (selon Supabase) -- pas d'acces.
--   reset role;
--
-- 5) Vault secrets crees (apres l'etape manuelle 2) :
--   select name, length(decrypted_secret) > 0 as has_value
--   from vault.decrypted_secrets
--   where name in ('supabase_url','service_role_key');
--   --> 2 lignes, has_value = true.
