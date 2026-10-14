import { localeTag, t } from './i18n'

export const initials = name => (name || '?').split(/\s+/).map(p => p[0]).join('').slice(0, 2).toUpperCase()

// Literal class strings so Tailwind picks them up.
const PALETTE = [
  { bg: 'bg-emerald-100', text: 'text-emerald-700', bar: 'bg-emerald-600' },
  { bg: 'bg-sky-100', text: 'text-sky-700', bar: 'bg-sky-600' },
  { bg: 'bg-amber-100', text: 'text-amber-700', bar: 'bg-amber-600' },
  { bg: 'bg-rose-100', text: 'text-rose-700', bar: 'bg-rose-600' },
  { bg: 'bg-violet-100', text: 'text-violet-700', bar: 'bg-violet-600' },
  { bg: 'bg-teal-100', text: 'text-teal-700', bar: 'bg-teal-600' },
  { bg: 'bg-orange-100', text: 'text-orange-700', bar: 'bg-orange-600' },
]

export function colorFor(name = '') {
  let hash = 0
  for (const c of name.toLowerCase()) hash = (hash * 31 + c.charCodeAt(0)) | 0
  return PALETTE[Math.abs(hash) % PALETTE.length]
}

export const formatDate = d => new Date(d).toLocaleDateString(localeTag(), { day: 'numeric', month: 'short', year: 'numeric' })
export const formatDay = d => new Date(d).toLocaleDateString(localeTag(), { weekday: 'short', day: 'numeric', month: 'short' })

export function timeAgo(d) {
  const rtf = new Intl.RelativeTimeFormat(localeTag(), { numeric: 'auto' })
  const sec = (new Date(d) - Date.now()) / 1000
  for (const [unit, s] of [['year', 31536000], ['month', 2592000], ['week', 604800], ['day', 86400], ['hour', 3600], ['minute', 60]]) {
    if (Math.abs(sec) >= s) return rtf.format(Math.round(sec / s), unit)
  }
  return t('time.justNow')
}

export { parseIsoDate, toIsoDate } from './dates.js'
