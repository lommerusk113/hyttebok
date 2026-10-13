<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDeepLink } from '../deepLink'
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

const conflicts = computed(() => range.value
  ? bookings.value.filter(b => b.id !== form.value.id && b.startDate <= range.value.end && b.endDate >= range.value.start)
  : [])

const nights = b => Math.round((parseIsoDate(b.endDate) - parseIsoDate(b.startDate)) / 864e5)
const period = b => `${formatDay(parseIsoDate(b.startDate))} – ${formatDay(parseIsoDate(b.endDate))}`

function shiftMonth(delta) {
  cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + delta, 1)
}

function openNew(iso) {
  const start = iso ? parseIsoDate(iso) : null
  form.value = { guestName: userName.value, note: '', range: start ? [start, null] : null }
  formOpen.value = true
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
             class="group min-h-20 cursor-pointer border-r border-zinc-100 pb-1.5 last:border-r-0 hover:bg-zinc-100 sm:min-h-24"
             :class="{ 'bg-zinc-50/60': !day.inMonth }" @click="openNew(day.iso)">
          <div class="flex justify-end p-1.5">
            <span class="grid size-7 place-items-center rounded-full text-sm"
                  :class="day.iso === today ? 'bg-zinc-950 font-semibold text-surface' : day.inMonth ? 'text-zinc-700' : 'text-zinc-300'">
              {{ day.day }}
            </span>
          </div>
          <div class="space-y-1">
            <template v-for="(slot, i) in day.slots" :key="i">
              <div v-if="slot" role="button" tabindex="0" :aria-label="`${slot.booking.guestName}: ${period(slot.booking)}`"
                   class="h-6 truncate px-1 text-[10px] font-medium leading-6 text-white transition hover:brightness-110 sm:h-5 sm:px-1.5 sm:text-[11px] sm:leading-5"
                   :class="[colorFor(slot.booking.guestName).bar, slot.isStart && 'ml-1 rounded-l-md', slot.isEnd && 'mr-1 rounded-r-md']"
                   :title="`${slot.booking.guestName}: ${period(slot.booking)}`" @click.stop="selected = slot.booking" @keydown.enter.stop="selected = slot.booking">
                <span v-if="slot.showName">{{ slot.booking.guestName }}</span>
              </div>
              <div v-else class="h-5" />
            </template>
          </div>
        </div>
      </div>
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
            <span class="shrink-0 rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600">{{ t('booking.nights', nights(b)) }}</span>
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
      <div v-if="posts.some(p => p.bookingId === selected.id)">
        <p class="mb-1.5 text-xs font-medium uppercase tracking-wide text-zinc-500">{{ t('booking.posts') }}</p>
        <RouterLink v-for="p in posts.filter(p => p.bookingId === selected.id)" :key="p.id" to="/hyttebok"
                    class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-zinc-100">
          <i class="pi pi-book text-xs text-zinc-500" />{{ p.title || p.content }}
        </RouterLink>
      </div>
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
      <div class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('booking.period') }}
        <DatePicker v-model="form.range" selection-mode="range" inline :manual-input="false" class="w-full">
          <template #date="{ date }">
            <span class="relative">
              {{ date.day }}
              <span v-if="bookedDays.has(toIsoDate(new Date(date.year, date.month, date.day)))" class="absolute -bottom-1.5 left-1/2 size-1 -translate-x-1/2 rounded-full bg-amber-500" />
            </span>
          </template>
        </DatePicker>
        <span class="text-xs font-normal text-zinc-500"><span class="mr-1 inline-block size-1.5 rounded-full bg-amber-500 align-middle" />{{ t('booking.alreadyBooked') }}</span>
      </div>
      <Message v-if="conflicts.length" severity="warn" icon="pi pi-exclamation-triangle">
        {{ t('booking.overlapWith') }}
        <span v-for="(c, i) in conflicts" :key="c.id"><b>{{ c.guestName }}</b> ({{ period(c) }}){{ i < conflicts.length - 1 ? ', ' : '' }}</span>.
        {{ t('booking.overlapHint') }}
      </Message>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('booking.note') }}<Textarea v-model="form.note" rows="2" auto-resize :placeholder="t('booking.notePlaceholder')" /></label>
    </form>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="booking-form" :label="conflicts.length ? t('booking.saveAnyway') : t('common.save')" icon="pi pi-check"
              :severity="conflicts.length ? 'warn' : undefined" :disabled="!range || !form.guestName" />
    </template>
  </Dialog>
</template>
