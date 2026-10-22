<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Paginator from 'primevue/paginator'
import Select from 'primevue/select'
import AuditList from '../components/AuditList.vue'
import { api } from '../api'
import { auth } from '../auth'
import { setAdminMode } from '../hytte'
import { colorFor, formatDate, initials, timeAgo } from '../utils'
import { locale, t } from '../i18n'

// The admin console (system admins): every cabin and every user. Search, filter, sort and paging happen on the
// server and live in the address (?q=&filter=&sort=&page=), so the back button and shared links keep them.
const route = useRoute()
const router = useRouter()
const tab = computed(() => ({ brukere: 'users', logg: 'log' })[route.params.tab] || 'cabins')
const TABS = [{ key: 'cabins', path: '/admin', icon: 'pi-home' }, { key: 'users', path: '/admin/brukere', icon: 'pi-users' }, { key: 'log', path: '/admin/logg', icon: 'pi-history' }]
const result = ref(null) // { items, total, pageSize, counts }
const loading = ref(false)
const search = ref(route.query.q || '')
const searchInput = ref()
const busy = ref(false)

const FILTERS = {
  cabins: [
    { value: 'ALL', dot: 'bg-zinc-400' },
    { value: 'ACTIVE', dot: 'bg-emerald-500' },
    { value: 'INACTIVE', dot: 'bg-amber-500' },
    { value: 'EMPTY', dot: 'bg-sky-500' },
    { value: 'DELETED', dot: 'bg-rose-500' },
  ],
  users: [
    { value: 'ALL', dot: 'bg-zinc-400' },
    { value: 'GOOGLE', dot: 'bg-sky-500' },
    { value: 'PASSWORD', dot: 'bg-violet-500' },
    { value: 'NONE', dot: 'bg-amber-500' },
  ],
}
const SORTS = { cabins: ['active', 'name', 'members', 'created'], users: ['active', 'name', 'created'], log: [] }
const filter = computed(() => route.query.filter || 'ALL')
const sort = computed(() => route.query.sort || 'active')
const page = computed(() => Number(route.query.page) || 0)
const sortOptions = computed(() => SORTS[tab.value].map(value => ({ value, label: t(`admin.sort.${value}`) })))

// Changing the search, filter or sort starts at the first page again.
const go = query => router.replace({ query: { ...route.query, page: undefined, ...query } })

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({ q: route.query.q || '', sort: sort.value, page: page.value })
    params.set(tab.value === 'cabins' ? 'status' : 'filter', filter.value)
    result.value = await api.get(tab.value === 'log' ? `/admin/log?page=${page.value}` : `/admin/${tab.value}?${params}`)
  } finally {
    loading.value = false
  }
}
watch(() => [route.params.tab, route.query], load, { immediate: true })
watch(tab, () => { result.value = null; search.value = '' })

// Searches while typing, after a short pause.
let timer
watch(search, q => {
  clearTimeout(timer)
  timer = setTimeout(() => { if (q !== (route.query.q || '')) go({ q: q || undefined }) }, 250)
})

