import { useMemo, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
} from 'react-native';
import { useLocalSearchParams, Stack } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../../../../src/stores/authStore';
import { useGroupMembers, displayNameFor } from '../../../../src/hooks/useGroups';
import {
  useGroupExpenses,
  useGroupSettlements,
  useCreateExpense,
  useUpdateExpense,
  useDeleteExpense,
  useCreateSettlement,
  useDeleteSettlement,
} from '../../../../src/hooks/useExpenses';
import {
  computeBalances,
  suggestSettlements,
  splitEqually,
  formatAmount,
  type ExpenseWithShares,
} from '../../../../src/utils/balances';
import {
  ExpenseFormModal,
  type ExpenseFormValues,
} from '../../../../src/components/features/ExpenseFormModal';
import { useRealtimeExpenses } from '../../../../src/hooks/useRealtimeExpenses';
import { LoadingState } from '../../../../src/components/ui/LoadingState';
import { EmptyState } from '../../../../src/components/ui/EmptyState';
import { Button } from '../../../../src/components/ui/Button';
import { confirm, notifyError } from '../../../../src/utils/confirm';

type TabKey = 'expenses' | 'balances';

export default function GroupExpensesScreen() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const currentUser = useAuthStore((s) => s.user);
  const queryClient = useQueryClient();

  const { data: members } = useGroupMembers(groupId);
  const { data: expenses, isLoading: expensesLoading } =
    useGroupExpenses(groupId);
  const { data: settlements, isLoading: settlementsLoading } =
    useGroupSettlements(groupId);

  useRealtimeExpenses(groupId);

  const createExpense = useCreateExpense();
  const updateExpense = useUpdateExpense();
  const deleteExpense = useDeleteExpense();
  const createSettlement = useCreateSettlement();
  const deleteSettlement = useDeleteSettlement();

  const [tab, setTab] = useState<TabKey>('expenses');
  const [modal, setModal] = useState<
    { kind: 'closed' } | { kind: 'create' } | { kind: 'edit'; expense: ExpenseWithShares }
  >({ kind: 'closed' });
  const [refreshing, setRefreshing] = useState(false);

  const nameOf = useMemo(() => {
    const map = new Map<string, string>();
    for (const member of members ?? []) {
      map.set(member.user_id, displayNameFor(member));
    }
    return (userId: string) =>
      map.get(userId) ?? 'Ancien membre';
  }, [members]);

  const total = useMemo(
    () => (expenses ?? []).reduce((sum, e) => sum + e.amount, 0),
    [expenses],
  );

  const balances = useMemo(
    () => computeBalances(expenses ?? [], settlements ?? []),
    [expenses, settlements],
  );

  const suggestions = useMemo(() => suggestSettlements(balances), [balances]);

  const isLoading = expensesLoading || settlementsLoading;

  const handleRefresh = async () => {
    setRefreshing(true);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['expenses', groupId] }),
      queryClient.invalidateQueries({ queryKey: ['settlements', groupId] }),
    ]);
    setRefreshing(false);
  };

  const sharesFromForm = (values: ExpenseFormValues) => {
    const split = splitEqually(values.amount, values.participantIds);
    return [...split.entries()].map(([user_id, amount]) => ({
      user_id,
      amount,
    }));
  };

  const handleCreate = async (values: ExpenseFormValues) => {
    try {
      await createExpense.mutateAsync({
        groupId,
        title: values.title,
        amount: values.amount,
        paidBy: values.paidBy,
        expenseDate: values.expenseDate,
        shares: sharesFromForm(values),
      });
      setModal({ kind: 'closed' });
    } catch (e: unknown) {
      notifyError(e instanceof Error ? e.message : 'Erreur lors de la création');
    }
  };

  const handleEdit = async (values: ExpenseFormValues) => {
    if (modal.kind !== 'edit') return;
    try {
      await updateExpense.mutateAsync({
        expenseId: modal.expense.id,
        groupId,
        title: values.title,
        amount: values.amount,
        paidBy: values.paidBy,
        expenseDate: values.expenseDate,
        shares: sharesFromForm(values),
      });
      setModal({ kind: 'closed' });
    } catch (e: unknown) {
      notifyError(
        e instanceof Error ? e.message : 'Erreur lors de la modification',
      );
    }
  };

  const handleDelete = async (expense: ExpenseWithShares) => {
    const ok = await confirm({
      title: 'Supprimer la dépense',
      message: `Supprimer "${expense.title}" (${formatAmount(expense.amount)}) ?`,
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteExpense.mutateAsync({ expenseId: expense.id, groupId });
      setModal({ kind: 'closed' });
    } catch (e: unknown) {
      notifyError(
        e instanceof Error ? e.message : 'Erreur lors de la suppression',
      );
    }
  };

  const handleSettle = async (from: string, to: string, amount: number) => {
    const ok = await confirm({
      title: 'Marquer comme remboursé',
      message: `${nameOf(from)} a remboursé ${formatAmount(amount)} à ${nameOf(to)} ?`,
      confirmLabel: 'Confirmer',
    });
    if (!ok) return;
    try {
      await createSettlement.mutateAsync({
        groupId,
        fromUser: from,
        toUser: to,
        amount,
      });
    } catch (e: unknown) {
      notifyError(
        e instanceof Error ? e.message : "Erreur lors de l'enregistrement",
      );
    }
  };

  const handleDeleteSettlement = async (settlementId: string) => {
    const ok = await confirm({
      title: 'Annuler le remboursement',
      message: 'Supprimer ce remboursement de l’historique ?',
      confirmLabel: 'Supprimer',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteSettlement.mutateAsync({ settlementId, groupId });
    } catch (e: unknown) {
      notifyError(
        e instanceof Error ? e.message : 'Erreur lors de la suppression',
      );
    }
  };

  const editInitialValues: ExpenseFormValues | null =
    modal.kind === 'edit'
      ? {
          title: modal.expense.title,
          amount: modal.expense.amount,
          paidBy: modal.expense.paid_by,
          expenseDate: modal.expense.expense_date,
          participantIds: modal.expense.expense_shares.map((s) => s.user_id),
        }
      : null;

  const renderExpense = ({ item }: { item: ExpenseWithShares }) => (
    <TouchableOpacity
      onPress={() => setModal({ kind: 'edit', expense: item })}
      onLongPress={() => handleDelete(item)}
      className="bg-white rounded-xl px-4 py-3 mb-2 mx-4"
    >
      <View className="flex-row items-center justify-between">
        <View className="flex-1 mr-3">
          <Text className="text-sm font-semibold text-gray-800" numberOfLines={1}>
            {item.title}
          </Text>
          <Text className="text-xs text-gray-500 mt-0.5">
            Payé par {nameOf(item.paid_by)} ·{' '}
            {item.expense_shares.length} participant
            {item.expense_shares.length > 1 ? 's' : ''}
          </Text>
        </View>
        <View className="items-end">
          <Text className="text-base font-bold text-gray-900">
            {formatAmount(item.amount)}
          </Text>
          <Text className="text-xs text-gray-400 mt-0.5">
            {item.expense_date.split('-').reverse().join('/')}
          </Text>
        </View>
      </View>
    </TouchableOpacity>
  );

  return (
    <>
      <Stack.Screen
        options={{
          headerShown: true,
          title: 'Dépenses',
          headerTintColor: '#0d9488',
        }}
      />

      <View className="flex-1 bg-gray-50">
        {/* Total + onglets */}
        <View className="px-4 pt-4">
          <View className="bg-primary-600 rounded-2xl px-5 py-4 mb-4">
            <Text className="text-primary-100 text-xs uppercase tracking-wide">
              Total du groupe
            </Text>
            <Text className="text-white text-2xl font-bold mt-1">
              {formatAmount(total)}
            </Text>
          </View>

          <View className="flex-row bg-gray-200 rounded-xl p-1 mb-3">
            {(
              [
                ['expenses', 'Dépenses'],
                ['balances', 'Équilibre'],
              ] as [TabKey, string][]
            ).map(([key, label]) => (
              <TouchableOpacity
                key={key}
                onPress={() => setTab(key)}
                className={`flex-1 py-2 rounded-lg ${
                  tab === key ? 'bg-white' : ''
                }`}
              >
                <Text
                  className={`text-center text-sm font-medium ${
                    tab === key ? 'text-primary-700' : 'text-gray-500'
                  }`}
                >
                  {label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {isLoading ? (
          <LoadingState />
        ) : tab === 'expenses' ? (
          <>
            {!expenses?.length ? (
              <EmptyState
                icon="💶"
                title="Aucune dépense"
                subtitle="Ajoutez la première dépense du groupe."
              />
            ) : (
              <FlatList
                data={expenses}
                keyExtractor={(item) => item.id}
                renderItem={renderExpense}
                refreshControl={
                  <RefreshControl
                    refreshing={refreshing}
                    onRefresh={handleRefresh}
                  />
                }
                contentContainerStyle={{ paddingBottom: 96 }}
              />
            )}
            <View className="absolute bottom-6 left-4 right-4">
              <Button
                title="+ Ajouter une dépense"
                onPress={() => setModal({ kind: 'create' })}
              />
            </View>
          </>
        ) : (
          <FlatList
            data={balances}
            keyExtractor={(item) => item.userId}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
            }
            ListHeaderComponent={
              <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide px-4 mb-2">
                Soldes
              </Text>
            }
            renderItem={({ item }) => (
              <View className="bg-white rounded-xl px-4 py-3 mb-2 mx-4 flex-row items-center justify-between">
                <Text className="text-sm font-medium text-gray-800">
                  {nameOf(item.userId)}
                  {item.userId === currentUser?.id ? ' (vous)' : ''}
                </Text>
                <Text
                  className={`text-sm font-bold ${
                    item.balance > 0.004
                      ? 'text-success-600'
                      : item.balance < -0.004
                        ? 'text-danger-600'
                        : 'text-gray-400'
                  }`}
                >
                  {item.balance > 0 ? '+' : ''}
                  {formatAmount(item.balance)}
                </Text>
              </View>
            )}
            ListEmptyComponent={
              <EmptyState
                icon="⚖️"
                title="Rien à équilibrer"
                subtitle="Ajoutez des dépenses pour voir les soldes."
              />
            }
            ListFooterComponent={
              <View>
                {suggestions.length > 0 && (
                  <>
                    <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide px-4 mt-4 mb-2">
                      Remboursements suggérés
                    </Text>
                    {suggestions.map((s, index) => (
                      <View
                        key={`${s.from}-${s.to}-${index}`}
                        className="bg-white rounded-xl px-4 py-3 mb-2 mx-4 flex-row items-center justify-between"
                      >
                        <Text className="text-sm text-gray-700 flex-1 mr-2">
                          {nameOf(s.from)} doit{' '}
                          <Text className="font-bold">
                            {formatAmount(s.amount)}
                          </Text>{' '}
                          à {nameOf(s.to)}
                        </Text>
                        <Button
                          title="Remboursé"
                          size="sm"
                          variant="outline"
                          onPress={() => handleSettle(s.from, s.to, s.amount)}
                        />
                      </View>
                    ))}
                  </>
                )}

                {(settlements?.length ?? 0) > 0 && (
                  <>
                    <Text className="text-xs font-medium text-gray-400 uppercase tracking-wide px-4 mt-4 mb-2">
                      Remboursements effectués
                    </Text>
                    {settlements!.map((settlement) => (
                      <TouchableOpacity
                        key={settlement.id}
                        onLongPress={() => handleDeleteSettlement(settlement.id)}
                        className="bg-white/70 rounded-xl px-4 py-3 mb-2 mx-4 flex-row items-center justify-between"
                      >
                        <Text className="text-sm text-gray-500 flex-1 mr-2">
                          {nameOf(settlement.from_user)} a remboursé{' '}
                          {formatAmount(settlement.amount)} à{' '}
                          {nameOf(settlement.to_user)}
                        </Text>
                        <Text className="text-xs text-gray-400">
                          {settlement.created_at.slice(0, 10).split('-').reverse().join('/')}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </>
                )}
                <View className="h-8" />
              </View>
            }
            contentContainerStyle={{ paddingBottom: 32 }}
          />
        )}
      </View>

      <ExpenseFormModal
        visible={modal.kind !== 'closed'}
        title={
          modal.kind === 'edit' ? 'Modifier la dépense' : 'Nouvelle dépense'
        }
        members={members ?? []}
        currentUserId={currentUser?.id ?? ''}
        initialValues={editInitialValues}
        loading={createExpense.isPending || updateExpense.isPending}
        onSubmit={modal.kind === 'edit' ? handleEdit : handleCreate}
        onCancel={() => setModal({ kind: 'closed' })}
      />
    </>
  );
}
