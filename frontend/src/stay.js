import { api } from './api'
import { toIsoDate } from './dates'
import { status } from './maintenance'
import { userName } from './user'

// Checking in and out of a stay (a booking). "Er på hytta nå" follows the check-in, not just the booked dates.

export const today = () => toIsoDate(new Date())

/** Checked in, not out, and the stay hasn't ended (someone who forgot to check out drops off after the last day). */
export const isHere = b => !!b.checkedInAt && !b.checkedOutAt && b.endDate >= today()

/** Booked for today but not checked in yet. */
export const isExpected = b => !b.checkedInAt && b.startDate <= today() && b.endDate >= today()

/** "Ankomst"/"Avreise" text as steps: one per line, bullet marks dropped. */
export const steps = text => (text || '').split('\n').map(l => l.replace(/^\s*[-*•]\s+/, '').trim()).filter(Boolean)

export const checkIn = b => api.put('/bookings', { ...b, checkedInAt: new Date().toISOString(), checkedInBy: userName.value, checkedOutAt: null })

/** Leaving early frees the rest of the booking: it ends today. */
export const checkOut = b => api.put('/bookings', { ...b, checkedOutAt: new Date().toISOString(), endDate: b.endDate > today() ? today() : b.endDate })

/** Here without a booking: one for today, so the calendar shows it too. */
export const bookToday = () => api.post('/bookings', { guestName: userName.value, startDate: today(), endDate: today(), guests: null, note: null })

/** What needs doing: overdue and soon-due maintenance, and open one-off tasks. */
export async function openTasks() {
  const [todos, maintenance] = await Promise.all([api.get('/todos'), api.get('/maintenance')])
  const due = maintenance.map(m => ({ m, ...status(m, today()) })).filter(x => x.kind !== 'later').sort((a, b) => a.days - b.days)
  return [...due.map(x => ({ key: `m-${x.m.id}`, name: x.m.name, overdue: x.kind === 'overdue', maintenance: x.m })),
    ...todos.filter(x => !x.done).map(x => ({ key: `t-${x.id}`, name: x.title, claimedBy: x.claimedBy, todo: x }))]
}

/** Marks a task from `openTasks` done, by you. */
export function complete(task) {
  const by = userName.value
  if (task.todo) {
    return api.put('/todos', { ...task.todo, done: true, doneBy: by, doneAt: new Date().toISOString(), doneWith: [by], doneNote: null })
  }
  return api.post('/maintenance-completions', { maintenanceId: task.maintenance.id, completedOn: today(), completedBy: by, participants: [by], note: null })
}