// "/" jumps to the search, as on many admin tools.
const slash = e => {
  if (e.key === '/' && !['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName)) {
    e.preventDefault()
    searchInput.value?.$el?.focus()
  }
}
onMounted(() => document.addEventListener('keydown', slash))
onUnmounted(() => document.removeEventListener('keydown', slash))

const shown = computed(() => {
  const r = result.value
  if (!r?.total) return null
  const from = page.value * r.pageSize + 1
  return t('admin.shown', { from, to: from + r.items.length - 1, total: r.total })
})

// How recently someone used the cabin: green within a month, amber within half a year.
function freshness(at) {
  if (!at) return 'bg-zinc-300'
  const days = (Date.now() - new Date(at)) / 86400000
  return days < 30 ? 'bg-emerald-500' : days < 182 ? 'bg-amber-500' : 'bg-zinc-300'
}

// Into the cabin with admin mode on (the server only lets admins in with it); a full load so every request carries it.
function open(cabin) {
  setAdminMode(true)
  location.href = `/h/${cabin.id}`
}

async function restore(cabin) {
  await api.post(`/cabins/${cabin.id}/restore`)
  await load()
}

async function createDemo() {
  busy.value = true
  try {
    const demo = await api.post('/cabins/demo', { lang: locale.value })
    open(demo)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-5xl space-y-5">
    <p v-if="!auth?.admin" class="rounded-lg bg-amber-50 p-4 text-sm text-amber-800">{{ t('admin.notAllowed') }}</p>

    <template v-else>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <h1 class="flex items-center gap-2 text-xl font-semibold"><i class="pi pi-shield text-zinc-500" />{{ t('admin.title') }}</h1>
        <Button v-if="tab === 'cabins'" :label="t('cabins.demo')" icon="pi pi-box" severity="secondary" outlined size="small" :loading="busy" @click="createDemo" />
      </div>

      <!-- Cabins | Users -->
      <nav class="flex gap-1 border-b border-zinc-200" :aria-label="t('admin.title')">
        <RouterLink v-for="x in TABS" :key="x.key" :to="x.path"
                    class="-mb-px flex min-h-11 items-center gap-2 border-b-2 px-4 text-sm font-medium transition"
                    :class="tab === x.key ? 'border-zinc-950 text-zinc-950' : 'border-transparent text-zinc-500 hover:text-zinc-900'">
          <i class="pi text-xs" :class="x.icon" />{{ t(`admin.${x.key}`) }}
        </RouterLink>
      </nav>

      <div v-if="tab !== 'log'" class="flex flex-wrap items-center gap-2">
        <IconField class="min-w-0 flex-1 basis-60">
          <InputIcon class="pi pi-search" />
          <InputText ref="searchInput" v-model="search" type="search" :placeholder="t(`admin.search.${tab}`)" :aria-label="t(`admin.search.${tab}`)" class="w-full" />
        </IconField>
        <Select :model-value="sort" :options="sortOptions" option-label="label" option-value="value" :aria-label="t('admin.sortBy')" class="w-48"
                @update:model-value="go({ sort: $event })" />
      </div>

      <!-- Filter chips that also count: the numbers follow the search -->
      <div v-if="tab !== 'log'" class="flex flex-wrap gap-2" role="group" :aria-label="t('admin.filter')">
        <button v-for="f in FILTERS[tab]" :key="f.value" class="flex min-h-9 items-center gap-2 rounded-full border px-3 text-sm transition"
                :class="filter === f.value ? 'border-zinc-950 bg-zinc-950 text-surface' : 'border-zinc-200 hover:border-zinc-400'"
                :aria-pressed="filter === f.value" @click="go({ filter: f.value === 'ALL' ? undefined : f.value })">
          <span class="size-2 rounded-full" :class="f.dot" />{{ t(`admin.filters.${f.value}`) }}
          <span class="tabular-nums" :class="filter === f.value ? 'text-surface/70' : 'text-zinc-500'">{{ result?.counts?.[f.value] ?? 0 }}</span>
        </button>
      </div>

      <div class="card overflow-hidden" :class="loading && 'opacity-60 transition-opacity'" :aria-busy="loading">
        <p v-if="result && !result.items.length" class="p-8 text-center text-sm text-zinc-500">{{ t('admin.nothing') }}</p>

        <div v-else-if="tab === 'log'" class="px-5"><AuditList :events="result?.items || []" with-cabin /></div>

        <!-- Cabins -->
        <ul v-else-if="tab === 'cabins'" class="divide-y divide-zinc-100">
          <li v-for="c in result?.items" :key="c.id" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:flex-nowrap">
            <img v-if="c.heroPhotoId" :src="`/api/photos/${c.heroPhotoId}/file?hytte=${c.id}`" alt="" class="size-11 shrink-0 rounded-lg object-cover" :class="c.status === 'DELETED' && 'grayscale'">
            <span v-else class="grid size-11 shrink-0 place-items-center rounded-lg bg-zinc-100 text-zinc-500"><i class="pi pi-home" /></span>
            <span class="min-w-0 flex-1 basis-48">
              <span class="flex items-center gap-2">
                <span class="truncate font-semibold">{{ c.name }}</span>
                <span v-if="c.status !== 'ACTIVE'" class="shrink-0 rounded-full px-2 py-0.5 text-xs"
                      :class="{ DELETED: 'bg-rose-50 text-rose-700', EMPTY: 'bg-sky-50 text-sky-700', INACTIVE: 'bg-amber-50 text-amber-800' }[c.status]">{{ t(`admin.filters.${c.status}`) }}</span>
                <!-- AI chat: only the share of the monthly budget -->
                <span v-if="c.chatEnabled" class="shrink-0 rounded-full px-2 py-0.5 text-xs tabular-nums" :title="t('admin.chatUsed')"
                      :class="c.chatPercent >= 100 ? 'bg-rose-50 text-rose-700' : c.chatPercent >= 80 ? 'bg-amber-50 text-amber-800' : 'bg-violet-50 text-violet-700'">AI {{ c.chatPercent }} %</span>
              </span>
              <span class="block truncate text-xs text-zinc-500">{{ c.owners || t('admin.noOwner') }}</span>
            </span>
            <span class="flex shrink-0 items-center gap-1.5 text-sm text-zinc-600 sm:w-24" :title="t('admin.members')"><i class="pi pi-users text-xs text-zinc-400" />{{ c.members }}</span>
            <span class="flex shrink-0 items-center gap-2 text-xs text-zinc-500 sm:w-40" :title="t('admin.lastActive')">
              <span class="size-2 shrink-0 rounded-full" :class="freshness(c.lastActive)" />{{ c.lastActive ? timeAgo(c.lastActive) : t('admin.never') }}
            </span>
            <span class="hidden w-28 shrink-0 text-xs text-zinc-500 md:block" :title="t('admin.created')">{{ formatDate(c.createdAt) }}</span>
            <span class="ml-auto flex shrink-0 justify-end sm:w-36">
              <Button v-if="c.status === 'DELETED'" :label="t('access.restore')" icon="pi pi-replay" size="small" outlined @click="restore(c)" />
              <Button v-else :label="t('admin.open')" icon="pi pi-arrow-right" icon-pos="right" size="small" text @click="open(c)" />
            </span>
          </li>
        </ul>

        <!-- Users -->
        <ul v-else class="divide-y divide-zinc-100">
          <li v-for="u in result?.items" :key="u.id">
            <RouterLink :to="`/admin/brukere/${u.id}`" class="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 transition hover:bg-zinc-50 sm:flex-nowrap">
              <Avatar :image="u.picture || undefined" :label="u.picture ? undefined : initials(u.name)" shape="circle" :class="[colorFor(u.name).bg, colorFor(u.name).text]" />
              <span class="min-w-0 flex-1 basis-48">
                <span class="flex items-center gap-2">
                  <span class="truncate font-semibold">{{ u.name }}</span>
                  <span v-if="u.admin" class="shrink-0 rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600">{{ t('access.admin') }}</span>
                </span>
                <span class="block truncate text-xs text-zinc-500">{{ u.email }}</span>
              </span>
              <span class="flex shrink-0 gap-1.5 sm:w-28">
                <span v-if="u.google" class="rounded-full bg-sky-50 px-2 py-0.5 text-xs text-sky-700">Google</span>
                <span v-if="u.password" class="rounded-full bg-violet-50 px-2 py-0.5 text-xs text-violet-700">{{ t('admin.passwordBadge') }}</span>
              </span>
              <span class="flex shrink-0 items-center gap-1.5 text-sm text-zinc-600 sm:w-20" :title="t('admin.cabins')"><i class="pi pi-home text-xs text-zinc-400" />{{ u.cabins }}</span>
              <span class="flex shrink-0 items-center gap-2 text-xs text-zinc-500 sm:w-40" :title="t('admin.lastLogin')">
                <span class="size-2 shrink-0 rounded-full" :class="freshness(u.lastLoginAt)" />{{ timeAgo(u.lastLoginAt) }}
              </span>
              <i class="pi pi-chevron-right hidden text-xs text-zinc-400 sm:block" />
            </RouterLink>
          </li>
        </ul>
      </div>

      <div v-if="result?.total > result?.pageSize || shown" class="flex flex-wrap items-center justify-between gap-2">
        <span class="text-sm text-zinc-500">{{ shown }}</span>
        <Paginator v-if="result.total > result.pageSize" :rows="result.pageSize" :total-records="result.total" :first="page * result.pageSize"
                   template="PrevPageLink PageLinks NextPageLink" @page="go({ page: $event.page || undefined })" />
      </div>
    </template>
  </div>
</template>
