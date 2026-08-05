import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { supabase } from '../lib/supabase';
import { useAuthStore } from '../stores/authStore';
import type { Database, Json } from '../types/database';
import type { ExpenseWithShares } from '../utils/balances';

type SettlementRow = Database['public']['Tables']['settlements']['Row'];

export interface ExpenseShareInput {
  user_id: string;
  amount: number;
}

export interface ExpenseInput {
  groupId: string;
  title: string;
  amount: number;
  paidBy: string;
  /** Format ISO (YYYY-MM-DD) ; null = aujourd'hui côté serveur */
  expenseDate: string | null;
  shares: ExpenseShareInput[];
}

export function useGroupExpenses(groupId: string) {
  return useQuery({
    queryKey: ['expenses', groupId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('expenses')
        .select('*, expense_shares(*)')
        .eq('group_id', groupId)
        .order('expense_date', { ascending: false })
        .order('created_at', { ascending: false });

      if (error) throw error;
      return data as unknown as ExpenseWithShares[];
    },
    enabled: !!groupId,
  });
}

export function useGroupSettlements(groupId: string) {
  return useQuery({
    queryKey: ['settlements', groupId],
    queryFn: async () => {
      const { data, error } = await supabase
        .from('settlements')
        .select('*')
        .eq('group_id', groupId)
        .order('created_at', { ascending: false });

      if (error) throw error;
      return data as SettlementRow[];
    },
    enabled: !!groupId,
  });
}

function useInvalidateExpenses() {
  const queryClient = useQueryClient();
  return (groupId: string) => {
    queryClient.invalidateQueries({ queryKey: ['expenses', groupId] });
    queryClient.invalidateQueries({ queryKey: ['settlements', groupId] });
  };
}

export function useCreateExpense() {
  const invalidate = useInvalidateExpenses();

  return useMutation({
    mutationFn: async (input: ExpenseInput) => {
      // RPC atomique : valide somme(parts) = montant et insère
      // dépense + parts dans la même transaction (migration 016).
      const { data, error } = await supabase.rpc('create_expense', {
        p_group_id: input.groupId,
        p_title: input.title,
        p_amount: input.amount,
        p_paid_by: input.paidBy,
        p_expense_date: input.expenseDate,
        p_shares: input.shares as unknown as Json,
      });
      if (error) throw error;
      return data;
    },
    onSuccess: (_data, input) => invalidate(input.groupId),
  });
}

export function useUpdateExpense() {
  const invalidate = useInvalidateExpenses();

  return useMutation({
    mutationFn: async ({
      expenseId,
      ...input
    }: ExpenseInput & { expenseId: string }) => {
      const { error } = await supabase.rpc('update_expense', {
        p_expense_id: expenseId,
        p_title: input.title,
        p_amount: input.amount,
        p_paid_by: input.paidBy,
        p_expense_date: input.expenseDate,
        p_shares: input.shares as unknown as Json,
      });
      if (error) throw error;
    },
    onSuccess: (_data, input) => invalidate(input.groupId),
  });
}

export function useDeleteExpense() {
  const invalidate = useInvalidateExpenses();

  return useMutation({
    mutationFn: async ({
      expenseId,
    }: {
      expenseId: string;
      groupId: string;
    }) => {
      const { error } = await supabase
        .from('expenses')
        .delete()
        .eq('id', expenseId);
      if (error) throw error;
    },
    onSuccess: (_data, input) => invalidate(input.groupId),
  });
}

export function useCreateSettlement() {
  const invalidate = useInvalidateExpenses();
  const user = useAuthStore((s) => s.user);

  return useMutation({
    mutationFn: async ({
      groupId,
      fromUser,
      toUser,
      amount,
    }: {
      groupId: string;
      fromUser: string;
      toUser: string;
      amount: number;
    }) => {
      if (!user) throw new Error('Utilisateur non connecté');
      const { error } = await supabase.from('settlements').insert({
        group_id: groupId,
        from_user: fromUser,
        to_user: toUser,
        amount,
        created_by: user.id,
      });
      if (error) throw error;
    },
    onSuccess: (_data, input) => invalidate(input.groupId),
  });
}

export function useDeleteSettlement() {
  const invalidate = useInvalidateExpenses();

  return useMutation({
    mutationFn: async ({
      settlementId,
    }: {
      settlementId: string;
      groupId: string;
    }) => {
      const { error } = await supabase
        .from('settlements')
        .delete()
        .eq('id', settlementId);
      if (error) throw error;
    },
    onSuccess: (_data, input) => invalidate(input.groupId),
  });
}
