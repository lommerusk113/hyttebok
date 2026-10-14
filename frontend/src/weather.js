import { reactive, ref } from 'vue'
import { api } from './api'
import { parseIsoDate, toIsoDate } from './dates'

// Cabin weather from /api/weather: MET for ~9 days, Open-Meteo to 16 days (uncertain), monthly averages beyond.
// /api/weather/days adds measured weather for past days and the usual weather (source NORMAL) for far-off dates.
export const weather = ref(null)
const byDate = reactive(new Map())
const loadedRanges = new Set()
let loading = null

const remember = days => days.forEach(d => byDate.set(d.date, d))

export function loadWeather() {
  loading ??= api.get('/weather').then(r => { weather.value = r; remember(r.days) }).catch(() => { loading = null })
  return loading
}

export async function loadWeatherDays(from, to) {
  const key = `${from}/${to}`
  if (loadedRanges.has(key)) return
  loadedRanges.add(key)
  await loadWeather() // forecast first, so a range result doesn't get overwritten by older data
  try {
    remember(await api.get(`/weather/days?from=${from}&to=${to}`))
  } catch {
    loadedRanges.delete(key)
  }
}

/** After the cabin's location changed: drop everything and fetch for the new spot. */
export const weatherVersion = ref(0)
export function reloadWeather() {
  byDate.clear()
  loadedRanges.clear()
  loading = null
  weather.value = null
  weatherVersion.value++
  return loadWeather()
}

export const dayWeather = iso => byDate.get(iso) ?? null

/**
 * A stay's weather in one go: the average high over the days we have data for, and the most common symbol
 * (none when every day is only a "usual for the date" value, shown as ≈).
 */
export function stayWeather(start, end) {

  const days = []
  for (const d = parseIsoDate(start); toIsoDate(d) <= end; d.setDate(d.getDate() + 1)) {
    const day = byDate.get(toIsoDate(d))
    if (day) days.push(day)
  }
  if (!days.length) return null

  const counts = new Map()
  for (const d of days.filter(x => x.symbol)) counts.set(d.symbol, (counts.get(d.symbol) || 0) + 1)
  const symbol = [...counts].sort((a, b) => b[1] - a[1])[0]?.[0] ?? null

  return { symbol, maxTemp: days.reduce((sum, d) => sum + d.maxTemp, 0) / days.length, uncertain: days.some(d => d.uncertain) }
}
export const monthClimate = month => weather.value?.climate.find(c => c.month === month) ?? null
