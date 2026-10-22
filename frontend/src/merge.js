// No imports, so plain Node can run src/offline.check.js on it.

const same = (a, b) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null)

/**
 * Three-way merge of an offline edit: `base` is what it started from, `mine` the edit, `theirs` the server's version
 * now. Returns the version to save and the fields where both changed it differently (theirs is kept there).
 */
export function mergeEdit(base, mine, theirs) {
  const merged = { ...theirs }
  const conflicts = []
  for (const key of Object.keys(mine)) {
    if (same(mine[key], base[key])) continue // not changed here
    if (same(theirs[key], base[key]) || same(theirs[key], mine[key])) merged[key] = mine[key]
    else conflicts.push(key)
  }
  return { merged, conflicts }
}
