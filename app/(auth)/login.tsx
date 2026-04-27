import { useState } from 'react';
import {
  View,
  Text,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/hooks/useAuth';
import { Button } from '../../src/components/ui/Button';
import { Input } from '../../src/components/ui/Input';
import { notifyError } from '../../src/utils/confirm';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const { signInWithOtp } = useAuth();
  const router = useRouter();

  const handleSendOtp = async () => {
    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail) {
      notifyError('Veuillez entrer votre adresse email.');
      return;
    }

    setLoading(true);
    const { error } = await signInWithOtp(trimmedEmail);
    setLoading(false);

    if (error) {
      notifyError(error.message);
      return;
    }

    router.push({
      pathname: '/(auth)/verify',
      params: { email: trimmedEmail },
    });
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      className="flex-1 bg-white"
    >
      <View className="flex-1 justify-center px-8">
        <View className="items-center mb-12">
          <Text className="text-4xl font-bold text-primary-600">
            Collabasket
          </Text>
          <Text className="text-base text-gray-500 mt-2">
            Vos courses, ensemble.
          </Text>
        </View>

        <View className="gap-4">
          <Input
            label="Adresse email"
            placeholder="vous@exemple.com"
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            editable={!loading}
          />

          <Button
            title={loading ? 'Envoi en cours...' : 'Recevoir un code'}
            onPress={handleSendOtp}
            loading={loading}
          />
        </View>

        <Text className="text-center text-xs text-gray-400 mt-8">
          Un code de vérification sera envoyé à votre email.
        </Text>
      </View>
    </KeyboardAvoidingView>
  );
}
