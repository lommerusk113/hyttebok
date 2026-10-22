<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import Avatar from 'primevue/avatar'
import AvatarGroup from 'primevue/avatargroup'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import CheckInDialog from '../components/CheckInDialog.vue'
import CheckOutDialog from '../components/CheckOutDialog.vue'
import DashCard from '../components/DashCard.vue'
import RichText from '../components/RichText.vue'
import WeatherIcon from '../components/WeatherIcon.vue'
import { loadWeather, loadWeatherDays, stayWeather } from '../weather'
import { api, photoUrl } from '../api'
import { toggleShopping, undoToast } from '../actions'
import { cabin, editCabin, INFO_TILES } from '../cabin'
import { status as maintenanceStatus } from '../maintenance'
import { dueLabel, overdueBadge, STATUS_STYLE } from '../maintenanceLabels'
import { colorFor, formatDay, initials, parseIsoDate, timeAgo, toIsoDate } from '../utils'
import { bookToday, isExpected, isHere } from '../stay'
import { askName, userName } from '../user'
import { localeTag } from '../i18n'
import { t } from '../i18n'

const SHOPPING_LIMIT = 6
const TASK_LIMIT = 6

const toast = useToast()
const todos = ref([])
const maintenance = ref([])
const shopping = ref([])
const bookings = ref([])
const log = ref([])
const photos = ref([])
const suggestions = ref([])

// Items ticked off here stay visible (struck through) until reload, so a mis-tap is easy to undo.
const tickedHere = ref(new Set())

onMounted(loadWeather)

onMounted(async () => {
  [todos.value, shopping.value, bookings.value, log.value, photos.value, suggestions.value, maintenance.value] = await Promise.all(
    ['/todos', '/shopping', '/bookings', '/log', '/photos', '/suggestions', '/maintenance'].map(api.get),
  )
})

const today = toIsoDate(new Date())
const day = iso => formatDay(parseIsoDate(iso))
const period = b => (!b.endDate || b.startDate === b.endDate ? day(b.startDate) : `${day(b.startDate)} – ${day(b.endDate)}`)

// "Er på hytta nå" is who checked in; a booking for today that nobody checked in to yet offers the check-in.
const here = computed(() => bookings.value.filter(isHere))
const expected = computed(() => bookings.value.filter(isExpected))
const time = iso => new Date(iso).toLocaleTimeString(localeTag(), { hour: '2-digit', minute: '2-digit' })
const checkedInWhen = b => (b.checkedInAt.slice(0, 10) === today ? time(b.checkedInAt) : `${day(b.checkedInAt.slice(0, 10))} ${time(b.checkedInAt)}`)

const stay = ref(null) // the booking being checked in to or out of
const checkingIn = ref(false)
const checkingOut = ref(false)

function startCheckIn(b) {
  if (!userName.value) return (askName.value = true)
  stay.value = b
  checkingIn.value = true
}

// At the cabin without a booking: a booking for today first.
async function checkInUnbooked() {
  if (!userName.value) return (askName.value = true)
  const b = await bookToday()
  bookings.value = [...bookings.value, b]
  startCheckIn(b)
}

function startCheckOut(b) {
  if (!userName.value) return (askName.value = true)
  stay.value = b
  checkingOut.value = true
}

const updated = b => { bookings.value = bookings.value.map(x => (x.id === b.id ? b : x)) }
const upcoming = computed(() => bookings.value.filter(b => b.startDate > today).slice(0, 4))
// Weather for each upcoming stay as a whole (average high over its days), not just the arrival day.
watch(upcoming, list => list.forEach(b => loadWeatherDays(b.startDate, b.endDate)), { immediate: true })
const recent = computed(() => bookings.value.filter(b => b.endDate < today).sort((a, b) => b.endDate.localeCompare(a.endDate)).slice(0, 3))
const postCount = b => log.value.filter(e => e.bookingId === b.id).length
const daysUntil = b => Math.round((parseIsoDate(b.startDate) - parseIsoDate(today)) / 864e5)

const visibleShopping = computed(() => shopping.value.filter(i => !i.bought || tickedHere.value.has(i.id)))
const openShopping = computed(() => shopping.value.filter(i => !i.bought).length)

const latestPosts = computed(() => log.value.slice(0, 3))
const firstPhoto = entry => photos.value.findLast(p => p.logEntryId === entry.id)

