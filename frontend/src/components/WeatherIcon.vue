<script setup>
import { computed } from 'vue'
import { t } from '../i18n'

// MET's weather icons (public/weather, MIT licence). Uncertain days are shown faded.
const props = defineProps({ symbol: String, uncertain: Boolean, size: { type: String, default: 'size-6' } })

const KINDS = ['thunder', 'sleet', 'snow', 'showers', 'heavyrain', 'lightrain', 'rain', 'fog', 'partlycloudy', 'fair', 'clearsky', 'cloudy']
const label = computed(() => {
  const kind = KINDS.find(k => props.symbol?.includes(k)) ?? 'cloudy'
  return t(`weather.kind.${kind}`) + (props.uncertain ? ` (${t('weather.uncertain')})` : '')
})
</script>

<template>
  <img :src="`/weather/${symbol}.svg`" :alt="label" :title="label" :class="[size, uncertain && 'opacity-45']" class="shrink-0" loading="lazy">
</template>
