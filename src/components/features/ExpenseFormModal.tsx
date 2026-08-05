import { useEffect, useState } from 'react';
import {
  View,
  Text,
  Modal,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  TouchableOpacity,
} from 'react-native';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import type { GroupMember } from '../../hooks/useGroups';
import { splitEqually, formatAmount } from '../../utils/balances';

export interface ExpenseFormValues {
  title: string;
  amount: number;
  paidBy: string;
  /** ISO YYYY-MM-DD, null = aujourd'hui */
  expenseDate: string | null;
  participantIds: string[];
}

interface Props {
  visible: boolean;
  title: string;
  members: GroupMember[];
  currentUserId: string;
  initialValues?: ExpenseFormValues | null;
  loading?: boolean;
  onSubmit: (values: ExpenseFormValues) => void;
  onCancel: () => void;
}

function todayFr(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

function isoToFr(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

function frToIso(fr: string): string | null {
  const match = fr.trim().match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (!match) return null;
  const [, d, m, y] = match;
  const day = Number(d);
  const month = Number(m);
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  return `${y}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function parseAmount(text: string): number | null {
  const value = Number(text.trim().replace(',', '.'));
  if (!Number.isFinite(value) || value <= 0) return null;
  return Math.round(value * 100) / 100;
}

export function ExpenseFormModal({
  visible,
  title,
  members,
  currentUserId,
  initialValues,
  loading = false,
  onSubmit,
  onCancel,
}: Props) {
  const [name, setName] = useState('');
  const [amountText, setAmountText] = useState('');
  const [dateText, setDateText] = useState(todayFr());
  const [paidBy, setPaidBy] = useState(currentUserId);
  const [participants, setParticipants] = useState<Set<string>>(new Set());
  const [errors, setErrors] = useState<{
    name?: string;
    amount?: string;
    date?: string;
    participants?: string;
  }>({});

  // (Ré)initialise le formulaire à chaque ouverture
  useEffect(() => {
    if (!visible) return;
    setErrors({});
    if (initialValues) {
      setName(initialValues.title);
      setAmountText(String(initialValues.amount).replace('.', ','));
      setDateText(
        initialValues.expenseDate ? isoToFr(initialValues.expenseDate) : todayFr(),
      );
      setPaidBy(initialValues.paidBy);
      setParticipants(new Set(initialValues.participantIds));
    } else {
      setName('');
      setAmountText('');
      setDateText(todayFr());
      setPaidBy(currentUserId);
      // Par défaut : tout le monde participe
      setParticipants(new Set(members.map((m) => m.user_id)));
    }
  }, [visible, initialValues, currentUserId, members]);

  const toggleParticipant = (userId: string) => {
    setParticipants((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
    if (errors.participants) setErrors((e) => ({ ...e, participants: undefined }));
  };

  const parsedAmount = parseAmount(amountText);
  const participantCount = participants.size;
  const perPerson =
    parsedAmount !== null && participantCount > 0
      ? splitEqually(parsedAmount, [...participants]).values().next().value
      : null;

  const handleSubmit = () => {
    const nextErrors: typeof errors = {};
    const trimmedName = name.trim();
    if (!trimmedName) nextErrors.name = 'Le titre est requis';
    if (parsedAmount === null) nextErrors.amount = 'Montant invalide';
    const iso = frToIso(dateText);
    if (!iso) nextErrors.date = 'Date invalide (JJ/MM/AAAA)';
    if (participantCount === 0)
      nextErrors.participants = 'Sélectionnez au moins un participant';

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    onSubmit({
      title: trimmedName,
      amount: parsedAmount!,
      paidBy,
      expenseDate: iso,
      participantIds: [...participants],
    });
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onCancel}
    >
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        className="flex-1 justify-center items-center bg-black/50"
      >
        <View className="bg-white dark:bg-gray-900 w-full h-full md:h-auto md:max-h-[90%] md:max-w-[500px] md:rounded-2xl md:my-8 overflow-hidden">
          <View className="px-6 pt-6 pb-4 border-b border-gray-100 dark:border-gray-800">
            <Text className="text-lg font-semibold text-gray-800 dark:text-gray-100">{title}</Text>
          </View>

          <ScrollView
            className="flex-1"
            contentContainerClassName="px-6 py-4"
            keyboardShouldPersistTaps="handled"
          >
            <Input
              label="Titre *"
              placeholder="Courses, Essence, Restaurant..."
              value={name}
              onChangeText={(t) => {
                setName(t);
                if (errors.name) setErrors((e) => ({ ...e, name: undefined }));
              }}
              error={errors.name}
              autoFocus
              returnKeyType="next"
            />

            <View className="flex-row gap-3 mt-4">
              <View className="flex-1">
                <Input
                  label="Montant (€) *"
                  placeholder="0,00"
                  value={amountText}
                  onChangeText={(t) => {
                    setAmountText(t);
                    if (errors.amount)
                      setErrors((e) => ({ ...e, amount: undefined }));
                  }}
                  error={errors.amount}
                  keyboardType="decimal-pad"
                />
              </View>
              <View className="flex-1">
                <Input
                  label="Date"
                  placeholder="JJ/MM/AAAA"
                  value={dateText}
                  onChangeText={(t) => {
                    setDateText(t);
                    if (errors.date) setErrors((e) => ({ ...e, date: undefined }));
                  }}
                  error={errors.date}
                  keyboardType="numbers-and-punctuation"
                />
              </View>
            </View>

            <Text className="text-sm font-medium text-gray-700 dark:text-gray-200 mt-4 mb-2">
              Payé par
            </Text>
            <View className="flex-row flex-wrap gap-2">
              {members.map((member) => {
                const selected = paidBy === member.user_id;
                return (
                  <TouchableOpacity
                    key={member.user_id}
                    onPress={() => setPaidBy(member.user_id)}
                    className={`rounded-full px-3 py-1.5 border ${
                      selected
                        ? 'bg-primary-600 border-primary-600'
                        : 'bg-white dark:bg-gray-900 border-gray-300 dark:border-gray-600'
                    }`}
                  >
                    <Text
                      className={`text-sm ${
                        selected ? 'text-white font-medium' : 'text-gray-700 dark:text-gray-200'
                      }`}
                    >
                      {member.display_name}
                      {member.user_id === currentUserId ? ' (vous)' : ''}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>

            <Text className="text-sm font-medium text-gray-700 dark:text-gray-200 mt-4 mb-2">
              Pour qui ? *
            </Text>
            <View className="flex-row flex-wrap gap-2">
              {members.map((member) => {
                const selected = participants.has(member.user_id);
                return (
                  <TouchableOpacity
                    key={member.user_id}
                    onPress={() => toggleParticipant(member.user_id)}
                    className={`rounded-full px-3 py-1.5 border ${
                      selected
                        ? 'bg-primary-50 dark:bg-primary-950 border-primary-600'
                        : 'bg-white dark:bg-gray-900 border-gray-300 dark:border-gray-600'
                    }`}
                  >
                    <Text
                      className={`text-sm ${
                        selected
                          ? 'text-primary-700 dark:text-primary-300 font-medium'
                          : 'text-gray-400'
                      }`}
                    >
                      {selected ? '✓ ' : ''}
                      {member.display_name}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
            {errors.participants && (
              <Text className="text-xs text-danger-600 mt-1">
                {errors.participants}
              </Text>
            )}

            {perPerson !== null && perPerson !== undefined && (
              <View className="bg-gray-50 dark:bg-gray-950 rounded-xl px-4 py-3 mt-4">
                <Text className="text-sm text-gray-600 dark:text-gray-300">
                  Réparti équitablement : environ {formatAmount(perPerson)} par
                  personne ({participantCount} participant
                  {participantCount > 1 ? 's' : ''})
                </Text>
              </View>
            )}
          </ScrollView>

          <View className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 flex-row gap-3">
            <View className="flex-1">
              <Button title="Annuler" variant="outline" onPress={onCancel} />
            </View>
            <View className="flex-1">
              <Button
                title="Enregistrer"
                onPress={handleSubmit}
                loading={loading}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
