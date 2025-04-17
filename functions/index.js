const {
  onDocumentCreated,
  onDocumentDeleted,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const sendNotification = require("./sendNotification");

initializeApp();
const db = getFirestore();

exports.onProduitAjoute = onDocumentCreated(
  { region: "europe-west1" },
  "groups/{groupId}/produits/{produitId}",
  async (event) => {
    const produit = event.data?.fields;
    const groupId = event.params.groupId;

    if (!produit) return;

    const groupSnap = await db.collection("groups").doc(groupId).get();
    const groupData = groupSnap.data();
    const groupName = groupData?.nom || "un groupe";

    const ajoutePar = produit.ajoutePar?.stringValue || "Quelqu’un";
    const nomProduit = produit.nom?.stringValue || "un produit";
    const userId = produit.userId?.stringValue;

    const title = "Produit ajouté";
    const body = `${ajoutePar} a ajouté ${nomProduit} dans ${groupName}`;

    const memberIds = groupData.memberIds || [];
    const destinataires = memberIds.filter((id) => id !== userId);

    const tokens = [];

    for (const uid of destinataires) {
      const userSnap = await db.collection("users").doc(uid).get();
      const userData = userSnap.data();

      const settings = userData.notificationsSettings || {};
      if (userData.fcmToken && settings.global !== false && settings.produitAjoute !== false) {
        tokens.push(userData.fcmToken);
      }
    }

    if (tokens.length > 0) await sendNotification(tokens, title, body);
  }
);

exports.onProduitSupprime = onDocumentDeleted(
  { region: "europe-west1" },
  "groups/{groupId}/produits/{produitId}",
  async (event) => {
    const produit = event.data?.fields;
    const groupId = event.params.groupId;

    if (!produit) return;

    const groupSnap = await db.collection("groups").doc(groupId).get();
    const groupData = groupSnap.data();
    const groupName = groupData?.nom || "un groupe";

    const supprimePar = produit.ajoutePar?.stringValue || "Quelqu’un";
    const nomProduit = produit.nom?.stringValue || "un produit";
    const userId = produit.userId?.stringValue;

    const title = "Produit supprimé";
    const body = `${supprimePar} a supprimé ${nomProduit} de ${groupName}`;

    const memberIds = groupData.memberIds || [];
    const destinataires = memberIds.filter((id) => id !== userId);

    const tokens = [];

    for (const uid of destinataires) {
      const userSnap = await db.collection("users").doc(uid).get();
      const userData = userSnap.data();

      const settings = userData.notificationsSettings || {};
      if (userData.fcmToken && settings.global !== false && settings.produitSupprime !== false) {
        tokens.push(userData.fcmToken);
      }
    }

    if (tokens.length > 0) await sendNotification(tokens, title, body);
  }
);

exports.onProduitCoche = onDocumentUpdated(
  { region: "europe-west1" },
  "groups/{groupId}/produits/{produitId}",
  async (event) => {
    const before = event.data?.before?.fields;
    const after = event.data?.after?.fields;
    const groupId = event.params.groupId;

    if (!before || !after) return;

    if (before.checked?.booleanValue === false && after.checked?.booleanValue === true) {
      const groupSnap = await db.collection("groups").doc(groupId).get();
      const groupData = groupSnap.data();
      const groupName = groupData?.nom || "un groupe";

      const cochePar = after.ajoutePar?.stringValue || "Quelqu’un";
      const nomProduit = after.nom?.stringValue || "un produit";
      const userId = after.userId?.stringValue;

      const title = "Produit acheté";
      const body = `${cochePar} a coché ${nomProduit} comme acheté dans ${groupName}`;

      const memberIds = groupData.memberIds || [];
      const destinataires = memberIds.filter((id) => id !== userId);

      const tokens = [];

      for (const uid of destinataires) {
        const userSnap = await db.collection("users").doc(uid).get();
        const userData = userSnap.data();

        const settings = userData.notificationsSettings || {};
        if (userData.fcmToken && settings.global !== false && settings.produitAjoute !== false) {
          tokens.push(userData.fcmToken);
        }
      }

      if (tokens.length > 0) await sendNotification(tokens, title, body);
    }
  }
);

exports.onMembreAjoute = onDocumentUpdated(
  { region: "europe-west1" },
  "groups/{groupId}",
  async (event) => {
    const before = event.data?.before?.fields;
    const after = event.data?.after?.fields;

    const beforeMembers = before?.members?.mapValue?.fields || {};
    const afterMembers = after?.members?.mapValue?.fields || {};

    const groupName = after?.groupName?.stringValue || "un groupe";

    const nouveaux = Object.keys(afterMembers).filter((id) => !beforeMembers.hasOwnProperty(id));
    if (nouveaux.length === 0) return;

    for (const nouveauId of nouveaux) {
      const nouveauUser = await db.collection("users").doc(nouveauId).get();
      const nouveauNom = nouveauUser.data()?.username || "Quelqu’un";

      const title = "Nouveau membre";
      const body = `${nouveauNom} a rejoint le groupe ${groupName}`;

      const autres = Object.keys(afterMembers).filter((id) => id !== nouveauId);
      const tokens = [];

      for (const uid of autres) {
        const userSnap = await db.collection("users").doc(uid).get();
        const userData = userSnap.data();

        const settings = userData.notificationsSettings || {};
        if (userData.fcmToken && settings.global !== false && settings.membreAjoute !== false) {
          tokens.push(userData.fcmToken);
        }
      }

      if (tokens.length > 0) await sendNotification(tokens, title, body);
    }
  }
);

exports.onGroupeCree = onDocumentCreated(
  { region: "europe-west1" },
  "groups/{groupId}",
  async (event) => {
    const group = event.data?.fields;
    const groupName = group?.nom?.stringValue || "un groupe";
    const memberIds = group?.memberIds?.arrayValue?.values?.map((v) => v.stringValue) || [];
    const creePar = group?.creePar?.stringValue || "Quelqu’un";

    const title = "Nouveau groupe créé";
    const body = `${creePar} a créé le groupe ${groupName}`;

    const tokens = [];

    for (const uid of memberIds) {
      const userSnap = await db.collection("users").doc(uid).get();
      const userData = userSnap.data();

      const settings = userData.notificationsSettings || {};
      if (userData.fcmToken && settings.global !== false && settings.groupeCree !== false) {
        tokens.push(userData.fcmToken);
      }
    }

    if (tokens.length > 0) await sendNotification(tokens, title, body);
  }
);
