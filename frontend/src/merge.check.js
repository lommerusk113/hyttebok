// Run: node src/merge.check.js
import assert from 'node:assert/strict'
import { mergeEdit } from './merge.js'

const base = { id: 1, title: 'Male', done: false, claimedBy: null }
// I renamed it offline; meanwhile someone ticked it done: both changes survive.
assert.deepEqual(mergeEdit(base, { ...base, title: 'Male terrassen' }, { ...base, done: true }),
  { merged: { id: 1, title: 'Male terrassen', done: true, claimedBy: null }, conflicts: [] })
// Both renamed it differently: theirs stays, and it's reported.
assert.deepEqual(mergeEdit(base, { ...base, title: 'A' }, { ...base, title: 'B' }),
  { merged: { ...base, title: 'B' }, conflicts: ['title'] })
// Both made the same change: no conflict.
assert.deepEqual(mergeEdit(base, { ...base, done: true }, { ...base, done: true }).conflicts, [])
// Arrays compare by value.
assert.deepEqual(mergeEdit({ a: ['x'] }, { a: ['x', 'y'] }, { a: ['x'] }).merged, { a: ['x', 'y'] })
console.log('offline merge ok')
