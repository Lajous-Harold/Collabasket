import { useState, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useAuth } from '../../src/hooks/useAuth';
import { Button } from '../../src/components/ui/Button';
import { notifyError, notifyInfo } from '../../src/utils/confirm';

export default function VerifyScreen() {
  const { email } = useLocalSearchParams<{ email: string }>();
  const OTP_LENGTH = 8;
  const [code, setCode] = useState(Array(OTP_LENGTH).fill(''));
  const [loading, setLoading] = useState(false);
  const { verifyOtp, signInWithOtp } = useAuth();
  const inputRefs = useRef<(TextInput | null)[]>([]);

  const handleCodeChange = (text: string, index: number) => {
    const newCode = [...code];
    newCode[index] = text;
    setCode(newCode);

    if (text && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    if (newCode.every((c) => c !== '')) {
      handleVerify(newCode.join(''));
    }
  };

  const handleKeyPress = (key: string, index: number) => {
    if (key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const handleVerify = async (token: string) => {
    if (!email) return;

    setLoading(true);
    const { error } = await verifyOtp(email, token);
    setLoading(false);

    if (error) {
      notifyInfo('Code invalide', 'Le code saisi est incorrect ou a expiré.');
      setCode(Array(OTP_LENGTH).fill(''));
      inputRefs.current[0]?.focus();
    }
  };

  const handleResend = async () => {
    if (!email) return;

    setLoading(true);
    const { error } = await signInWithOtp(email);
    setLoading(false);

    if (error) {
      notifyError(error.message);
    } else {
      notifyInfo('Code renvoyé', `Un nouveau code a été envoyé à ${email}.`);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      className="flex-1 bg-white"
    >
      <View className="flex-1 justify-center px-8">
        <View className="items-center mb-10">
          <Text className="text-2xl font-bold text-gray-900">
            Vérification
          </Text>
          <Text className="text-sm text-gray-500 mt-2 text-center">
            Entrez le code à {OTP_LENGTH} chiffres envoyé à
          </Text>
          <Text className="text-sm font-semibold text-primary-600 mt-1">
            {email}
          </Text>
        </View>

        <View className="flex-row justify-center gap-2 mb-8">
          {code.map((digit, index) => (
            <TextInput
              key={index}
              ref={(ref) => {
                inputRefs.current[index] = ref;
              }}
              className="w-10 h-12 border-2 border-gray-300 rounded-lg text-center text-xl font-bold text-gray-900 bg-gray-50 focus:border-primary-500"
              maxLength={1}
              keyboardType="number-pad"
              value={digit}
              onChangeText={(text) => handleCodeChange(text, index)}
              onKeyPress={({ nativeEvent }) =>
                handleKeyPress(nativeEvent.key, index)
              }
              editable={!loading}
            />
          ))}
        </View>

        <Button
          title="Renvoyer le code"
          variant="outline"
          size="sm"
          onPress={handleResend}
          disabled={loading}
        />
      </View>
    </KeyboardAvoidingView>
  );
}
