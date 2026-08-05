import { useEffect, useState } from 'react';
import { View, Text, Linking, TouchableOpacity } from 'react-native';
import { useAuth } from '../../src/hooks/useAuth';
import { useMyProfile, useUpdateProfile } from '../../src/hooks/useProfile';
import { Card } from '../../src/components/ui/Card';
import { Button } from '../../src/components/ui/Button';
import { EditProfileModal } from '../../src/components/ui/EditProfileModal';
import { confirm, notifyError, notifyInfo } from '../../src/utils/confirm';
import { requestAndRegisterPushToken } from '../../src/lib/notifications';
import {
  loadThemePreference,
  setThemePreference,
  type ThemePreference,
} from '../../src/lib/theme';

const THEME_OPTIONS: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: 'Système' },
  { value: 'light', label: 'Clair' },
  { value: 'dark', label: 'Sombre' },
];

export default function ProfileScreen() {
  const { user, signOut } = useAuth();
  const { data: profile } = useMyProfile();
  const updateProfile = useUpdateProfile();

  const [showEditModal, setShowEditModal] = useState(false);
  const [themePref, setThemePref] = useState<ThemePreference>('system');

  useEffect(() => {
    loadThemePreference().then(setThemePref);
  }, []);

  const handleThemeChange = (pref: ThemePreference) => {
    setThemePref(pref);
    setThemePreference(pref).catch(() => {
      // Échec de persistance : le thème est quand même appliqué en mémoire
    });
  };

  const handleSignOut = async () => {
    const ok = await confirm({
      title: 'Déconnexion',
      message: 'Voulez-vous vraiment vous déconnecter ?',
      confirmLabel: 'Se déconnecter',
      destructive: true,
    });
    if (!ok) return;
    try {
      await signOut();
    } catch (e: any) {
      notifyError(e?.message ?? 'Erreur lors de la deconnexion.');
    }
  };

  const handleNotifications = async () => {
    if (!user?.id) return;
    try {
      const token = await requestAndRegisterPushToken(user.id);
      if (token) {
        notifyInfo('Notifications activées', 'Vous recevrez les notifications push sur cet appareil.');
      } else {
        notifyInfo(
          'Permissions refusées',
          'Pour activer les notifications, autorisez-les dans les réglages de votre appareil.',
        );
        await Linking.openSettings();
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : 'Une erreur est survenue';
      notifyError(message);
    }
  };

  const handleUpdateDisplayName = async (displayName: string) => {
    try {
      await updateProfile.mutateAsync({ display_name: displayName });
      setShowEditModal(false);
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  const displayName = profile?.display_name ?? 'Utilisateur';

  return (
    <View className="flex-1 bg-gray-50 dark:bg-gray-950">
      <Card className="mx-4 mt-6">
        <View className="items-center mb-6">
          <View className="w-20 h-20 rounded-full bg-primary-100 dark:bg-primary-900 items-center justify-center mb-3">
            <Text className="text-3xl">👤</Text>
          </View>
          <Text className="text-lg font-semibold text-gray-900 dark:text-gray-50">
            {displayName}
          </Text>
          <Text className="text-sm text-gray-500 dark:text-gray-400 mt-1">{user?.email}</Text>
        </View>

        <View className="border-t border-gray-100 dark:border-gray-800 pt-4 gap-3">
          <Card onPress={() => setShowEditModal(true)}>
            <Text className="text-base text-gray-700 dark:text-gray-200">Modifier le profil</Text>
          </Card>
          <Card onPress={handleNotifications}>
            <Text className="text-base text-gray-700 dark:text-gray-200">Notifications</Text>
          </Card>

          <View className="pt-1">
            <Text className="text-sm font-medium text-gray-700 dark:text-gray-200 mb-2">
              Thème
            </Text>
            <View className="flex-row bg-gray-100 dark:bg-gray-800 rounded-xl p-1">
              {THEME_OPTIONS.map((option) => (
                <TouchableOpacity
                  key={option.value}
                  onPress={() => handleThemeChange(option.value)}
                  className={`flex-1 py-2 rounded-lg ${
                    themePref === option.value ? 'bg-white dark:bg-gray-900' : ''
                  }`}
                >
                  <Text
                    className={`text-center text-sm ${
                      themePref === option.value
                        ? 'text-primary-700 dark:text-primary-300 font-medium'
                        : 'text-gray-500 dark:text-gray-400'
                    }`}
                  >
                    {option.label}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        </View>
      </Card>

      <View className="mx-4 mt-4">
        <Button title="Se déconnecter" variant="danger" onPress={handleSignOut} />
      </View>

      <Text className="text-center text-xs text-gray-300 mt-auto mb-6">
        Collabasket v2.0.0
      </Text>

      <EditProfileModal
        visible={showEditModal}
        currentDisplayName={displayName}
        loading={updateProfile.isPending}
        onSubmit={handleUpdateDisplayName}
        onCancel={() => setShowEditModal(false)}
      />
    </View>
  );
}
