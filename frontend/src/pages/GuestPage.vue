<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import LanguageToggle from '../components/LanguageToggle.vue'
import RichText from '../components/RichText.vue'
import { steps } from '../stay'
import { parseIsoDate } from '../dates'
import { localeTag, t } from '../i18n'

// /gjest/<token>: for someone borrowing the cabin, without an account. Only what a guest needs: getting in and
// started, WiFi, keys, the address, and what to do before leaving. Nothing else of the cabin.
const token = useRoute().params.token
const toast = useToast()
const view = ref(null)
const gone = ref(false)
const arrivalDone = ref([])
const departureDone = ref([])

onMounted(async () => {
  const res = await fetch(`/api/guest/${encodeURIComponent(token)}`).catch(() => null)
  if (res?.ok) view.value = await res.json()
  else gone.value = true
})

const cover = computed(() => view.value?.cover && `/api/guest/${encodeURIComponent(token)}/cover`)
const until = computed(() => view.value && parseIsoDate(view.value.validUntil).toLocaleDateString(localeTag(), { weekday: 'long', day: 'numeric', month: 'long' }))
const mapUrl = computed(() => view.value?.address && `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(view.value.address)}`)

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    toast.add({ severity: 'success', summary: t('common.copied'), life: 1500 })
  } catch { /* it's on screen to type */ }
}
</script>

<template>
  <div class="mx-auto max-w-2xl py-6">
    <div class="mb-4 flex justify-end"><LanguageToggle /></div>

    <div v-if="gone" class="card p-8 text-center">
      <i class="pi pi-clock text-3xl text-zinc-400" />
      <h1 class="mt-3 text-lg font-semibold">{{ t('guest.gone') }}</h1>
      <p class="mt-1 text-sm text-zinc-500">{{ t('guest.goneHint') }}</p>
    </div>

    <template v-else-if="view">
      <section class="relative isolate overflow-hidden rounded-xl bg-gradient-to-br from-[#27272a] via-[#18181b] to-black shadow-lg">
        <img v-if="cover" :src="cover" alt="" class="absolute inset-0 -z-10 h-full w-full object-cover">
        <div class="absolute inset-0 -z-10 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />
        <div class="flex min-h-[11rem] flex-col justify-end p-6">
          <p class="text-sm text-white/80">{{ t('guest.welcome', { name: view.label }) }}</p>
          <h1 class="font-display break-words text-3xl uppercase tracking-[0.12em] text-white drop-shadow">{{ view.cabinName }}</h1>
          <p v-if="view.tagline" class="mt-1 text-white/85">{{ view.tagline }}</p>
        </div>
      </section>
      <p class="mt-3 text-center text-sm text-zinc-500">{{ t('guest.validUntil', { date: until }) }}</p>

      <div class="mt-6 space-y-4">
        <section v-if="view.arrivalInfo" class="card p-5">
          <h2 class="flex items-center gap-2 font-semibold"><i class="pi pi-sign-in text-zinc-500" />{{ t('info.arrivalInfo') }}</h2>
          <label v-for="(s, i) in steps(view.arrivalInfo)" :key="i" class="mt-2 flex cursor-pointer items-start gap-3">
            <Checkbox v-model="arrivalDone" :value="i" class="mt-0.5" />
            <span :class="arrivalDone.includes(i) && 'text-zinc-400 line-through'">{{ s }}</span>
          </label>
        </section>

        <section v-if="view.wifiName" class="card p-5">
          <h2 class="flex items-center gap-2 font-semibold"><i class="pi pi-wifi text-zinc-500" />WiFi</h2>
          <dl class="mt-3 grid gap-3 sm:grid-cols-2">
            <div v-for="[label, value] in [[t('home.wifiNetwork'), view.wifiName], [t('home.wifiPassword'), view.wifiPassword]]" :key="label"
                 class="flex items-center justify-between gap-2 rounded-md bg-zinc-50 px-3 py-2">
              <div class="min-w-0"><dt class="text-xs text-zinc-500">{{ label }}</dt><dd class="truncate font-mono font-medium">{{ value || '–' }}</dd></div>
              <Button v-if="value" icon="pi pi-copy" text rounded size="small" :aria-label="`${t('common.copy')}: ${label}`" @click="copy(value)" />
            </div>
          </dl>
        </section>

        <section v-if="view.keyInfo" class="card p-5">
          <h2 class="flex items-center gap-2 font-semibold"><i class="pi pi-key text-zinc-500" />{{ t('info.keyInfo') }}</h2>
          <RichText :text="view.keyInfo" class="mt-2 text-zinc-700" />
        </section>

        <section v-if="view.address" class="card p-5">
          <h2 class="flex items-center gap-2 font-semibold"><i class="pi pi-map-marker text-zinc-500" />{{ t('info.address') }}</h2>
          <p class="mt-2 whitespace-pre-line text-zinc-700">{{ view.address }}</p>
          <a :href="mapUrl" target="_blank" rel="noopener noreferrer" class="mt-2 inline-flex min-h-11 items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
            <i class="pi pi-map text-xs" />{{ t('guest.map') }}
          </a>
        </section>

        <section v-if="view.departureInfo" class="card p-5">
          <h2 class="flex items-center gap-2 font-semibold"><i class="pi pi-sign-out text-zinc-500" />{{ t('guest.beforeYouGo') }}</h2>
          <label v-for="(s, i) in steps(view.departureInfo)" :key="i" class="mt-2 flex cursor-pointer items-start gap-3">
            <Checkbox v-model="departureDone" :value="i" class="mt-0.5" />
            <span :class="departureDone.includes(i) && 'text-zinc-400 line-through'">{{ s }}</span>
          </label>
        </section>
      </div>

      <p class="mt-8 text-center text-xs text-zinc-500">{{ t('guest.footer') }} <a href="/" class="underline underline-offset-2">Hyttebok</a></p>
    </template>
  </div>
</template>