// Vedlikehold that needs attention: overdue recurring tasks first, then open one-off jobs, then recurring ones due soon.
const dueMaintenance = computed(() => maintenance.value
  .map(m => ({ m, ...maintenanceStatus(m, today) }))
  .filter(x => x.kind !== 'later')
  .sort((a, b) => a.days - b.days))
const overdueCount = computed(() => dueMaintenance.value.filter(x => x.kind === 'overdue').length)
const taskRows = computed(() => [
  ...dueMaintenance.value.filter(x => x.kind === 'overdue').map(x => ({ key: `m-${x.m.id}`, ...x })),
  ...todos.value.filter(x => !x.done).map(x => ({ key: `t-${x.id}`, todo: x })),
  ...dueMaintenance.value.filter(x => x.kind !== 'overdue').map(x => ({ key: `m-${x.m.id}`, ...x })),
])

const topIdeas = computed(() => suggestions.value.filter(s => !s.done).sort((a, b) => b.voters.length - a.voters.length).slice(0, 3))

const infoTiles = computed(() => INFO_TILES.filter(tile => !cabin.value.hiddenSections.includes(tile.key)))

async function tickShopping(item) {
  const updated = await toggleShopping(item)
  if (!updated) return
  tickedHere.value.add(item.id)
  shopping.value = shopping.value.map(i => (i.id === updated.id ? updated : i))
  if (updated.bought) undoToast(toast, t('shopping.boughtToast', { name: updated.name }), () => tickShopping(updated))
}

// One info section open at a time; `shownInfo` lags behind on close so the panel can slide shut with its content.
const openInfo = ref(null)
const shownInfo = ref(null)

function toggleInfo(key) {
  openInfo.value = openInfo.value === key ? null : key
  if (openInfo.value) shownInfo.value = key
}

function copy(text) {
  navigator.clipboard?.writeText(text)
  toast.add({ severity: 'info', summary: t('common.copied'), life: 1500 })
}
</script>

