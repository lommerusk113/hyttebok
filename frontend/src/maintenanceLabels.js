import { formatDate, parseIsoDate } from './utils'
import { byMonth, nextDue, status } from './maintenance'
import { localeTag, t } from './i18n'

// One wording for maintenance status everywhere (tab, dashboard).

export function dueLabel(m, today) {
  const { days } = status(m, today)
  if (days < 0) return t('maintenance.overdue', -days)
  if (byMonth(m)) return nextDue(m).slice(0, 7) <= today.slice(0, 7) ? t('maintenance.dueThisMonth') : t('maintenance.nextOn', { date: dueText(m, nextDue(m)) })
  if (days === 0) return t('maintenance.dueToday')
  if (days <= 30) return t('maintenance.dueIn', days)
  return t('maintenance.nextOn', { date: dueText(m, nextDue(m)) })
}

/** A due date as the task counts it: "oktober 2027" for monthly/yearly tasks, the exact date otherwise. */
export const dueText = (m, iso) => (byMonth(m)
  ? parseIsoDate(iso).toLocaleDateString(localeTag(), { month: 'long', year: 'numeric' })
  : formatDate(parseIsoDate(iso)))

export const STATUS_STYLE = {
  overdue: { bar: 'bg-rose-500', text: 'text-rose-700' },
  soon: { bar: 'bg-amber-400', text: 'text-amber-700' },
  later: { bar: 'bg-zinc-200', text: 'text-zinc-500' },
}

export const overdueBadge = n => (n ? t('maintenance.overdueCount', n) : undefined)
