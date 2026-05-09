-- ============================================================
-- Collabasket v2 — Migration 014
-- handle_new_user adapte aux metadonnees OAuth
-- ============================================================
-- Probleme :
--   La version 001 du trigger lit uniquement
--   raw_user_meta_data->>'display_name', qui n'est pose que par le
--   flow email OTP custom. Pour les flows OAuth (Google maintenant,
--   Apple plus tard), Supabase remplit raw_user_meta_data avec les
--   claims OIDC standards : name / full_name / picture / avatar_url.
--   Aucune cle 'display_name' -> tous les nouveaux comptes OAuth se
--   retrouvent avec display_name = 'Utilisateur' et photo_url = NULL.
--
-- Fix :
--   Reecrire handle_new_user() avec une fallback chain robuste :
--     display_name :
--       raw_user_meta_data->>'display_name'   (email OTP custom)
--       > raw_user_meta_data->>'full_name'    (Google parfois)
--       > raw_user_meta_data->>'name'         (Google standard, Apple 1ere connexion)
--       > split_part(email, '@', 1)           (fallback hash visible)
--       > 'Utilisateur'                       (filet ultime)
--     photo_url :
--       raw_user_meta_data->>'avatar_url'     (Supabase normalisee)
--       > raw_user_meta_data->>'picture'      (claim OIDC brut)
--       > NULL
--
--   Le trigger on_auth_user_created (cree dans 001) reference la
--   fonction par nom et persiste -- on ne fait que CREATE OR REPLACE
--   du corps.
--
-- Apple gotcha (documentation, pas un fix ici) :
--   Apple n'envoie le name QU'A LA PREMIERE connexion. Si un user
--   supprime son compte Supabase puis se reconnecte avec le meme
--   Apple ID, raw_user_meta_data->>'name' est vide -> on tombe sur
--   le fallback email-prefix. Pour un email private relay
--   (xyz@privaterelay.appleid.com), le prefix est un hash opaque.
--   L'utilisateur pourra editer display_name dans /profile.
--
-- Backfill : aucun. Confirmation utilisateur : zero compte de prod
--   a migrer a ce stade.
--
-- Idempotent : CREATE OR REPLACE FUNCTION.
-- ============================================================

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
begin
  insert into public.profiles (id, display_name, photo_url)
  values (
    new.id,
    coalesce(
      nullif(trim(new.raw_user_meta_data->>'display_name'), ''),
      nullif(trim(new.raw_user_meta_data->>'full_name'),    ''),
      nullif(trim(new.raw_user_meta_data->>'name'),         ''),
      nullif(split_part(new.email, '@', 1),                 ''),
      'Utilisateur'
    ),
    coalesce(
      nullif(trim(new.raw_user_meta_data->>'avatar_url'), ''),
      nullif(trim(new.raw_user_meta_data->>'picture'),    '')
    )
  );
  return new;
end;
$$;

notify pgrst, 'reload schema';


-- ============================================================
-- VERIFICATION POST-MIGRATION
-- ============================================================
-- 1) Definition a jour :
--   select pg_get_functiondef(oid)
--   from pg_proc
--   where proname = 'handle_new_user'
--     and pronamespace = 'public'::regnamespace;
--   --> doit contenir le coalesce display_name/full_name/name.
--
-- 2) Trigger on_auth_user_created toujours en place et actif :
--   select tgname, tgenabled
--   from pg_trigger
--   where tgname = 'on_auth_user_created';
--   --> tgenabled = 'O' (origin/local, soit actif).
--
-- 3) Test regression email OTP avec display_name explicite :
--   - Cote app : appel signUp ou signInWithOtp avec
--     options: { data: { display_name: 'Test User' } }
--   - select display_name, photo_url
--     from profiles where id = '<new-uid>';
--   --> display_name = 'Test User', photo_url = NULL.
--
-- 4) Test email OTP sans display_name (fallback email-prefix) :
--   - Inscription par email seul (sans options.data.display_name)
--   - select display_name from profiles where id = '<new-uid>';
--   --> display_name = portion avant '@' de l'email.
--
-- 5) Test Google OAuth (apres ajout du provider dans Wave OAuth) :
--   - signInWithIdToken provider 'google'
--   - select display_name, photo_url from profiles
--     where id = '<new-uid>';
--   --> display_name = nom Google, photo_url = URL Google avatar.
--
-- 6) Inspection raw_user_meta_data brut (debug si besoin) :
--   select id, email, raw_user_meta_data
--   from auth.users
--   order by created_at desc limit 3;
