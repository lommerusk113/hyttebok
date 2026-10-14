import { reactive, ref } from 'vue'
import { api } from './api'

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

export const dayWeather = iso => byDate.get(iso) ?? null
export const monthClimate = month => weather.value?.climate.find(c => c.month === month) ?? null
