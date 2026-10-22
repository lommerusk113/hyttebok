// Shared costs, in øre (whole numbers, so nothing is lost to rounding).

/**
 * Each person's part of an amount split evenly: the øre that don't divide go to the first people in the list, so the
 * parts always add up to the amount.
 */
export function evenShares(amount, count) {
  const share = Math.floor(amount / count)
  const extra = amount - share * count
  return Array.from({ length: count }, (_, i) => share + (i < extra ? 1 : 0))
}

/**
 * What each person is owed (+) or owes (−). An expense is split by its shares, or evenly; one that isn't split
 * (nobody in splitBetween) changes nothing. A settlement moves money from one person to another.
 */
export function balances(expenses) {
  const sum = {}
  const add = (name, amount) => { sum[name] = (sum[name] || 0) + amount }
  for (const e of expenses) {
    if (!e.splitBetween.length) continue
    add(e.paidBy, e.amount)
    const shares = e.shares || evenShares(e.amount, e.splitBetween.length)
    e.splitBetween.forEach((name, i) => add(name, -shares[i]))
  }
  return sum
}

/** Who has paid the most (expenses, not paying each other back), optionally in one year: [{ name, total, count }]. */
export function paidMost(expenses, year) {
  const by = {}
  for (const e of expenses) {
    if (e.kind !== 'EXPENSE' || (year && !e.spentOn.startsWith(String(year)))) continue
    by[e.paidBy] ??= { name: e.paidBy, total: 0, count: 0 }
    by[e.paidBy].total += e.amount
    by[e.paidBy].count++
  }
  return Object.values(by).sort((a, b) => b.total - a.total)
}

/**
 * Fewest-ish payments that settle everyone: the one who owes most pays the one owed most, until all are even.
 * ponytail: greedy, not the true minimum number of payments; plenty for a family.
 */
export function settleUp(sum) {
  const owed = Object.entries(sum).filter(([, v]) => v > 0).map(([name, v]) => ({ name, v })).sort((a, b) => b.v - a.v)
  const owes = Object.entries(sum).filter(([, v]) => v < 0).map(([name, v]) => ({ name, v: -v })).sort((a, b) => b.v - a.v)
  const payments = []
  let i = 0
  let j = 0
  while (i < owes.length && j < owed.length) {
    const amount = Math.min(owes[i].v, owed[j].v)
    payments.push({ from: owes[i].name, to: owed[j].name, amount })
    owes[i].v -= amount
    owed[j].v -= amount
    if (!owes[i].v) i++
    if (!owed[j].v) j++
  }
  return payments
}
