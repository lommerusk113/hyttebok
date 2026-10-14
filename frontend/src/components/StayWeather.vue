<script setup>
import { computed, watch } from 'vue'
import WeatherIcon from './WeatherIcon.vue'
import { dayWeather, loadWeatherDays, monthClimate } from '../weather'
import { parseIsoDate, toIsoDate } from '../utils'
import { localeTag, t } from '../i18n'

// Weather for a stay: measured for past days, the forecast where there is one, otherwise what's usual for that month.
const props = defineProps({ start: String, end: String })
watch(() => [props.start, props.end], ([start, end]) => start && end && loadWeatherDays(start, end), { immediate: true })

const days = computed(() => {
  const list = []
  for (let d = parseIsoDate(props.start); toIsoDate(d) <= props.end && list.length < 31; d.setDate(d.getDate() + 1)) {
    const iso = toIsoDate(d)
    list.push({ iso, label: d.toLocaleDateString(localeTag(), { weekday: 'short', day: 'numeric' }), forecast: dayWeather(iso)?.symbol && dayWeather(iso) })
  }
  return list
})
const forecastDays = computed(() => days.value.filter(d => d.forecast))
const climate = computed(() => {
  if (forecastDays.value.length === days.value.length) return null
  const month = parseIsoDate(props.start).getMonth() + 1
  const c = monthClimate(month)
  return c && { ...c, name: parseIsoDate(props.start).toLocaleDateString(localeTag(), { month: 'long' }) }
})
</script>

<template>
  <div v-if="forecastDays.length || climate" class="space-y-2">
    <ul v-if="forecastDays.length" class="flex gap-1 overflow-x-auto pb-1">
      <li v-for="d in forecastDays" :key="d.iso" class="flex w-14 shrink-0 flex-col items-center gap-0.5 rounded-md bg-zinc-50 py-1.5 text-xs">
        <span class="text-zinc-500">{{ d.label }}</span>
        <WeatherIcon :symbol="d.forecast.symbol" :uncertain="d.forecast.uncertain" size="size-7" />
        <span class="font-semibold tabular-nums">{{ Math.round(d.forecast.maxTemp) }}°</span>
        <span class="tabular-nums text-zinc-500">{{ Math.round(d.forecast.minTemp) }}°</span>
      </li>
    </ul>
    <p v-if="climate" class="text-sm text-zinc-600">
      <i class="pi pi-chart-line mr-1 text-xs text-zinc-500" />
      {{ t('weather.usual', { month: climate.name, max: Math.round(climate.maxTemp), min: Math.round(climate.minTemp), rain: climate.rainChancePercent }) }}
    </p>
  </div>
</template>
