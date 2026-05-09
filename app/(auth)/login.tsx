import { useState } from 'react';
import {
  View,
  Text,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useAuth } from '../../src/hooks/useAuth';
import { APPLE_SIGN_IN_AVAILABLE } from '../../src/lib/auth';
import { Button } from '../../src/components/ui/Button';
import { Input } from '../../src/components/ui/Input';
import { notifyError } from '../../src/utils/confirm';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [otpLoading, setOtpLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [appleLoading, setAppleLoading] = useState(false);
  const { signInWithOtp, signInWithGoogle, signInWithApple } = useAuth();
  const router = useRouter();

  const handleSendOtp = async () => {
    const trimmedEmail = email.trim().toLowerCase();
    if (!trimmedEmail) {
      notifyError('Veuillez entrer votre adresse email.');
      return;
    }

    setOtpLoading(true);
    const { error } = await signInWithOtp(trimmedEmail);
    setOtpLoading(false);

    if (error) {
      notifyError(error.message);
      return;
    }

    router.push({
      pathname: '/(auth)/verify',
      params: { email: trimmedEmail },
    });
  };

  const handleGoogle = async () => {
    setGoogleLoading(true);
    const { cancelled, error } = await signInWithGoogle();
    setGoogleLoading(false);

    if (cancelled) return;
    if (error) {
      notifyError(error.message);
      return;
    }
    // Succes : onAuthStateChange dans authStore met a jour la session,
    // useEffect dans app/_layout redirige vers /(app). Rien a faire ici.
  };

  const handleApple = async () => {
    setAppleLoading(true);
    const { cancelled, error } = await signInWithApple();
    setAppleLoading(false);

    if (cancelled) return;
    if (error) {
      notifyError(error.message);
      return;
    }
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

        {/* Email OTP */}
        <View className="gap-4">
          <Input
            label="Adresse email"
            placeholder="vous@exemple.com"
            value={email}
            onChangeText={setEmail}
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            editable={!otpLoading}
          />

          <Button
            title="Recevoir un code"
            onPress={handleSendOtp}
            loading={otpLoading}
            disabled={googleLoading || appleLoading}
          />
        </View>

        {/* Divider */}
        <View className="flex-row items-center my-6">
          <View className="flex-1 h-px bg-gray-200" />
          <Text className="mx-3 text-xs text-gray-400">
            ou continuer avec
          </Text>
          <View className="flex-1 h-px bg-gray-200" />
        </View>

        {/* OAuth */}
        <View className="gap-3">
          <Button
            title="Google"
            variant="outline"
            onPress={handleGoogle}
            loading={googleLoading}
            disabled={otpLoading || appleLoading}
          />
          {Platform.OS === 'ios' && (
            <Button
              title={APPLE_SIGN_IN_AVAILABLE ? 'Apple' : 'Apple (bientôt)'}
              variant="outline"
              onPress={handleApple}
              loading={appleLoading}
              disabled={
                !APPLE_SIGN_IN_AVAILABLE || otpLoading || googleLoading
              }
            />
          )}
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}