<template>
  <div class="space-y-6">
    <!-- Who's at the cabin right now (checked in), and who's booked today but hasn't checked in -->
    <div v-for="b in here" :key="b.id" class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-2.5 text-emerald-900">
      <span class="relative flex size-2.5"><span class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75" /><span class="relative inline-flex size-2.5 rounded-full bg-emerald-500" /></span>
      <span class="min-w-0 flex-1 text-sm">
        <b>{{ t('overview.atCabin', { name: b.guestName }) }}</b> · {{ t('stay.since', { time: checkedInWhen(b) }) }} · {{ t('overview.until', { date: day(b.endDate) }) }}
      </span>
      <Button :label="t('stay.checkOut')" icon="pi pi-sign-out" size="small" severity="success" outlined class="!bg-surface" @click="startCheckOut(b)" />
    </div>
    <div v-for="b in expected" :key="b.id" class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-sky-200 bg-sky-50 px-4 py-2.5 text-sky-900">
      <i class="pi pi-calendar text-sm" />
      <span class="min-w-0 flex-1 text-sm"><b>{{ t('stay.bookedToday', { name: b.guestName }) }}</b> · {{ t('overview.until', { date: day(b.endDate) }) }}</span>
      <Button :label="t('stay.checkIn')" icon="pi pi-sign-in" size="small" @click="startCheckIn(b)" />
    </div>
    <CheckInDialog v-model:visible="checkingIn" :booking="stay" @saved="updated" />
    <CheckOutDialog v-model:visible="checkingOut" :booking="stay" @saved="updated" />

    <!-- About the cabin: description + info buttons; a button slides its content open below -->
    <DashCard :title="t('home.about')" icon="pi pi-home" tint="bg-zinc-100 text-zinc-700">
      <template #action>
        <RouterLink to="/wiki" class="-my-2 inline-flex min-h-11 items-center gap-1.5 text-sm font-medium text-zinc-500 hover:text-zinc-950">
          <i class="pi pi-info-circle text-xs" />{{ t('wiki.title') }}
        </RouterLink>
        <Button icon="pi pi-pencil" text rounded size="small" severity="secondary" class="-my-2" :aria-label="t('home.editInfo')" v-tooltip.left="t('home.editInfo')" @click="editCabin++" />
      </template>
      <p v-if="cabin.description" class="mb-4 max-w-3xl whitespace-pre-line leading-relaxed text-zinc-600">{{ cabin.description }}</p>

      <div v-if="infoTiles.length" class="flex flex-wrap gap-2">
        <button v-for="tile in infoTiles" :key="tile.key" :aria-expanded="openInfo === tile.key" aria-controls="info-panel"
                class="flex min-h-11 items-center gap-2 rounded-full border py-1 pl-1.5 pr-3.5 text-sm font-medium transition"
                :class="openInfo === tile.key ? 'border-zinc-950 bg-zinc-950 text-surface' : 'border-zinc-200 hover:border-zinc-300 hover:bg-zinc-50'"
                @click="toggleInfo(tile.key)">
          <span class="grid size-8 place-items-center rounded-full" :class="openInfo === tile.key ? 'bg-surface/15' : tile.tint"><i :class="tile.icon" class="text-xs" /></span>
          {{ t(`info.${tile.key}`) }}
          <i class="pi pi-chevron-down text-[10px] transition-transform duration-300" :class="openInfo === tile.key && 'rotate-180'" />
        </button>
      </div>

      <!-- grid-rows 0fr → 1fr animates to the content's natural height -->
      <div id="info-panel" class="grid transition-all duration-300 ease-out"
           :class="openInfo ? 'mt-3 grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'">
        <div class="overflow-hidden">
          <div v-if="shownInfo" class="rounded-lg bg-zinc-50 p-4">
            <dl v-if="shownInfo === 'wifi'" class="grid gap-3 sm:grid-cols-2">
              <div v-for="[label, value] in [[t('home.wifiNetwork'), cabin.wifiName], [t('home.wifiPassword'), cabin.wifiPassword]]" :key="label"
                   class="flex items-center justify-between gap-2 rounded-md bg-surface px-3 py-2">
                <div class="min-w-0">
                  <dt class="text-xs text-zinc-500">{{ label }}</dt>
                  <dd class="truncate font-mono font-medium">{{ value || '–' }}</dd>
                </div>
                <Button v-if="value" icon="pi pi-copy" text rounded size="small" :aria-label="`${t('common.copy')}: ${label}`" @click="copy(value)" />
              </div>
            </dl>
            <RichText v-else-if="cabin[shownInfo]" :text="cabin[shownInfo]" class="text-zinc-700" />
            <p v-else class="text-sm text-zinc-500">{{ t('overview.notFilled') }}</p>
            <!-- Leaving: note what's running out while you're still there -->
            <RouterLink v-if="shownInfo === 'departureInfo'" to="/handleliste/sjekkliste"
                        class="mt-3 inline-flex min-h-11 items-center gap-2 text-sm font-medium underline-offset-4 hover:underline">
              <i class="pi pi-list-check text-xs" />{{ t('checklist.goThrough') }}
            </RouterLink>
          </div>
        </div>
      </div>
    </DashCard>

    <!-- Mobile: one column in `order`; desktop: main column + side column -->
    <div class="flex flex-col gap-6 lg:grid lg:grid-cols-3 lg:items-start">
      <div class="contents lg:col-span-2 lg:flex lg:flex-col lg:gap-6">
        <DashCard class="order-1" :title="t('booking.upcoming')" icon="pi pi-calendar" tint="bg-sky-50 text-sky-700" to="/booking">
          <ul v-if="upcoming.length" class="-mx-2">
            <li v-for="b in upcoming" :key="b.id">
              <RouterLink :to="`/booking?id=${b.id}`" class="flex items-center gap-3 rounded-md px-2 py-2 transition hover:bg-zinc-50">
                <span :class="colorFor(b.guestName).bar" class="h-9 w-1 shrink-0 rounded-full" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-medium">{{ b.guestName }}<span v-if="b.guests" class="font-normal text-zinc-500"> · {{ t('booking.people', b.guests) }}</span></span>
                  <span class="block text-sm text-zinc-500">{{ period(b) }}</span>
                </span>
                <span class="flex shrink-0 flex-col items-end gap-0.5 text-xs text-zinc-500">
                  {{ t('overview.inDays', daysUntil(b)) }}
                  <span v-if="stayWeather(b.startDate, b.endDate)" class="flex items-center gap-1 tabular-nums"
                        :title="t('weather.stayAverage', { max: Math.round(stayWeather(b.startDate, b.endDate).maxTemp) })">
                    <WeatherIcon v-if="stayWeather(b.startDate, b.endDate).symbol" :symbol="stayWeather(b.startDate, b.endDate).symbol" :uncertain="stayWeather(b.startDate, b.endDate).uncertain" size="size-5" />
                    {{ stayWeather(b.startDate, b.endDate).symbol ? '' : '≈' }}{{ Math.round(stayWeather(b.startDate, b.endDate).maxTemp) }}°
                  </span>
                </span>
              </RouterLink>
            </li>
          </ul>
          <div v-else class="flex flex-wrap items-center justify-between gap-2 py-1">
            <p class="text-sm text-zinc-500">{{ t('booking.noUpcoming') }}</p>
            <RouterLink to="/booking?new=1" class="inline-flex min-h-11 items-center text-sm font-medium underline-offset-4 hover:underline">{{ t('booking.new') }}</RouterLink>
          </div>
          <!-- At the cabin without a booking (nobody checked in or expected today) -->
          <button v-if="!here.length && !expected.length" type="button" class="mt-1 inline-flex min-h-11 items-center gap-2 text-sm text-zinc-500 underline-offset-4 hover:text-zinc-900 hover:underline"
                  @click="checkInUnbooked">
            <i class="pi pi-sign-in text-xs" />{{ t('stay.hereUnbooked') }}
          </button>

          <template v-if="recent.length">
            <p class="mb-1 mt-3 border-t border-zinc-100 pt-3 text-xs font-medium uppercase tracking-wide text-zinc-500">{{ t('booking.recent') }}</p>
            <ul class="-mx-2">
              <li v-for="b in recent" :key="b.id">
                <RouterLink :to="`/booking?id=${b.id}`" class="flex items-center gap-3 rounded-md px-2 py-1.5 transition hover:bg-zinc-50">
                  <span :class="colorFor(b.guestName).bar" class="h-9 w-1 shrink-0 rounded-full opacity-40" />
                  <span class="min-w-0 flex-1">
                    <span class="block truncate text-sm font-medium">{{ b.guestName }}</span>
                    <span class="block truncate text-sm text-zinc-500">{{ period(b) }}</span>
                  </span>
                  <span class="flex shrink-0 flex-col items-end gap-0.5 text-xs text-zinc-500">
                    {{ timeAgo(parseIsoDate(b.endDate)) }}
                    <span v-if="postCount(b)" class="flex items-center gap-1"><i class="pi pi-book text-[10px]" />{{ postCount(b) }}</span>
                  </span>
                </RouterLink>
              </li>
            </ul>
          </template>
        </DashCard>

        <DashCard class="order-5" :title="t('overview.latest')" icon="pi pi-book" tint="bg-violet-50 text-violet-700" to="/hyttebok">
          <ul v-if="latestPosts.length" class="-mx-2">
            <li v-for="e in latestPosts" :key="e.id">
              <RouterLink :to="`/hyttebok?id=${e.id}`" class="flex items-center gap-3 rounded-md px-2 py-2 transition hover:bg-zinc-50">
                <img v-if="firstPhoto(e)" :src="photoUrl(firstPhoto(e).id)" alt="" loading="lazy" class="size-14 shrink-0 rounded-md object-cover">
                <span v-else class="grid size-14 shrink-0 place-items-center rounded-md bg-zinc-100 text-zinc-500"><i class="pi pi-book" /></span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-medium">{{ e.title || e.content }}</span>
                  <span class="block truncate text-sm text-zinc-500">{{ e.startDate ? period(e) : timeAgo(e.createdAt) }}</span>
                </span>
                <AvatarGroup v-if="e.participants.length" class="shrink-0">
                  <Avatar v-for="n in e.participants.slice(0, 3)" :key="n" :label="initials(n)" shape="circle" size="small"
                          :class="[colorFor(n).bg, colorFor(n).text]" class="!border-2 !border-surface !text-[10px] !font-semibold" />
                </AvatarGroup>
              </RouterLink>
            </li>
          </ul>
          <div v-else class="flex flex-wrap items-center justify-between gap-2 py-1">
            <p class="text-sm text-zinc-500">{{ t('overview.noPosts') }}</p>
            <RouterLink to="/hyttebok?new=1" class="inline-flex min-h-11 items-center text-sm font-medium underline-offset-4 hover:underline">{{ t('log.new') }}</RouterLink>
          </div>
        </DashCard>
      </div>

      <div class="contents lg:flex lg:flex-col lg:gap-6">
        <DashCard class="order-3" collapsible :title="t('tabs.shopping')" icon="pi pi-shopping-cart" tint="bg-amber-50 text-amber-700" to="/handleliste" :count="openShopping">
          <ul class="-mx-1">
            <li v-for="i in visibleShopping.slice(0, SHOPPING_LIMIT)" :key="i.id">
              <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded px-1">
                <Checkbox :model-value="i.bought" binary @update:model-value="tickShopping(i)" />
                <span class="min-w-0 flex-1 truncate" :class="i.bought && 'text-zinc-500 line-through'">{{ i.name }}</span>
                <span v-if="i.quantity" class="shrink-0 text-xs text-zinc-500">{{ i.quantity }}</span>
              </label>
            </li>
          </ul>
          <RouterLink v-if="visibleShopping.length > SHOPPING_LIMIT" to="/handleliste" class="mt-1 inline-flex min-h-9 items-center text-sm text-zinc-500 hover:text-zinc-950">
            {{ t('overview.more', { n: visibleShopping.length - SHOPPING_LIMIT }) }}
          </RouterLink>
          <p v-if="!visibleShopping.length" class="py-1 text-sm text-zinc-500">{{ t('shopping.empty') }}</p>
        </DashCard>

        <!-- Recurring tasks and one-off jobs together, like on the Vedlikehold tab -->
        <DashCard class="order-4" collapsible :title="t('tabs.maintenance')" icon="pi pi-wrench" tint="bg-zinc-100 text-zinc-700" to="/vedlikehold"
                  :count="overdueCount" count-class="bg-rose-50 text-rose-700" :count-title="overdueBadge(overdueCount)">
          <ul v-if="taskRows.length" class="-mx-2">
            <li v-for="r in taskRows.slice(0, TASK_LIMIT)" :key="r.key">
              <!-- One-off jobs are ticked off on the Vedlikehold tab; here they just get their own colour -->
              <RouterLink v-if="r.todo" :to="`/vedlikehold?todo=${r.todo.id}`" class="flex min-h-11 items-center gap-3 rounded-md px-2 transition hover:bg-zinc-50">
                <span class="h-7 w-1 shrink-0 rounded-full bg-sky-400" />
                <span class="min-w-0 flex-1 truncate text-sm">{{ r.todo.title }}</span>
                <span class="shrink-0 text-xs font-medium text-sky-700">{{ t('maintenance.once') }}</span>
              </RouterLink>
              <RouterLink v-else :to="`/vedlikehold?id=${r.m.id}`" class="flex min-h-11 items-center gap-3 rounded-md px-2 transition hover:bg-zinc-50">
                <span class="h-7 w-1 shrink-0 rounded-full" :class="STATUS_STYLE[r.kind].bar" />
                <span class="min-w-0 flex-1 truncate text-sm">{{ r.m.name }}</span>
                <span class="shrink-0 text-xs font-medium" :class="STATUS_STYLE[r.kind].text">{{ dueLabel(r.m, today) }}</span>
              </RouterLink>
            </li>
          </ul>
          <RouterLink v-if="taskRows.length > TASK_LIMIT" to="/vedlikehold" class="mt-1 inline-flex min-h-9 items-center text-sm text-zinc-500 hover:text-zinc-950">
            {{ t('overview.more', { n: taskRows.length - TASK_LIMIT }) }}
          </RouterLink>
          <p v-if="!taskRows.length" class="flex items-center gap-2 py-1 text-sm text-zinc-500"><i class="pi pi-check-circle text-emerald-600" />{{ t('maintenance.allGood') }}</p>
        </DashCard>

        <!-- Suggestions live in their lists now: jobs under Vedlikehold, purchases on the shopping list -->
        <DashCard class="order-6" collapsible :title="t('overview.topIdeas')" icon="pi pi-lightbulb" tint="bg-amber-50 text-amber-700">
          <ul v-if="topIdeas.length" class="-mx-2">
            <li v-for="s in topIdeas" :key="s.id">
              <RouterLink :to="s.category === 'BUY' ? '/handleliste' : '/vedlikehold/forslag'" class="flex min-h-11 items-center gap-2.5 rounded-md px-2 transition hover:bg-zinc-50">
                <span class="flex w-9 shrink-0 items-center gap-1 text-sm text-rose-600"><i class="pi pi-heart-fill text-xs" />{{ s.voters.length }}</span>
                <span class="min-w-0 flex-1 truncate text-sm">{{ s.title }}</span>
                <span class="shrink-0 text-xs text-zinc-500">{{ t(s.category === 'BUY' ? 'suggestions.buy' : 'suggestions.do') }}</span>
              </RouterLink>
            </li>
          </ul>
          <p v-else class="py-1 text-sm text-zinc-500">{{ t('suggestions.empty') }}</p>
        </DashCard>
      </div>
    </div>

  </div>
</template>
