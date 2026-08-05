# 🧺 Collabasket V2

Liste de courses **collaborative** avec mode **hors ligne**, synchronisation temps réel et **partage des dépenses** façon Tricount.

Réécriture complète de la V1 Android/Firebase en **Expo (React Native + TypeScript) + Supabase**.

## Fonctionnalités

- 🔐 **Auth** : email OTP + Google Sign-In natif (Android)
- 📝 **Listes personnelles** : créer, renommer, supprimer
- 👥 **Groupes** : rôles propriétaire / admin / membre, surnoms par groupe
- 🔗 **Invitations par lien** : token sécurisé 7 jours, deep-link `collabasket://invite/<token>` (fonctionne aussi pour un invité non connecté : le token est rejoué après l'authentification)
- 🛒 **Articles** : quantités, unités, catégories structurées (perso et par groupe), lieu de stockage (placard/frigo/congélateur), notes, prix
- 🕵️ **Historique + autocomplétion** : suggestions basées sur vos articles fréquents
- 🔍 Recherche, tri, filtres, groupement par catégorie
- 📴 **Mode hors ligne complet** :
  - cache persisté 7 jours (les listes s'affichent sans réseau, même après un cold start)
  - updates optimistes (cocher un article est instantané)
  - file de mutations rejouée automatiquement au retour du réseau — y compris après un redémarrage de l'app
  - bandeau d'état hors ligne
- 💶 **Partage des dépenses** (façon Tricount) : qui a payé quoi, répartition par participants, soldes par membre, plan de remboursement suggéré (« X doit Y à Z »), remboursements enregistrés
- 🔔 **Notifications push** server-side (outbox + pg_cron + Expo Push) avec debounce/agrégation, navigation vers la liste au tap
- 🌓 **Thème clair / sombre** : système, clair ou sombre, persisté
- ⚡ **Temps réel** : modifications des autres membres visibles immédiatement

## Stack

| Couche | Techno |
|---|---|
| App | Expo SDK 54 · React Native 0.81 · TypeScript strict · expo-router v6 |
| UI | NativeWind v4 (Tailwind) |
| Données | Supabase (Postgres + RLS + Realtime + Edge Functions) |
| État serveur | TanStack Query v5 (+ persister AsyncStorage) |
| État auth | Zustand |
| Push | expo-notifications + Expo Push API |

## Installation

### 1. Prérequis

- Node 20+
- Un projet [Supabase](https://supabase.com)
- (Push + Google OAuth) un projet Google Cloud / Firebase

### 2. Base de données

Dans le SQL Editor de Supabase, jouer **toutes les migrations dans l'ordre** :

```
supabase/migrations/001_initial_schema.sql
...jusqu'à...
supabase/migrations/016_expenses.sql
```

Ou avec la CLI : `supabase db push` (projet lié via `supabase link`).

**Étapes manuelles obligatoires pour le pipeline push** (détaillées dans les en-têtes de `010_push_outbox.sql` et `013_push_cron.sql`) :

1. Activer les extensions `pg_net`, `pg_cron` (Dashboard > Database > Extensions).
2. Créer deux secrets **Vault** (Dashboard > Settings > Vault) :
   - `supabase_url` : l'URL du projet
   - `service_role_key` : la clé service_role
3. Déployer l'edge function :
   ```bash
   supabase functions deploy send-push-notification
   ```

### 3. Variables d'environnement

```bash
cp .env.example .env
```

Renseigner `EXPO_PUBLIC_SUPABASE_URL`, `EXPO_PUBLIC_SUPABASE_KEY` (clé anon) et les client IDs Google (voir `.env.example`).

> Le code OTP email est attendu sur **6 chiffres** (défaut Supabase). Si votre projet est configuré différemment, définissez `EXPO_PUBLIC_OTP_LENGTH`.

### 4. Lancer en développement

```bash
npm install
npm run android   # ou npm start
```

### 5. Builds EAS

Chaque profil de `eas.json` référence un **environnement EAS** (`development` / `preview` / `production`). Les variables `EXPO_PUBLIC_*` doivent y être créées :

```bash
eas env:create --environment production --name EXPO_PUBLIC_SUPABASE_URL --value https://xxx.supabase.co
eas env:create --environment production --name EXPO_PUBLIC_SUPABASE_KEY --value <anon key>
eas env:create --environment production --name EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID --value <web client id>
```

Puis :

```bash
eas build --profile production --platform android
```

> ⚠️ `google-services.json` (config Firebase Android) est versionné car requis par le build. Les identifiants qu'il contient sont publics par conception ; la sécurité repose sur les restrictions SHA-1 configurées dans Google Cloud. Pour le sortir du repo : EAS file env var `GOOGLE_SERVICES_JSON` + `app.config.js`.

## Tests

- **Sécurité SQL (pgTAP)** : `supabase/tests/critical_security.sql` à exécuter dans le SQL Editor — vérifie les invariants critiques de RLS (tokens d'invitation non énumérables, `push_outbox` deny-all…).
- **CI GitHub Actions** : typecheck (`tsc --noEmit`) + ESLint sur chaque push.

## Architecture — points clés

```
app/                      routes expo-router
  (auth)/                 login (email OTP + Google), vérification
  (app)/                  tabs : Mes listes / Groupes / Profil
    groups/[groupId]/     listes du groupe, membres, dépenses
  invite/[token]          acceptation d'invitation (deep-link)
src/
  lib/
    supabase.ts           client (erreur explicite si env manquante)
    queryClient.ts        cache 7 j + persister AsyncStorage
    offline.ts            NetInfo -> onlineManager, useIsOnline
    itemMutations.ts      mutations d'articles offline-first (défauts
                          rejouables + updates optimistes + rollback)
    theme.ts              préférence de thème persistée + useNavColors
    notifications.ts      token push + navigation au tap
  hooks/                  un hook par domaine (lists, groups, items,
                          expenses, invitations, realtime…)
  utils/balances.ts       soldes et plan de remboursement (centimes)
supabase/
  migrations/             schéma versionné 001 → 016 (en-têtes documentés)
  functions/              edge function send-push-notification (Deno)
  tests/                  tests pgTAP
```

**Sécurité** : toute l'autorisation est portée par la **RLS Postgres** (une quarantaine de policies). L'UI ne fait que refléter les permissions ; un client modifié ne peut rien faire de plus que ce que les policies autorisent.

**Offline** : les mutations d'articles sont déclarées via `setMutationDefaults` avec `networkMode: 'online'` → hors ligne elles sont mises en pause, persistées, puis rejouées (même après cold start). Les articles créés hors ligne reçoivent un UUID généré côté client, ils restent donc modifiables avant synchronisation. Les autres mutations (groupes, invitations, dépenses) échouent explicitement hors ligne (`networkMode: 'always'`).

## Roadmap

- [ ] iOS : Apple Sign In (obligatoire App Store), config `associatedDomains`
- [ ] App Links HTTPS (page web de fallback pour les invitations)
- [ ] Suppression de compte (RGPD) + politique de confidentialité
- [ ] Observabilité (Sentry) et OTA updates (expo-updates)
- [ ] Types Supabase générés (`supabase gen types`) en CI
- [ ] Tests unitaires (balances, splitEqually) et E2E

---

> Projet initialement réalisé en groupe (V1 Android) par Lucas M., Lucas R., Léo M. et Harold L. — V2 Expo/Supabase.
