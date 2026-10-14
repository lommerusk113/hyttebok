import { api } from './api'
import { askName, userName } from './user'
import { t } from './i18n'

// Tick/untick an item and record who did it and when. Returns the saved item,
// or null when we first need to ask for the user's name.
async function toggleDone(url, item, flag, byField, atField) {
  if (!userName.value) {
    askName.value = true
    return null
  }
  const on = !item[flag]
  return api.put(url, { ...item, [flag]: on, [byField]: on ? userName.value : null, [atField]: on ? new Date().toISOString() : null })
}

export const toggleTodo = todo => toggleDone('/todos', todo, 'done', 'doneBy', 'doneAt')
export const toggleShopping = item => toggleDone('/shopping', item, 'bought', 'boughtBy', 'boughtAt')

/** "«Ved» er kjøpt · Angre": shown after ticking something off, so a mis-tap is one tap to undo. */
export function undoToast(toast, summary, undo) {
  toast.add({ group: 'action', summary, actionLabel: t('maintenance.undo'), action: undo, life: 5000 })
}

export const addShoppingItem = (name, quantity) =>
  api.post('/shopping', { name: name.trim(), quantity: quantity?.trim() || null, bought: false, addedBy: userName.value })
