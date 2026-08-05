import type { Database } from '../types/database';

type ExpenseRow = Database['public']['Tables']['expenses']['Row'];
type ExpenseShareRow = Database['public']['Tables']['expense_shares']['Row'];
type SettlementRow = Database['public']['Tables']['settlements']['Row'];

export type ExpenseWithShares = ExpenseRow & {
  expense_shares: ExpenseShareRow[];
};

/** Solde en euros. Positif = le groupe doit de l'argent à ce membre. */
export interface MemberBalance {
  userId: string;
  balance: number;
}

/** Remboursement suggéré : `from` doit verser `amount` à `to`. */
export interface SuggestedSettlement {
  from: string;
  to: string;
  amount: number;
}

// Tous les calculs internes sont en CENTIMES (entiers) : additionner
// des flottants (0.1 + 0.2) produirait des soldes du type 9.999999.
const toCents = (euros: number): number => Math.round(euros * 100);
const toEuros = (cents: number): number => cents / 100;

/**
 * Solde de chaque membre :
 *   + ce qu'il a payé (expenses.paid_by)
 *   - sa part des dépenses (expense_shares)
 *   + les remboursements qu'il a versés
 *   - les remboursements qu'il a reçus
 */
export function computeBalances(
  expenses: ExpenseWithShares[],
  settlements: SettlementRow[],
): MemberBalance[] {
  const cents = new Map<string, number>();
  const add = (userId: string, delta: number) => {
    cents.set(userId, (cents.get(userId) ?? 0) + delta);
  };

  for (const expense of expenses) {
    add(expense.paid_by, toCents(expense.amount));
    for (const share of expense.expense_shares) {
      add(share.user_id, -toCents(share.amount));
    }
  }

  for (const settlement of settlements) {
    add(settlement.from_user, toCents(settlement.amount));
    add(settlement.to_user, -toCents(settlement.amount));
  }

  return [...cents.entries()]
    .map(([userId, c]) => ({ userId, balance: toEuros(c) }))
    .sort((a, b) => b.balance - a.balance);
}

/**
 * Plan de remboursement glouton : à chaque étape, le plus gros débiteur
 * rembourse le plus gros créancier. Produit au plus (n - 1) virements.
 */
export function suggestSettlements(
  balances: MemberBalance[],
): SuggestedSettlement[] {
  const creditors = balances
    .filter((b) => toCents(b.balance) > 0)
    .map((b) => ({ userId: b.userId, cents: toCents(b.balance) }))
    .sort((a, b) => b.cents - a.cents);
  const debtors = balances
    .filter((b) => toCents(b.balance) < 0)
    .map((b) => ({ userId: b.userId, cents: -toCents(b.balance) }))
    .sort((a, b) => b.cents - a.cents);

  const result: SuggestedSettlement[] = [];
  let ci = 0;
  let di = 0;

  while (ci < creditors.length && di < debtors.length) {
    const creditor = creditors[ci];
    const debtor = debtors[di];
    const transfer = Math.min(creditor.cents, debtor.cents);

    if (transfer > 0) {
      result.push({
        from: debtor.userId,
        to: creditor.userId,
        amount: toEuros(transfer),
      });
    }

    creditor.cents -= transfer;
    debtor.cents -= transfer;
    if (creditor.cents === 0) ci++;
    if (debtor.cents === 0) di++;
  }

  return result;
}

/**
 * Répartition équitable d'un montant entre n participants, exacte au
 * centime : les `reste` premiers participants reçoivent un centime de
 * plus (ex. 10 € pour 3 → 3.34, 3.33, 3.33).
 */
export function splitEqually(amount: number, userIds: string[]): Map<string, number> {
  const shares = new Map<string, number>();
  if (userIds.length === 0) return shares;

  const totalCents = toCents(amount);
  const base = Math.floor(totalCents / userIds.length);
  let remainder = totalCents - base * userIds.length;

  for (const userId of userIds) {
    const extra = remainder > 0 ? 1 : 0;
    remainder -= extra;
    shares.set(userId, toEuros(base + extra));
  }
  return shares;
}

/** Formatte un montant en euros : 12.5 -> "12,50 €". */
export function formatAmount(amount: number): string {
  return `${amount.toFixed(2).replace('.', ',')} €`;
}
