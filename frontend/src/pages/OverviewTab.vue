<script setup>
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Avatar from 'primevue/avatar'
import AvatarGroup from 'primevue/avatargroup'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import DashCard from '../components/DashCard.vue'
import RichText from '../components/RichText.vue'
import { api, photoUrl } from '../api'
import { addShoppingItem, toggleShopping, toggleTodo } from '../actions'
import { cabin, INFO_TILES } from '../cabin'
import { status as maintenanceStatus } from '../maintenance'
import { dueLabel, overdueBadge, STATUS_STYLE } from '../maintenanceLabels'
import { colorFor, formatDay, initials, parseIsoDate, timeAgo, toIsoDate } from '../utils'
import { t } from '../i18n'

const SHOPPING_LIMIT = 6
const TODO_LIMIT = 5

const toast = useToast()
const todos = ref([])
const maintenance = ref([])
const shopping = ref([])
const bookings = ref([])
const log = ref([])
const photos = ref([])
const suggestions = ref([])
const newItem = ref('')

// Items ticked off here stay visible (struck through) until reload, so a mis-tap is easy to undo.
const tickedHere = ref(new Set())

onMounted(async () => {
  [todos.value, shopping.value, bookings.value, log.value, photos.value, suggestions.value, maintenance.value] = await Promise.all(
    ['/todos', '/shopping', '/bookings', '/log', '/photos', '/suggestions', '/maintenance'].map(api.get),
  )
})

const today = toIsoDate(new Date())
const day = iso => formatDay(parseIsoDate(iso))
const period = b => (!b.endDate || b.startDate === b.endDate ? day(b.startDate) : `${day(b.startDate)} – ${day(b.endDate)}`)

const staying = computed(() => bookings.value.filter(b => b.startDate <= today && b.endDate >= today))
const upcoming = computed(() => bookings.value.filter(b => b.startDate > today).slice(0, 4))
const recent = computed(() => bookings.value.filter(b => b.endDate < today).sort((a, b) => b.endDate.localeCompare(a.endDate)).slice(0, 3))
const postCount = b => log.value.filter(e => e.bookingId === b.id).length
const daysUntil = b => Math.round((parseIsoDate(b.startDate) - parseIsoDate(today)) / 864e5)

const visibleShopping = computed(() => shopping.value.filter(i => !i.bought || tickedHere.value.has(i.id)))
const openShopping = computed(() => shopping.value.filter(i => !i.bought).length)
const visibleTodos = computed(() => todos.value.filter(x => !x.done || tickedHere.value.has(x.id)))
const openTodos = computed(() => todos.value.filter(x => !x.done).length)

const latestPosts = computed(() => log.value.slice(0, 3))
const firstPhoto = entry => photos.value.findLast(p => p.logEntryId === entry.id)

// Maintenance that needs attention: overdue or due within the "soon" window.
const dueMaintenance = computed(() => maintenance.value
  .map(m => ({ m, ...maintenanceStatus(m, today) }))
  .filter(x => x.kind !== 'later')
  .sort((a, b) => a.days - b.days)
  .slice(0, 4))

const topIdeas = computed(() => suggestions.value.filter(s => !s.done).sort((a, b) => b.voters.length - a.voters.length).slice(0, 3))

const infoTiles = computed(() => INFO_TILES.filter(tile => !cabin.value.hiddenSections.includes(tile.key)))

async function tickShopping(item) {
  const updated = await toggleShopping(item)
  if (!updated) return
  tickedHere.value.add(item.id)
  shopping.value = shopping.value.map(i => (i.id === updated.id ? updated : i))
}

async function tickTodo(todo) {
  const updated = await toggleTodo(todo)
  if (!updated) return
  tickedHere.value.add(todo.id)
  todos.value = todos.value.map(x => (x.id === updated.id ? updated : x))
}

