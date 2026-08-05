import { View, Text } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useAcceptInvitation } from '../../src/hooks/useInvitations';
import { useAuth } from '../../src/hooks/useAuth';
import { Button } from '../../src/components/ui/Button';
import { Card } from '../../src/components/ui/Card';
import { LoadingState } from '../../src/components/ui/LoadingState';
import { notifyError, notifyInfo } from '../../src/utils/confirm';
import { setPendingInviteToken } from '../../src/stores/pendingInvite';

export default function AcceptInvitationScreen() {
  const { token } = useLocalSearchParams<{ token: string }>();
  const { user, isLoading: authLoading } = useAuth();
  const acceptInvitation = useAcceptInvitation();
  const router = useRouter();

  const handleAccept = async () => {
    try {
      const result = await acceptInvitation.mutateAsync(token);
      notifyInfo(
        'Bienvenue !',
        `Vous avez rejoint le groupe "${result.group_name}".`,
      );
      router.replace('/(app)/groups');
    } catch (e: any) {
      notifyError(e.message);
    }
  };

  if (authLoading) return <LoadingState />;

  if (!user) {
    return (
      <View className="flex-1 bg-gray-50 items-center justify-center px-8">
        <Card className="w-full">
          <Text className="text-lg font-semibold text-gray-800 text-center mb-2">
            Invitation Collabasket
          </Text>
          <Text className="text-sm text-gray-500 text-center mb-6">
            Connectez-vous pour accepter cette invitation.
          </Text>
          <Button
            title="Se connecter"
            onPress={async () => {
              // Memorise le token pour rejouer l'invitation apres l'auth
              if (token) await setPendingInviteToken(token);
              router.replace('/(auth)/login');
            }}
          />
        </Card>
      </View>
    );
  }

  return (
    <View className="flex-1 bg-gray-50 items-center justify-center px-8">
      <Card className="w-full">
        <Text className="text-lg font-semibold text-gray-800 text-center mb-2">
          Invitation Collabasket
        </Text>
        <Text className="text-sm text-gray-500 text-center mb-6">
          Vous avez été invité à rejoindre un groupe.
        </Text>
        <View className="gap-3">
          <Button
            title="Accepter l'invitation"
            onPress={handleAccept}
            loading={acceptInvitation.isPending}
          />
          <Button
            title="Ignorer"
            variant="outline"
            onPress={() => router.replace('/(app)')}
          />
        </View>
      </Card>
    </View>
  );
}
