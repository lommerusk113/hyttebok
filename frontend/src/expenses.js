// Shared costs, in øre (whole numbers, so nothing is lost to rounding).

/**
 * What each person is owed (+) or owes (−). An expense is split evenly; the øre that don't divide go to the first
 * people in the list, so the shares always add up to the amount. A settlement moves money from one person to another.
 */
export function balances(expenses) {
  const sum = {}
  const add = (name, amount) => { sum[name] = (sum[name] || 0) + amount }
  for (const e of expenses) {
    add(e.paidBy, e.amount)
    const share = Math.floor(e.amount / e.splitBetween.length)
    const extra = e.amount - share * e.splitBetween.length
    e.splitBetween.forEach((name, i) => add(name, -(share + (i < extra ? 1 : 0))))
  }
  return sum
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
