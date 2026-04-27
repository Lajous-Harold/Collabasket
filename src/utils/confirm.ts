import { Alert, Platform } from 'react-native';

interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
}

/**
 * Confirmation cross-platform.
 * - Web : window.confirm (Alert.alert de RN n'a pas de boutons custom utilisables sur web)
 * - Native : Alert.alert avec 2 boutons.
 * Resout `true` si l'utilisateur confirme, `false` sinon.
 */
export function confirm(options: ConfirmOptions): Promise<boolean> {
  const {
    title,
    message,
    confirmLabel = 'Confirmer',
    cancelLabel = 'Annuler',
    destructive = false,
  } = options;

  if (Platform.OS === 'web') {
    return Promise.resolve(window.confirm(`${title}\n\n${message}`));
  }

  return new Promise((resolve) => {
    Alert.alert(title, message, [
      { text: cancelLabel, style: 'cancel', onPress: () => resolve(false) },
      {
        text: confirmLabel,
        style: destructive ? 'destructive' : 'default',
        onPress: () => resolve(true),
      },
    ]);
  });
}

/**
 * Affiche un message d'erreur cross-platform.
 */
export function notifyError(message: string): void {
  if (Platform.OS === 'web') {
    window.alert(`Erreur\n\n${message}`);
  } else {
    Alert.alert('Erreur', message);
  }
}

/**
 * Affiche une notification info cross-platform.
 */
export function notifyInfo(title: string, message: string): void {
  if (Platform.OS === 'web') {
    window.alert(`${title}\n\n${message}`);
  } else {
    Alert.alert(title, message);
  }
}
