<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDeepLink } from '../deepLink'
import WeatherIcon from '../components/WeatherIcon.vue'
import StayWeather from '../components/StayWeather.vue'
import { dayWeather, loadWeatherDays } from '../weather'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Message from 'primevue/message'
import PageHeader from '../components/PageHeader.vue'
import { api } from '../api'
import { userName } from '../user'
import { colorFor, formatDay, parseIsoDate, timeAgo, toIsoDate } from '../utils'
import { localeTag, t } from '../i18n'

const toast = useToast()
const confirm = useConfirm()
const bookings = ref([])
const posts = ref([])
const cursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const formOpen = ref(false)
const form = ref({})
const selected = ref(null)

const weekdays = computed(() => [...Array(7)].map((_, i) => new Date(2024, 0, 1 + i).toLocaleDateString(localeTag(), { weekday: 'short' }).replace('.', ''))) // 1 Jan 2024 is a Monday
const today = toIsoDate(new Date())

const router = useRouter()
const takeQuery = useDeepLink()


onMounted(async () => {
  await load()
  const query = takeQuery()
  if (query.id) selected.value = bookings.value.find(b => b.id === Number(query.id)) || null
  if (query.new) openNew()
})

async function load() {
  [bookings.value, posts.value] = await Promise.all([api.get('/bookings'), api.get('/log')])
}

// Each booking gets a fixed row ("lane") so multi-day bars line up across days.
const lanes = computed(() => {
  const ends = []
  const result = new Map()
  for (const b of bookings.value) {
    let lane = ends.findIndex(end => end < b.startDate)
    if (lane === -1) lane = ends.length
    ends[lane] = b.endDate
    result.set(b.id, lane)
  }
  return result
})

const monthLabel = computed(() => cursor.value.toLocaleDateString(localeTag(), { month: 'long', year: 'numeric' }))

const weeks = computed(() => {
  const first = cursor.value
  const start = new Date(first)
  start.setDate(1 - ((first.getDay() + 6) % 7))
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0)

  const result = []
  for (const d = new Date(start); d <= last || d.getDay() !== 1; d.setDate(d.getDate() + 1)) {
    if (d.getDay() === 1) result.push([])
    result.at(-1).push({ iso: toIsoDate(d), day: d.getDate(), inMonth: d.getMonth() === first.getMonth() })
  }

  return result.map(days => {
    const inWeek = bookings.value.filter(b => b.startDate <= days[6].iso && b.endDate >= days[0].iso)
    const laneCount = Math.max(0, ...inWeek.map(b => lanes.value.get(b.id) + 1))
    return days.map((day, i) => ({
      ...day,
      slots: Array.from({ length: laneCount }, (_, lane) => {
        const b = inWeek.find(x => lanes.value.get(x.id) === lane && x.startDate <= day.iso && x.endDate >= day.iso)
        return b && { booking: b, isStart: b.startDate === day.iso, isEnd: b.endDate === day.iso, showName: b.startDate === day.iso || i === 0 }
      }),
    }))
  })
})

watch(() => [weeks.value[0][0].iso, weeks.value.at(-1)[6].iso], ([from, to]) => loadWeatherDays(from, to), { immediate: true })

const upcoming = computed(() => bookings.value.filter(b => b.endDate >= today))
const RECENT_LIMIT = 5
const recent = computed(() => bookings.value.filter(b => b.endDate < today).sort((a, b) => b.endDate.localeCompare(a.endDate)).slice(0, RECENT_LIMIT))
const postCount = b => posts.value.filter(p => p.bookingId === b.id).length
const bookedDays = computed(() => {
  const set = new Set()
  for (const b of bookings.value) {
    if (b.id === form.value.id) continue
    for (let d = parseIsoDate(b.startDate); toIsoDate(d) <= b.endDate; d.setDate(d.getDate() + 1)) set.add(toIsoDate(d))
  }
  return set
})

const range = computed(() => {
  const [start, end] = form.value.range || []
  return start && { start: toIsoDate(start), end: toIsoDate(end || start) }
})

