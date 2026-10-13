import { api } from './api'
import { askName, userName } from './user'

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

export const addShoppingItem = (name, quantity) =>
  api.post('/shopping', { name: name.trim(), quantity: quantity?.trim() || null, bought: false, addedBy: userName.value })
