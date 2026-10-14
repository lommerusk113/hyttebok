import { parseIsoDate, toIsoDate } from './dates.js'

// Date maths for recurring maintenance. All dates are local YYYY-MM-DD strings.

export const UNITS = ['DAYS', 'WEEKS', 'MONTHS', 'YEARS']

/** iso + n units. Month/year steps keep the day but clamp it (31 Jan + 1 month = 28/29 Feb). */
export function addInterval(iso, n, unit) {

  const d = parseIsoDate(iso)
  if (unit === 'DAYS' || unit === 'WEEKS') {
    d.setDate(d.getDate() + n * (unit === 'WEEKS' ? 7 : 1))
    return toIsoDate(d)
  }

  const day = d.getDate()
  d.setDate(1)
  d.setMonth(d.getMonth() + n * (unit === 'YEARS' ? 12 : 1))
  d.setDate(Math.min(day, new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()))

  return toIsoDate(d)
}

// Without a last completion, the schedule counts from the day the task was added.
const baseDate = m => m.lastCompleted || toIsoDate(m.createdAt ? new Date(m.createdAt) : new Date())

export const nextDue = m => addInterval(baseDate(m), m.intervalValue, m.intervalUnit)

export const daysBetween = (fromIso, toIso) => Math.round((parseIsoDate(toIso) - parseIsoDate(fromIso)) / 864e5)

// Monthly and yearly tasks are due in a month, not on a day: done in October, every year → due October
// next year, overdue from 1 November. Daily and weekly tasks keep their exact day.
export const byMonth = m => m.intervalUnit === 'MONTHS' || m.intervalUnit === 'YEARS'

const endOfMonth = iso => {
  const d = parseIsoDate(iso)
  return toIsoDate(new Date(d.getFullYear(), d.getMonth() + 1, 0))
}

/** The last day the task is still on time. */
export const dueBy = m => (byMonth(m) ? endOfMonth(nextDue(m)) : nextDue(m))

export const SOON_DAYS = 30

/** `days` until the last on-time day (negative = days overdue). */
export function status(m, today) {
  const days = daysBetween(today, dueBy(m))
  return { days, kind: days < 0 ? 'overdue' : daysBetween(today, nextDue(m)) <= SOON_DAYS ? 'soon' : 'later' }
}

/**
 * Everything to show for one year: completions from the history, plus (for this year and later)
 * the next due date and the planned dates after it. An overdue task appears once, at its due date.
 */
export function yearEntries(tasks, completions, year, today) {

  const thisYear = Number(today.slice(0, 4))
  const byId = new Map(tasks.map(m => [m.id, m]))
  const entries = completions
    .filter(c => c.completedOn.startsWith(`${year}-`) && byId.has(c.maintenanceId))
    .map(c => ({ key: `c${c.id}`, date: c.completedOn, kind: 'done', task: byId.get(c.maintenanceId), completion: c }))

  if (year >= thisYear) {
    for (const m of tasks) {
      const base = baseDate(m)
      // Tasks more often than every two weeks would flood the list; show only their next date.
      const onlyNext = m.intervalValue * { DAYS: 1, WEEKS: 7, MONTHS: 30, YEARS: 365 }[m.intervalUnit] < 14
      // Count from the base each time so month-end clamping doesn't drift (31 Jan, 28 Feb, 31 Mar…).
      for (let k = 1; ; k++) {
        const date = addInterval(base, k * m.intervalValue, m.intervalUnit)
        const overdue = (byMonth(m) ? endOfMonth(date) : date) < today
        if (overdue && k > 1) continue
        if (Number(date.slice(0, 4)) > year) break
        if (overdue ? year === thisYear : date.startsWith(`${year}-`)) {
          entries.push({ key: `p${m.id}-${k}`, date, kind: overdue ? 'overdue' : 'planned', task: m, monthOnly: byMonth(m) })
        }
        if (onlyNext) break
      }
    }
  }

  return entries.sort((a, b) => a.date.localeCompare(b.date))
}

// Self-check for the date maths: `node src/maintenance.js`
if (typeof process !== 'undefined' && import.meta.url === `file://${process.argv[1]}`) {
  const assert = (await import('node:assert')).strict
  assert.equal(addInterval('2026-01-31', 1, 'MONTHS'), '2026-02-28')
  assert.equal(addInterval('2024-01-31', 1, 'MONTHS'), '2024-02-29')
  assert.equal(addInterval('2026-01-31', 2, 'MONTHS'), '2026-03-31')
  assert.equal(addInterval('2026-11-15', 3, 'MONTHS'), '2027-02-15')
  assert.equal(addInterval('2024-02-29', 1, 'YEARS'), '2025-02-28')
  assert.equal(addInterval('2026-10-14', 2, 'WEEKS'), '2026-10-28')
  assert.equal(addInterval('2026-12-31', 1, 'DAYS'), '2027-01-01')
  const task = { id: 1, intervalValue: 3, intervalUnit: 'MONTHS', lastCompleted: '2026-04-01' }
  assert.deepEqual(yearEntries([task], [], 2026, '2026-10-14').map(e => [e.date, e.kind]), [['2026-07-01', 'overdue'], ['2026-10-01', 'planned']])
  assert.deepEqual(yearEntries([task], [], 2027, '2026-10-14').map(e => e.date), ['2027-01-01', '2027-04-01', '2027-07-01', '2027-10-01'])
  assert.deepEqual(yearEntries([task], [{ id: 9, maintenanceId: 1, completedOn: '2025-05-02' }], 2025, '2026-10-14').map(e => e.kind), ['done'])
  const daily = { id: 2, intervalValue: 1, intervalUnit: 'DAYS', lastCompleted: '2026-10-10' }
  assert.deepEqual(yearEntries([daily], [], 2026, '2026-10-14').map(e => [e.date, e.kind]), [['2026-10-11', 'overdue']])
  assert.deepEqual(yearEntries([daily], [], 2028, '2026-10-14'), [])
  const biweekly = { id: 3, intervalValue: 2, intervalUnit: 'WEEKS', lastCompleted: '2026-10-14' }
  assert.equal(yearEntries([biweekly], [], 2027, '2026-10-14').length, 26)
  const yearly = { id: 4, intervalValue: 1, intervalUnit: 'YEARS', lastCompleted: '2026-10-01' }
  assert.deepEqual(status(yearly, '2026-10-14'), { days: 382, kind: 'later' })
  assert.equal(status(yearly, '2027-10-31').kind, 'soon')
  assert.deepEqual(status(yearly, '2027-11-01'), { days: -1, kind: 'overdue' })
  assert.deepEqual(yearEntries([yearly], [], 2027, '2027-10-20').map(e => e.kind), ['planned'])
  console.log('maintenance.js ok')
}
