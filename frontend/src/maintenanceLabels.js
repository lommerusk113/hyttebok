import { formatDate, parseIsoDate } from './utils'
import { nextDue, status } from './maintenance'
import { t } from './i18n'

// One wording for maintenance status everywhere (tab, dashboard).

export function dueLabel(m, today) {
  const { days } = status(m, today)
  if (days < 0) return t('maintenance.overdue', -days)
  if (days === 0) return t('maintenance.dueToday')
  if (days <= 30) return t('maintenance.dueIn', days)
  return formatDate(parseIsoDate(nextDue(m)))
}

export const STATUS_STYLE = {
  overdue: { bar: 'bg-rose-500', text: 'text-rose-700' },
  soon: { bar: 'bg-amber-400', text: 'text-amber-700' },
  later: { bar: 'bg-zinc-200', text: 'text-zinc-500' },
}

export const overdueBadge = n => (n ? t('maintenance.overdueCount', n) : undefined)
