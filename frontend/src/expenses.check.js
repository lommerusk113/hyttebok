// Run: node src/expenses.check.js
import assert from 'node:assert/strict'
import { balances, settleUp } from './expenses.js'

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
console.log('expenses ok')
