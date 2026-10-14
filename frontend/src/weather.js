import { ref } from 'vue'
import { api } from './api'

// Cabin weather from /api/weather: MET for ~9 days, Open-Meteo to 16 days (uncertain), monthly averages beyond.
export const weather = ref(null)
let loading = null

export function loadWeather() {
  loading ??= api.get('/weather').then(r => { weather.value = r }).catch(() => { loading = null })
  return loading
}

export const dayWeather = iso => weather.value?.days.find(d => d.date === iso) ?? null
export const monthClimate = month => weather.value?.climate.find(c => c.month === month) ?? null
