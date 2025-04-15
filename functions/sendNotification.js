const admin = require("firebase-admin");

/**
 * Envoie une notification FCM à une liste de tokens.
 *
 * @param {Array<string>} tokens - Liste des tokens FCM.
 * @param {string} title - Titre de la notification.
 * @param {string} body - Corps de la notification.
 */
async function sendNotification(tokens, title, body) {
  if (!tokens || tokens.length === 0) {
    console.log("Aucun token à notifier.");
    return;
  }

  const payload = {
    notification: {
      title,
      body,
    },
  };

  const options = {
    priority: "high",
    timeToLive: 60 * 60,
  };

  try {
    const response = await admin.messaging().sendToDevice(tokens, payload, options);
    console.log(`📨 Notification envoyée à ${tokens.length} utilisateurs`);
    return response;
  } catch (error) {
    console.error("❌ Erreur lors de l'envoi de la notification :", error);
    throw error;
  }
}

module.exports = sendNotification;
