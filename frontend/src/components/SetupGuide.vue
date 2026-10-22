<script setup>
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import { api } from '../api'
import { cabin, editCabin } from '../cabin'
import { cabinId } from '../hytte'
import { installed } from '../install'
import { t } from '../i18n'

// "Kom i gang med hytta": for owners of a cabin that isn't set up yet. Each step says what's missing and goes
// there; done steps tick themselves off. Gone when everything is done, or when hidden ("Skjul") on this device.
const props = defineProps({ bookings: { type: Array, required: true } })

const HIDDEN_KEY = `hyttebok.setupHidden.${cabinId.value}`
const hidden = ref((() => { try { return localStorage.getItem(HIDDEN_KEY) === '1' } catch { return false } })())
const members = ref(null)

onMounted(async () => {
  if (!hidden.value) members.value = (await api.get('/access/members')).length
})

const off = key => cabin.value.hiddenSections.includes(key)
const steps = computed(() => [
  { key: 'cover', done: !!cabin.value.heroPhotoId, edit: true },
  { key: 'arrival', done: (off('arrivalInfo') || !!cabin.value.arrivalInfo?.trim()) && (off('departureInfo') || !!cabin.value.departureInfo?.trim()), edit: true },
  { key: 'wifi', done: off('wifi') || !!cabin.value.wifiName, edit: true },
  { key: 'invite', done: members.value > 1, to: '/tilgang' },
  { key: 'booking', done: props.bookings.length > 0, to: '/booking?new=1' },
  { key: 'install', done: installed, to: '/app' },
])
const doneCount = computed(() => steps.value.filter(s => s.done).length)
const show = computed(() => !hidden.value && members.value !== null && doneCount.value < steps.value.length)

function hide() {
  hidden.value = true
  try { localStorage.setItem(HIDDEN_KEY, '1') } catch { /* private mode */ }
}
</script>

<template>
  <section v-if="show" class="card p-5" :aria-label="t('setup.title')">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h2 class="text-lg font-semibold">{{ t('setup.title') }}</h2>
        <p class="text-sm text-zinc-500">{{ t('setup.progress', { done: doneCount, total: steps.length }) }}</p>
      </div>
      <Button :label="t('setup.hide')" text size="small" severity="secondary" @click="hide" />
    </div>
    <div class="mt-3 h-1.5 overflow-hidden rounded-full bg-zinc-100" role="progressbar" :aria-valuenow="doneCount" :aria-valuemax="steps.length">
      <div class="h-full rounded-full bg-emerald-500 transition-all" :style="{ width: `${(doneCount / steps.length) * 100}%` }" />
    </div>
    <ol class="mt-4 grid gap-2 sm:grid-cols-2">
      <li v-for="s in steps" :key="s.key">
        <component :is="s.done ? 'div' : s.to ? 'RouterLink' : 'button'" :to="s.to" type="button"
                   class="flex min-h-14 w-full items-center gap-3 rounded-lg border px-3 py-2 text-left transition"
                   :class="s.done ? 'border-transparent bg-zinc-50 text-zinc-500' : 'border-zinc-200 hover:border-zinc-400'"
                   @click="!s.done && s.edit && editCabin++">
          <span class="grid size-7 shrink-0 place-items-center rounded-full" :class="s.done ? 'bg-emerald-500 text-white' : 'border-2 border-zinc-300'">
            <i v-if="s.done" class="pi pi-check text-xs" />
          </span>
          <span class="min-w-0 flex-1">
            <span class="block text-sm font-medium" :class="s.done && 'line-through'">{{ t(`setup.steps.${s.key}.title`) }}</span>
            <span v-if="!s.done" class="block text-xs text-zinc-500">{{ t(`setup.steps.${s.key}.hint`) }}</span>
          </span>
          <i v-if="!s.done" class="pi pi-chevron-right text-xs text-zinc-400" />
        </component>
      </li>
    </ol>
  </section>
</template>
