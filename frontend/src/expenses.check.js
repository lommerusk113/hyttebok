// Run: node src/expenses.check.js
import assert from 'node:assert/strict'
import { balances, evenShares, paidMost, settleUp } from './expenses.js'

const sum = balances([
  { paidBy: 'Kari', amount: 1000, splitBetween: ['Kari', 'Ola', 'Per'] }, // 334/333/333
  { paidBy: 'Ola', amount: 300, splitBetween: ['Per'] },
])
assert.deepEqual(sum, { Kari: 666, Ola: -33, Per: -633 })
assert.equal(Object.values(sum).reduce((a, b) => a + b, 0), 0)

const payments = settleUp(sum)
assert.deepEqual(payments, [{ from: 'Per', to: 'Kari', amount: 633 }, { from: 'Ola', to: 'Kari', amount: 33 }])

// Recording the suggested payments as settlements evens everyone out.
const after = balances([
  { paidBy: 'Kari', amount: 1000, splitBetween: ['Kari', 'Ola', 'Per'] },
  { paidBy: 'Ola', amount: 300, splitBetween: ['Per'] },
  ...payments.map(p => ({ paidBy: p.from, amount: p.amount, splitBetween: [p.to] })),
])
assert.ok(Object.values(after).every(v => v === 0))
// Not split: nobody owes anything. Uneven shares: as given.
assert.deepEqual(balances([{ paidBy: 'Kari', amount: 500, splitBetween: [] }]), {})
assert.deepEqual(balances([{ paidBy: 'Kari', amount: 1000, splitBetween: ['Kari', 'Ola'], shares: [200, 800] }]), { Kari: 800, Ola: -800 })
assert.deepEqual(evenShares(1000, 3), [334, 333, 333])

// Who paid most counts expenses only, split or not, and can look at one year.
const list = [
  { kind: 'EXPENSE', paidBy: 'Kari', amount: 500, splitBetween: [], spentOn: '2026-03-01' },
  { kind: 'EXPENSE', paidBy: 'Ola', amount: 300, splitBetween: ['Kari'], spentOn: '2025-03-01' },
  { kind: 'EXPENSE', paidBy: 'Kari', amount: 100, splitBetween: ['Ola'], spentOn: '2026-05-01' },
  { kind: 'SETTLEMENT', paidBy: 'Ola', amount: 999, splitBetween: ['Kari'], spentOn: '2026-05-02' },
]
assert.deepEqual(paidMost(list), [{ name: 'Kari', total: 600, count: 2 }, { name: 'Ola', total: 300, count: 1 }])
assert.deepEqual(paidMost(list, 2025), [{ name: 'Ola', total: 300, count: 1 }])
console.log('expenses ok')
