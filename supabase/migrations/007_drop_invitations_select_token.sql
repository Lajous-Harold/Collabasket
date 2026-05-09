-- ============================================================
-- Collabasket v2 — Migration 007
-- Drop policy invitations_select_token (faille securite)
-- ============================================================
-- Probleme :
--   La policy invitations_select_token autorisait `to anon, authenticated`
--   un SELECT sur toute invitation pending non expiree, sans filtre par
--   token. Un attaquant anon pouvait :
--     SELECT token FROM invitations WHERE status='pending';
--   et recuperer tous les tokens valides pour rejoindre n'importe quel
--   groupe via la RPC accept_invitation.
--
-- Constat code :
--   Le frontend (app/invite/[token].tsx + useAcceptInvitation) n'utilise
--   PAS cette policy. Il appelle directement la RPC accept_invitation
--   (SECURITY DEFINER) qui resout le token cote serveur. La policy etait
--   donc exploitable mais inutile.
--
-- Fix :
--   DROP simple. Si plus tard on veut afficher le nom du groupe avant
--   acceptation, on creera une RPC get_invitation_preview SECURITY DEFINER
--   qui retournera uniquement les champs publics ({group_name,
--   invited_by_name}) pour un token donne.
--
-- Impact :
--   - Aucun changement frontend necessaire.
--   - invitations_select_admin reste en place (admins voient les
--     invitations de leurs groupes).
--   - Idempotent : DROP IF EXISTS.
-- ============================================================

DROP POLICY IF EXISTS "invitations_select_token" ON public.invitations;

-- Reload du cache schema PostgREST
NOTIFY pgrst, 'reload schema';


-- ────────────────────────────────────────────────────────────────
-- VERIFICATION POST-MIGRATION (a executer separement)
-- ────────────────────────────────────────────────────────────────
-- Doit retourner uniquement invitations_select_admin pour SELECT :
--
-- SELECT policyname, cmd, roles
-- FROM pg_policies
-- WHERE schemaname = 'public'
--   AND tablename = 'invitations'
--   AND cmd = 'SELECT';
--
-- Test exploitation (anon doit retourner 0 ligne) :
--   set role anon;
--   select count(*) from invitations where status = 'pending';
--   reset role;
