import { useState } from 'react';
import { View, Text, Linking } from 'react-native';
import { useAuth } from '../../src/hooks/useAuth';
import { useMyProfile, useUpdateProfile } from '../../src/hooks/useProfile';
import { Card } from '../../src/components/ui/Card';
import { Button } from '../../src/components/ui/Button';
import { EditProfileModal } from '../../src/components/ui/EditProfileModal';
import { confirm, notifyError, notifyInfo } from '../../src/utils/confirm';
import { requestAndRegisterPushToken } from '../../src/lib/notifications';

export default function ProfileScreen() {
  const { user, signOut } = useAuth();
  const { data: profile } = useMyProfile();
  const updateProfile = useUpdateProfile();

  const [showEditModal, setShowEditModal] = useState(false);

  const handleSignOut = async () => {
    const ok = await confirm({
      title: 'Déconnexion',
      message: 'Voulez-vous vraiment vous déconnecter ?',
      confirmLabel: 'Se déconnecter',
      destructive: true,
    });
    if (!ok) return;
    const { error } = await signOut();
    if (error) notifyError(error.message);
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
    <View className="flex-1 bg-gray-50">
      <Card className="mx-4 mt-6">
        <View className="items-center mb-6">
          <View className="w-20 h-20 rounded-full bg-primary-100 items-center justify-center mb-3">
            <Text className="text-3xl">👤</Text>
          </View>
          <Text className="text-lg font-semibold text-gray-900">
            {displayName}
          </Text>
          <Text className="text-sm text-gray-500 mt-1">{user?.email}</Text>
        </View>

        <View className="border-t border-gray-100 pt-4 gap-3">
          <Card onPress={() => setShowEditModal(true)}>
            <Text className="text-base text-gray-700">Modifier le profil</Text>
          </Card>
          <Card onPress={handleNotifications}>
            <Text className="text-base text-gray-700">Notifications</Text>
          </Card>
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
