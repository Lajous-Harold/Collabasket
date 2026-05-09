-- ============================================================
-- Collabasket v2 — Tests pgTAP critiques
-- ============================================================
-- Tests de regression sur les fixes de securite et l'isolation
-- des donnees push.
--
-- Pre-requis :
--   - Extension pgtap activee (Database > Extensions dans dashboard).
--   - Execute en SQL Editor (postgres role par defaut sur Supabase).
--
-- Usage :
--   - Coller le contenu integral dans SQL Editor et executer.
--   - Le block est wrappe dans BEGIN; ... ROLLBACK; -- aucun effet de
--     bord meme si un test echoue.
--   - Sortie attendue : 4 lignes "ok N - <description>" + resume.
--
-- Couverture :
--   1. La policy invitations_select_token (fuite Wave 1 Point A) est
--      bien droppee et n'a pas reapparu dans une migration ulterieure.
--   2. push_outbox a RLS active.
--   3. push_outbox a 0 policies (deny-all : seules les fonctions
--      SECURITY DEFINER peuvent y ecrire).
--   4. accept_invitation rejette un token inconnu/expire.
--
-- A jouer manuellement apres chaque migration. Plus tard, integrable
-- en CI contre un projet Supabase de staging via supabase test db.
-- ============================================================

begin;

select plan(4);


-- ────────────────────────────────────────────────────────────
-- Test 1 : Wave 1 Point A — policy invitations_select_token absente
-- ────────────────────────────────────────────────────────────
select is_empty(
  $$select policyname from pg_policies
    where schemaname = 'public'
      and tablename  = 'invitations'
      and policyname = 'invitations_select_token'$$,
  'invitations_select_token doit etre absente (Wave 1 Point A)'
);


-- ────────────────────────────────────────────────────────────
-- Test 2 : push_outbox RLS active
-- ────────────────────────────────────────────────────────────
select ok(
  (select relrowsecurity from pg_class
    where relname = 'push_outbox'
      and relnamespace = 'public'::regnamespace),
  'RLS doit etre active sur push_outbox'
);


-- ────────────────────────────────────────────────────────────
-- Test 3 : push_outbox aucune policy (deny-all)
-- ────────────────────────────────────────────────────────────
select is(
  (select count(*)::int from pg_policies
    where schemaname = 'public' and tablename = 'push_outbox'),
  0,
  'push_outbox doit avoir 0 policies (deny-all pour anon/authenticated)'
);


-- ────────────────────────────────────────────────────────────
-- Test 4 : accept_invitation rejette un token inconnu
-- ────────────────────────────────────────────────────────────
-- "Token inconnu" et "token expire" partagent la meme branche de
-- rejet (SELECT INTO + NOT FOUND). Tester un token bidon couvre
-- donc indirectement le cas expire.
--
-- Setup : fake JWT context pour passer la verification auth.uid()
-- au debut de la fonction. Sans ca, on tomberait sur l'exception
-- 'Authentification requise' avant d'atteindre la verif token.
set local "request.jwt.claims" to
  '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}';

select throws_ok(
  $$select * from public.accept_invitation('00000000000000000000000000000000')$$,
  'P0001',
  'Invitation invalide ou deja utilisee',
  'accept_invitation doit rejeter un token inconnu/non-pending'
);


select * from finish();

rollback;
