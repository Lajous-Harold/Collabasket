# 🧺 Collabasket – Liste de courses collaborative Android

Collabasket est une application Android permettant à plusieurs utilisateurs de gérer **ensemble** leurs listes de courses. Elle fonctionne **hors ligne** avec synchronisation automatique dès qu'une connexion est disponible.

> 🔧 Projet réalisé dans le cadre d’un travail de groupe par :
> - **Lucas M.**
> - **Lucas R.**
> - **Léo M.**
> - **Harold L.**

---

## 🚀 Fonctionnalités principales

- 🔐 Authentification par téléphone + vérification par SMS
- 📱 Détection des contacts et envoi d’invitations (via lien ou SMS)
- 👥 Création de **groupes partagés**
- ✅ Liste personnelle (hors groupe) et listes de groupes séparées
- 📦 Ajout de produits avec :
    - Catégorie
    - Unité
    - Quantité
    - Ajouté par (dans les groupes)
- 🕵️ Historique des produits (perso & groupe)
- 📊 Gestion d’état du stock (placard/frigo)
- 🔔 Notifications push (produit ajouté, supprimé, membre ajouté…)
- 🛠️ Permissions de gestion de groupe (Propriétaire, Admin, Membre)
- 🔍 Recherche dynamique, tri, filtres

---

## 🧑‍💻 Installation

### 1. Prérequis
- Android Studio Dolphin ou plus récent
- Un appareil ou un émulateur Android
- Un projet Firebase configuré :
    - Authentification par téléphone & email
    - Firestore Database
    - Firebase Cloud Messaging (FCM)
    - SHA-1 du projet enregistré

### 2. Lancer le projet

```bash
git clone https://github.com/Lajous-Harold/Collabasket.git
cd collabasket