const slotIso = d => toIsoDate(new Date(d.year, d.month, d.day))
const isBooked = d => bookedDays.value.has(slotIso(d))
const isPicked = d => range.value && slotIso(d) >= range.value.start && slotIso(d) <= range.value.end

const conflicts = computed(() => range.value
  ? bookings.value.filter(b => b.id !== form.value.id && b.startDate <= range.value.end && b.endDate >= range.value.start)
  : [])


// The chosen dates in words, so nobody has to decode circles in a calendar.
const rangeSummary = computed(() => {
  if (!range.value) return null
  const stay = { startDate: range.value.start, endDate: range.value.end }
  return stay.startDate === stay.endDate
    ? { period: formatDay(parseIsoDate(stay.startDate)) }
    : { period: period(stay), nights: t('booking.nights', nights(stay)) }
})

const nights = b => Math.round((parseIsoDate(b.endDate) - parseIsoDate(b.startDate)) / 864e5)
const period = b => `${formatDay(parseIsoDate(b.startDate))} – ${formatDay(parseIsoDate(b.endDate))}`

function shiftMonth(delta) {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
}

// Two ways to pick dates: the small calendar in the form, or tapping the first and then the last day in the
// big calendar, which opens the form with them filled in. `picking` is the first tap.
const picking = ref(null)
const hoverIso = ref(null)
// Only a real mouse previews the range; on touch screens "hover" just follows the last tap.
const canHover = matchMedia('(hover: hover)').matches
const newDraft = () => ({ guestName: userName.value, guests: 1, note: '' })

function openNew() {
  picking.value = null
  form.value = { ...newDraft(), range: null }
  formOpen.value = true
}

function pickDay(iso) {
  const start = picking.value?.start
  if (!start) {
    picking.value = { start: iso }
    hoverIso.value = iso
    return
  }
  const [first, last] = iso < start ? [iso, start] : [start, iso]
  form.value = { ...newDraft(), range: [parseIsoDate(first), parseIsoDate(last)] }
  picking.value = null
  formOpen.value = true
}

// The span shown while picking: from the first tap to the day under the pointer (desktop).
function pickState(iso) {
  const p = picking.value
  if (!p?.start) return null
  const other = hoverIso.value || p.start
  const [a, b] = other < p.start ? [other, p.start] : [p.start, other]
  if (iso === p.start) return 'end'
  return iso >= a && iso <= b ? 'inside' : null
}

function openEdit(b) {
  selected.value = null
  form.value = { ...b, range: [parseIsoDate(b.startDate), parseIsoDate(b.endDate)] }
  formOpen.value = true
}

async function save() {
  const { range: _, ...rest } = form.value
  const body = { ...rest, startDate: range.value.start, endDate: range.value.end }
  await (body.id ? api.put('/bookings', body) : api.post('/bookings', body))
  formOpen.value = false
  toast.add({ severity: 'success', summary: body.id ? t('booking.updated') : t('booking.created'), life: 2500 })
  cursor.value = new Date(parseIsoDate(body.startDate).setDate(1))
  await load()
}

function remove(b) {
  confirm.require({
    header: t('booking.deleteTitle'),
    message: `${b.guestName}, ${period(b)}`,
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      selected.value = null
      await api.del(`/bookings/${b.id}`)
      await load()
    },
  })
}
</script>