async function quickAdd() {
  if (!newItem.value.trim()) return
  shopping.value = [await addShoppingItem(newItem.value), ...shopping.value]
  newItem.value = ''
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
    <!-- Who's at the cabin right now -->
    <RouterLink v-for="b in staying" :key="b.id" to="/booking"
                class="flex items-center gap-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-emerald-900 transition hover:border-emerald-300">
      <span class="relative flex size-2.5"><span class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-75" /><span class="relative inline-flex size-2.5 rounded-full bg-emerald-500" /></span>
      <span class="text-sm"><b>{{ t('overview.atCabin', { name: b.guestName }) }}</b> · {{ t('overview.until', { date: day(b.endDate) }) }}</span>
    </RouterLink>

    <!-- About the cabin: description + info buttons; a button slides its content open below -->
    <DashCard :title="t('home.about')" icon="pi pi-home" tint="bg-zinc-100 text-zinc-700">
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
          </div>
        </div>
      </div>
    </DashCard>

    <!-- Mobile: one column in `order`; desktop: main column + side column -->
    <div class="flex flex-col gap-6 lg:grid lg:grid-cols-3 lg:items-start">
      <div class="contents lg:col-span-2 lg:flex lg:flex-col lg:gap-6">
        <DashCard class="order-1" :title="t('booking.upcoming')" icon="pi pi-calendar" tint="bg-sky-50 text-sky-700" to="/booking" add="/booking?new=1">
          <ul v-if="upcoming.length" class="-mx-2">
            <li v-for="b in upcoming" :key="b.id">
              <RouterLink :to="`/booking?id=${b.id}`" class="flex items-center gap-3 rounded-md px-2 py-2 transition hover:bg-zinc-50">
                <span :class="colorFor(b.guestName).bar" class="h-9 w-1 shrink-0 rounded-full" />
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-medium">{{ b.guestName }}<span v-if="b.guests" class="font-normal text-zinc-500"> · {{ t('booking.people', b.guests) }}</span></span>
                  <span class="block text-sm text-zinc-500">{{ period(b) }}</span>
                </span>
                <span class="shrink-0 text-xs text-zinc-500">{{ t('overview.inDays', daysUntil(b)) }}</span>
              </RouterLink>
            </li>
          </ul>
          <div v-else class="flex flex-wrap items-center justify-between gap-2 py-1">
            <p class="text-sm text-zinc-500">{{ t('booking.noUpcoming') }}</p>
            <RouterLink to="/booking?new=1" class="inline-flex min-h-11 items-center text-sm font-medium underline-offset-4 hover:underline">{{ t('booking.new') }}</RouterLink>
          </div>

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

        <DashCard class="order-5" :title="t('overview.latest')" icon="pi pi-book" tint="bg-violet-50 text-violet-700" to="/hyttebok" add="/hyttebok?new=1">
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
        <DashCard class="order-3" :title="t('tabs.shopping')" icon="pi pi-shopping-cart" tint="bg-amber-50 text-amber-700" to="/lister/handle" :count="openShopping">
          <form class="mb-1 flex gap-2" @submit.prevent="quickAdd">
            <InputText v-model="newItem" :placeholder="t('shopping.placeholder')" :aria-label="t('shopping.add')" class="min-w-0 flex-1" />
            <Button type="submit" icon="pi pi-plus" :aria-label="t('shopping.add')" :disabled="!newItem.trim()" />
          </form>
          <ul class="-mx-1">
            <li v-for="i in visibleShopping.slice(0, SHOPPING_LIMIT)" :key="i.id">
              <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded px-1">
                <Checkbox :model-value="i.bought" binary @update:model-value="tickShopping(i)" />
                <span class="min-w-0 flex-1 truncate" :class="i.bought && 'text-zinc-500 line-through'">{{ i.name }}</span>
                <span v-if="i.quantity" class="shrink-0 text-xs text-zinc-500">{{ i.quantity }}</span>
              </label>
            </li>
          </ul>
          <RouterLink v-if="visibleShopping.length > SHOPPING_LIMIT" to="/lister/handle" class="mt-1 inline-flex min-h-9 items-center text-sm text-zinc-500 hover:text-zinc-950">
            {{ t('overview.more', { n: visibleShopping.length - SHOPPING_LIMIT }) }}
          </RouterLink>
          <p v-if="!visibleShopping.length" class="py-1 text-sm text-zinc-500">{{ t('shopping.empty') }}</p>
        </DashCard>

        <DashCard class="order-4" :title="t('tabs.todos')" icon="pi pi-check-square" tint="bg-amber-50 text-amber-700" to="/lister/gjoremal" add="/lister/gjoremal?new=gjoremal" :count="openTodos">
          <ul class="-mx-1">
            <li v-for="x in visibleTodos.slice(0, TODO_LIMIT)" :key="x.id">
              <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded px-1">
                <Checkbox :model-value="x.done" binary @update:model-value="tickTodo(x)" />
                <span class="min-w-0 flex-1 truncate" :class="x.done && 'text-zinc-500 line-through'">{{ x.title }}</span>
              </label>
            </li>
          </ul>
          <RouterLink v-if="visibleTodos.length > TODO_LIMIT" to="/lister/gjoremal" class="mt-1 inline-flex min-h-9 items-center text-sm text-zinc-500 hover:text-zinc-950">
            {{ t('overview.more', { n: visibleTodos.length - TODO_LIMIT }) }}
          </RouterLink>
          <p v-if="!visibleTodos.length" class="py-1 text-sm text-zinc-500">{{ t('todo.allDone') }}</p>
        </DashCard>

        <DashCard class="order-4" :title="t('tabs.maintenance')" icon="pi pi-wrench" tint="bg-zinc-100 text-zinc-700" to="/vedlikehold" add="/vedlikehold?new=vedlikehold"
                  :count-label="overdueBadge(dueMaintenance.filter(x => x.days < 0).length)" count-class="bg-rose-50 text-rose-700">
          <ul v-if="dueMaintenance.length" class="-mx-2">
            <li v-for="x in dueMaintenance" :key="x.m.id">
              <RouterLink to="/vedlikehold" class="flex min-h-11 items-center gap-3 rounded-md px-2 transition hover:bg-zinc-50">
                <span class="h-7 w-1 shrink-0 rounded-full" :class="STATUS_STYLE[x.kind].bar" />
                <span class="min-w-0 flex-1 truncate text-sm">{{ x.m.name }}</span>
                <span class="shrink-0 text-xs font-medium" :class="STATUS_STYLE[x.kind].text">{{ dueLabel(x.m, today) }}</span>
              </RouterLink>
            </li>
          </ul>
          <p v-else class="flex items-center gap-2 py-1 text-sm text-zinc-500"><i class="pi pi-check-circle text-emerald-600" />{{ t('maintenance.allGood') }}</p>
        </DashCard>

        <DashCard class="order-6" :title="t('overview.topIdeas')" icon="pi pi-lightbulb" tint="bg-amber-50 text-amber-700" to="/lister/forslag" add="/lister/forslag?new=forslag">
          <ul v-if="topIdeas.length" class="space-y-2">
            <li v-for="s in topIdeas" :key="s.id" class="flex items-center gap-2.5">
              <span class="flex w-9 shrink-0 items-center gap-1 text-sm text-rose-600"><i class="pi pi-heart-fill text-xs" />{{ s.voters.length }}</span>
              <span class="min-w-0 flex-1 truncate text-sm">{{ s.title }}</span>
            </li>
          </ul>
          <p v-else class="py-1 text-sm text-zinc-500">{{ t('suggestions.empty') }}</p>
        </DashCard>
      </div>
    </div>

  </div>
</template>
