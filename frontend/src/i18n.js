import { watch } from 'vue'
import { createI18n } from 'vue-i18n'
import nb from './locales/nb.json'
import en from './locales/en.json'

// To add a language: add locales/<code>.json and an entry here.
export const LOCALES = { nb: { label: 'NO', tag: 'nb-NO' }, en: { label: 'EN', tag: 'en-GB' } }

const KEY = 'hyttebok.locale'
const stored = (() => { try { return localStorage.getItem(KEY) } catch { return null } })()

export const i18n = createI18n({ legacy: false, locale: LOCALES[stored] ? stored : 'nb', fallbackLocale: 'nb', messages: { nb, en } })
export const { t, locale } = i18n.global

// BCP 47 tag for Intl date formatting; reading locale.value keeps callers reactive.
export const localeTag = () => LOCALES[locale.value].tag

watch(locale, v => {
  try { localStorage.setItem(KEY, v) } catch { /* private mode */ }
  document.documentElement.lang = v
}, { immediate: true })

// PrimeVue's DatePicker texts, generated from Intl so no per-language arrays to maintain.
export function primeLocale() {
  const tag = localeTag()
  const names = (dates, opts) => dates.map(d => new Intl.DateTimeFormat(tag, opts).format(d))
  const week = [...Array(7)].map((_, i) => new Date(2024, 0, 7 + i)) // 7 Jan 2024 is a Sunday
  const months = [...Array(12)].map((_, i) => new Date(2024, i, 1))

  return {
    firstDayOfWeek: 1,
    dayNames: names(week, { weekday: 'long' }),
    dayNamesShort: names(week, { weekday: 'short' }),
    dayNamesMin: names(week, { weekday: 'narrow' }),
    monthNames: names(months, { month: 'long' }),
    monthNamesShort: names(months, { month: 'short' }),
    today: t('common.today'),
    clear: t('common.clear'),
    accept: t('common.yes'),
    reject: t('common.no'),
    dateFormat: 'dd.mm.yy',
    // Read out by screen readers on password fields
    passwordPrompt: t('login.passwordPrompt'),
  }
}