<template>
  <PageHeader :subtitle="t('booking.subtitle')">
    <Button :label="t('booking.new')" icon="pi pi-plus" @click="openNew()" />
  </PageHeader>

  <div class="grid gap-6 lg:grid-cols-3">
    <!-- Calendar -->
    <!-- While picking dates: what to tap next, and a way out. Fixed at the bottom so it stays in view while scrolling. -->
    <div v-if="picking" class="fixed inset-x-0 bottom-0 z-20 border-t border-emerald-200 bg-emerald-50 pb-[env(safe-area-inset-bottom)] text-emerald-900 shadow-lg" role="status">
      <div class="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2 text-sm">
        <i class="pi pi-calendar-plus" />
        <span class="flex-1 font-medium">{{ t('booking.pickEnd', { date: formatDay(parseIsoDate(picking.start)) }) }}</span>
        <Button :label="t('common.cancel')" size="small" outlined severity="secondary" class="!min-h-11" @click="picking = null" />
      </div>
    </div>

    <section class="card overflow-hidden lg:col-span-2">
      <div class="flex items-center justify-between border-b border-zinc-100 px-4 py-3">
        <Button icon="pi pi-chevron-left" text rounded severity="secondary" :aria-label="t('booking.prevMonth')" @click="shiftMonth(-1)" />
        <div class="flex items-center gap-2">
          <h2 class="text-lg font-semibold capitalize">{{ monthLabel }}</h2>
          <Button :label="t('common.today')" size="small" text @click="cursor = new Date(new Date().setDate(1))" />
        </div>
        <Button icon="pi pi-chevron-right" text rounded severity="secondary" :aria-label="t('booking.nextMonth')" @click="shiftMonth(1)" />
      </div>

      <div class="grid grid-cols-7 border-b border-zinc-100 bg-zinc-50 text-center text-xs font-medium uppercase tracking-wide text-zinc-500">
        <div v-for="d in weekdays" :key="d" class="py-2">{{ d }}</div>
      </div>

      <div v-for="(week, w) in weeks" :key="w" class="grid grid-cols-7 border-b border-zinc-100 last:border-0">
        <div v-for="day in week" :key="day.iso"
             class="group flex min-h-20 cursor-pointer flex-col border-r border-zinc-100 pb-1 last:border-r-0 hover:bg-zinc-100 sm:min-h-24"
             :class="pickState(day.iso) ? 'bg-emerald-50' : !day.inMonth && 'bg-zinc-50/60'"
             @click="pickDay(day.iso)" @mouseenter="canHover && (hoverIso = day.iso)">
          <!-- Day number alone at the top, so a temperature next to it isn't read as the date -->
          <div class="flex justify-end p-1.5">
            <span class="grid size-7 shrink-0 place-items-center rounded-full text-sm"
                  :class="pickState(day.iso) === 'end' ? 'bg-emerald-600 font-semibold text-white' : day.iso === today ? 'bg-zinc-950 font-semibold text-surface' : day.inMonth ? 'text-zinc-700' : 'text-zinc-300'">
              {{ day.day }}
            </span>
          </div>
          <div class="space-y-1">
            <template v-for="(slot, i) in day.slots" :key="i">
              <div v-if="slot" role="button" tabindex="0" :aria-label="`${slot.booking.guestName}: ${period(slot.booking)}`"
                   class="h-6 truncate px-1 text-[10px] font-medium leading-6 text-white transition hover:brightness-110 sm:h-5 sm:px-1.5 sm:text-[11px] sm:leading-5"
                   :class="[colorFor(slot.booking.guestName).bar, slot.isStart && 'ml-1 rounded-l-md', slot.isEnd && 'mr-1 rounded-r-md']"
                   :title="`${slot.booking.guestName}: ${period(slot.booking)}`" @click.stop="picking ? pickDay(day.iso) : (selected = slot.booking)" @keydown.enter.stop="selected = slot.booking">
                <span v-if="slot.showName">{{ slot.booking.guestName }}</span>
              </div>
              <div v-else class="h-5" />
            </template>
          </div>
          <!-- Weather at the bottom: measured, forecast, or (≈) the usual temperature for the date -->
          <div v-if="dayWeather(day.iso)" class="mt-auto flex items-center gap-0.5 px-1.5 pt-1 leading-none">
            <template v-if="dayWeather(day.iso).symbol">
              <WeatherIcon :symbol="dayWeather(day.iso).symbol" :uncertain="dayWeather(day.iso).uncertain" size="size-5" />
              <span class="text-[10px] tabular-nums text-zinc-500 sm:text-xs" :class="dayWeather(day.iso).uncertain && 'opacity-60'">{{ Math.round(dayWeather(day.iso).maxTemp) }}°</span>
            </template>
            <span v-else class="text-[10px] tabular-nums text-zinc-500 sm:text-xs"
                  :title="t('weather.normalDay', { max: Math.round(dayWeather(day.iso).maxTemp), min: Math.round(dayWeather(day.iso).minTemp) })">≈{{ Math.round(dayWeather(day.iso).maxTemp) }}°</span>
          </div>
        </div>
      </div>
      <p class="border-t border-zinc-100 px-4 py-2 text-xs text-zinc-500">
        {{ t('weather.credit') }}
        <a href="https://www.met.no" target="_blank" rel="noopener" class="underline underline-offset-2">MET Norway</a> ·
        <a href="https://open-meteo.com" target="_blank" rel="noopener" class="underline underline-offset-2">Open-Meteo</a>.
        {{ t('weather.uncertainHint') }}
      </p>
    </section>


    <div class="flex flex-col gap-6">
    <!-- Upcoming -->
    <section class="card p-5">
      <h2 class="mb-4 text-lg font-semibold">{{ t('booking.upcoming') }}</h2>
      <ul v-if="upcoming.length" class="space-y-2">
        <li v-for="b in upcoming" :key="b.id">
          <button class="flex w-full items-center gap-3 rounded-md p-2 text-left transition hover:bg-zinc-100" @click="selected = b">
            <span :class="colorFor(b.guestName).bar" class="h-10 w-1.5 shrink-0 rounded-full" />
            <span class="min-w-0 flex-1">
              <span class="block truncate font-medium">{{ b.guestName }}</span>
              <span class="block text-sm text-zinc-500">{{ period(b) }}</span>
            </span>
            <span class="flex shrink-0 flex-col items-end gap-1 text-xs text-zinc-600">
              <span class="rounded-full bg-zinc-100 px-2 py-0.5">{{ t('booking.nights', nights(b)) }}</span>
              <span v-if="b.guests" class="flex items-center gap-1 text-zinc-500"><i class="pi pi-users text-[10px]" />{{ t('booking.people', b.guests) }}</span>
            </span>
          </button>
        </li>
      </ul>
      <p v-else class="text-sm text-zinc-500">{{ t('booking.noUpcoming') }}</p>
    </section>

    <!-- Recent: open one to see its posts or write about the stay -->
    <section v-if="recent.length" class="card p-5">
      <h2 class="mb-4 text-lg font-semibold">{{ t('booking.recent') }}</h2>
      <ul class="space-y-2">
        <li v-for="b in recent" :key="b.id">
          <button class="flex w-full items-center gap-3 rounded-md p-2 text-left transition hover:bg-zinc-100" @click="selected = b">
            <span :class="colorFor(b.guestName).bar" class="h-10 w-1.5 shrink-0 rounded-full opacity-40" />
            <span class="shrink-0">
              <span class="block font-medium">{{ b.guestName }}</span>
              <span class="block whitespace-nowrap text-sm text-zinc-500">{{ period(b) }}</span>
            </span>
            <span class="flex min-w-0 flex-1 flex-col items-end gap-1 text-right text-xs text-zinc-500">
              {{ timeAgo(parseIsoDate(b.endDate)) }}
              <span v-if="postCount(b)" class="flex items-center gap-1"><i class="pi pi-book text-[10px]" />{{ postCount(b) }}</span>
            </span>
          </button>
        </li>
      </ul>
    </section>
    </div>
  </div>

  <!-- Details -->
  <Dialog :visible="!!selected" modal dismissable-mask :header="selected?.guestName" class="w-[26rem] max-w-[calc(100vw-2rem)]" @update:visible="selected = null">
    <div v-if="selected" class="space-y-3">
      <p class="flex items-center gap-2 text-zinc-600"><i class="pi pi-calendar text-zinc-500" />{{ period(selected) }} · {{ t('booking.nights', nights(selected)) }}</p>
      <p v-if="selected.guests" class="flex items-center gap-2 text-zinc-600"><i class="pi pi-users text-zinc-500" />{{ t('booking.people', selected.guests) }}</p>
      <div v-if="posts.some(p => p.bookingId === selected.id)">
        <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-zinc-500">{{ t('booking.posts') }}</p>
        <RouterLink v-for="p in posts.filter(p => p.bookingId === selected.id)" :key="p.id" to="/hyttebok"
                    class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-zinc-100">
          <i class="pi pi-book text-xs text-zinc-500" />{{ p.title || p.content }}
        </RouterLink>
      </div>
      <StayWeather :start="selected.startDate" :end="selected.endDate" />
      <p v-if="selected.note" class="whitespace-pre-line rounded-md bg-zinc-50 p-3 text-sm text-zinc-600">{{ selected.note }}</p>
    </div>
    <template #footer>
      <Button :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove(selected)" />
      <Button :label="t('booking.writePost')" icon="pi pi-book" outlined severity="secondary" @click="router.push(`/hyttebok?new=1&stay=${selected.id}`)" />
      <Button :label="t('common.edit')" icon="pi pi-pencil" @click="openEdit(selected)" />
    </template>
  </Dialog>

  <!-- Create / edit -->
  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('booking.edit') : t('booking.new')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <form id="booking-form" class="flex flex-col gap-4" @submit.prevent="save">
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('booking.who') }}<InputText v-model="form.guestName" required :placeholder="t('booking.namePlaceholder')" /></label>
      <div class="flex flex-col gap-2">
        <DatePicker v-model="form.range" selection-mode="range" inline :manual-input="false" class="w-full" :aria-label="t('booking.dates')">
          <template #date="{ date }">
            <!-- Already booked by someone else: amber number + dot, unless it's part of your selection -->
            <span class="relative" :class="isBooked(date) && !isPicked(date) && 'font-semibold text-amber-700'">
              {{ date.day }}
              <span v-if="isBooked(date)" class="absolute -bottom-2 left-1/2 size-1.5 -translate-x-1/2 rounded-full bg-amber-500" />
            </span>
          </template>
        </DatePicker>
        <span class="text-xs text-zinc-500"><span class="mr-1 inline-block size-1.5 rounded-full bg-amber-500 align-middle" />{{ t('booking.alreadyBooked') }}</span>
        <!-- The chosen dates in words, so nobody has to decode circles in a calendar -->
        <p v-if="rangeSummary" class="rounded-md bg-zinc-100 px-3 py-2 text-sm">
          <span class="font-semibold">{{ rangeSummary.period }}</span><template v-if="rangeSummary.nights"> · {{ rangeSummary.nights }}</template>
        </p>
        <StayWeather v-if="range" :start="range.start" :end="range.end" />
      </div>
      <Message v-if="conflicts.length" severity="warn" icon="pi pi-exclamation-triangle">
        {{ t('booking.overlapWith') }}
        <span v-for="(c, i) in conflicts" :key="c.id"><b>{{ c.guestName }}</b> ({{ period(c) }}){{ i < conflicts.length - 1 ? ', ' : '' }}</span>.
        {{ t('booking.overlapHint') }}
      </Message>
      <!-- Buttons only: no text field that pops up the phone keyboard -->
      <div class="flex items-center justify-between gap-3 text-sm font-medium" role="group" aria-labelledby="guests-label">
        <span id="guests-label">{{ t('booking.guests') }}</span>
        <span class="flex items-center gap-1">
          <Button type="button" icon="pi pi-minus" rounded outlined severity="secondary" class="!size-11" :aria-label="t('booking.fewer')" :disabled="(form.guests || 1) <= 1" @click="form.guests = Math.max(1, (form.guests || 1) - 1)" />
          <span class="w-10 text-center text-lg tabular-nums" aria-live="polite">{{ form.guests || 1 }}</span>
          <Button type="button" icon="pi pi-plus" rounded outlined severity="secondary" class="!size-11" :aria-label="t('booking.more')" @click="form.guests = Math.min(99, (form.guests || 1) + 1)" />
        </span>
      </div>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('booking.note') }}<Textarea v-model="form.note" rows="2" auto-resize :placeholder="t('booking.notePlaceholder')" /></label>
    </form>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="booking-form" :label="conflicts.length ? t('booking.saveAnyway') : t('common.save')" icon="pi pi-check"
              :severity="conflicts.length ? 'warn' : undefined" :disabled="!range || !form.guestName" />
    </template>
  </Dialog>
</template>
