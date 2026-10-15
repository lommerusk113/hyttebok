<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Drawer from 'primevue/drawer'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import DoneDialog from '../components/DoneDialog.vue'
import SuggestionList from '../components/SuggestionList.vue'
import { api } from '../api'
import { askName, userName } from '../user'
import { undoToast } from '../actions'
import { formatDate, parseIsoDate, toIsoDate } from '../utils'
import { nextDue, status, UNITS, yearEntries } from '../maintenance'
import { dueLabel, dueText, overdueBadge, STATUS_STYLE } from '../maintenanceLabels'
import { localeTag, t } from '../i18n'
import { useDeepLink } from '../deepLink'

const toast = useToast()
const confirm = useConfirm()
const takeQuery = useDeepLink()
// Two kinds of task in one list: recurring ones (/maintenance, with interval and history) and one-off jobs
// (/todos: done once, then they move to "Fullført", shown in a slide-in). Suggested jobs sit below, waiting for a yes.
const tasks = ref([])
const completions = ref([])
const todos = ref([])
const filter = ref('all')
const FILTERS = ['all', 'recurring', 'once']
const doneOpen = ref(false)
const formOpen = ref(false)
const form = ref({})
const highlighted = ref(null)

// "Today" refreshes when the app comes back to the foreground, so an app left open overnight stays right.
const today = ref(toIsoDate(new Date()))
const thisYear = computed(() => Number(today.value.slice(0, 4)))
const year = ref(thisYear.value)
const refreshToday = () => { today.value = toIsoDate(new Date()) }
onMounted(() => document.addEventListener('visibilitychange', refreshToday))
onUnmounted(() => document.removeEventListener('visibilitychange', refreshToday))

onMounted(async () => {
  [tasks.value, completions.value, todos.value] = await Promise.all(['/maintenance', '/maintenance-completions', '/todos'].map(api.get))
  people.value = await api.get('/cabin/people').catch(() => [])
  const query = takeQuery()
  if (query.new === 'vedlikehold' || query.new === 'gjoremal') openForm()
  if (query.id) showTask(`m-${query.id}`)
  if (query.todo) showTask(`t-${query.todo}`)
})

// Most urgent first: overdue recurring tasks, then one-off jobs, then recurring ones by due date.
// Sorted once per visit (and when tasks are added); completing a task keeps it in place, outlined,
// instead of making it jump while you look at it.
const openTodos = computed(() => todos.value.filter(x => !x.done))
const doneTodos = computed(() => todos.value.filter(x => x.done).sort((a, b) => (b.doneAt || '').localeCompare(a.doneAt || '')))

// "Fullført": everything done in a year, recurring tasks (each time they were done) and one-off jobs, newest
// first. Opens on this year; ‹ › goes back and forth. Recurring tasks also stay in the list with their next date.
const doneYear = ref(Number(toIsoDate(new Date()).slice(0, 4)))
const doneIn = year => {
  const y = String(year)
  const byId = new Map(tasks.value.map(m => [m.id, m]))
  return [
    ...completions.value.filter(c => c.completedOn.startsWith(y) && byId.has(c.maintenanceId))
      .map(c => ({ key: `c${c.id}`, date: c.completedOn, name: byId.get(c.maintenanceId).name, by: c.completedBy, with: c.participants, note: c.note,
        task: byId.get(c.maintenanceId), completion: c })),
    ...doneTodos.value.filter(x => (x.doneAt || '').startsWith(y))
      .map(x => ({ key: `t${x.id}`, date: x.doneAt.slice(0, 10), name: x.title, by: x.doneBy, with: x.doneWith, note: x.doneNote, todo: x })),
  ].sort((a, b) => b.date.localeCompare(a.date))
}
const doneInYear = computed(() => doneIn(doneYear.value))
const doneThisYear = computed(() => doneIn(thisYear.value).length)
const anythingDone = computed(() => completions.value.length > 0 || doneTodos.value.length > 0)
const allRows = computed(() => [
  ...tasks.value.map(m => ({ key: `m-${m.id}`, m, kind: status(m, today.value).kind, label: dueLabel(m, today.value) })),
  ...openTodos.value.map(x => ({ key: `t-${x.id}`, x, kind: 'once' })),
])
const RANK = { overdue: 0, once: 1, soon: 2, later: 3 }
const sortKey = r => (r.m ? nextDue(r.m) : r.x.createdAt || '')
const order = ref([])
watch(() => allRows.value.map(r => r.key).join(), () => {
  const known = new Set(order.value)
  order.value = allRows.value.every(r => known.has(r.key))
    ? order.value.filter(k => allRows.value.some(r => r.key === k)) // something left: keep everyone else in place
    : [...allRows.value].sort((a, b) => RANK[a.kind] - RANK[b.kind] || sortKey(a).localeCompare(sortKey(b))).map(r => r.key)
})
const rows = computed(() => order.value.map(k => allRows.value.find(r => r.key === k)).filter(Boolean))
const shownRows = computed(() => ({ all: rows.value, recurring: rows.value.filter(r => r.m), once: rows.value.filter(r => !r.m) })[filter.value])
const ideas = ref()
const overdueCount = computed(() => rows.value.filter(r => r.kind === 'overdue').length)
const ONCE_STYLE = { bar: 'bg-sky-400', text: 'text-sky-700' }

const intervalText = m => t(`maintenance.every.${m.intervalUnit}`, m.intervalValue)
const lastText = m => (m.lastCompleted ? t('maintenance.lastDone', { date: dueText(m, m.lastCompleted) }) : t('maintenance.neverDone'))

// Year overview: overdue items get their own group on top (they can be from last year), then month by month.
const yearGroups = computed(() => {
  const entries = yearEntries(tasks.value, completions.value, year.value, today.value)
  const groups = new Map()
  const overdue = entries.filter(e => e.kind === 'overdue')
  if (overdue.length) groups.set('overdue', { label: t('maintenance.overdueGroup'), entries: overdue })
  for (const e of entries.filter(e => e.kind !== 'overdue')) {
    const key = e.date.slice(0, 7)
    if (!groups.has(key)) groups.set(key, { label: parseIsoDate(e.date).toLocaleDateString(localeTag(), { month: 'long' }), entries: [] })
    groups.get(key).entries.push(e)
  }
  return [...groups]
})
const dayLabel = iso => parseIsoDate(iso).toLocaleDateString(localeTag(), { day: 'numeric', month: 'short' })
const monthLabel = iso => parseIsoDate(iso).toLocaleDateString(localeTag(), { month: 'short' })

const ENTRY_STYLE = {
  done: { icon: 'pi pi-check-circle', color: 'text-emerald-600' },
  overdue: { icon: 'pi pi-exclamation-circle', color: 'text-rose-600' },
  planned: { icon: 'pi pi-circle', color: 'text-zinc-500' },
}

// Scroll to a task ("m-3" recurring, "t-5" one-off) and outline it briefly, e.g. when coming from the dashboard.
async function showTask(key) {
  highlighted.value = key
  await nextTick()
  document.getElementById(`task-${key}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  setTimeout(() => { if (highlighted.value === key) highlighted.value = null }, 2500)
}

// ---- actions ----

// "Utført" first asks who was there and what was done (DoneDialog); `doing` is the task being completed.
const doing = ref(null) // { m } recurring, or { x } one-off
const people = ref([])

function complete(m) {
  if (!userName.value) {
    askName.value = true
    return
  }
  doing.value = { m }
}

async function saveDone({ participants, note }) {
  const { m, x } = doing.value
  doing.value = null
  if (m) await recordCompletion(m, participants, note)
  else await finishOnce(x, participants, note)
}

async function recordCompletion(m, participants, note) {

  const completedOn = toIsoDate(new Date())
  const saved = await api.post('/maintenance-completions', { maintenanceId: m.id, completedOn, completedBy: userName.value, participants, note })
  completions.value = [saved, ...completions.value]
  tasks.value = tasks.value.map(x => (x.id === m.id ? { ...x, lastCompleted: completedOn } : x))
  showTask(`m-${m.id}`)
  toast.add({ group: 'action', summary: t('maintenance.markedDone', { name: m.name }), actionLabel: t('maintenance.undo'), action: () => undo(saved), life: 6000 })
}

async function undo(completion) {

  await api.del(`/maintenance-completions/${completion.id}`)
  completions.value = completions.value.filter(c => c.id !== completion.id)
  const latest = completions.value.filter(c => c.maintenanceId === completion.maintenanceId).map(c => c.completedOn).sort().at(-1) ?? null
  tasks.value = tasks.value.map(x => (x.id === completion.maintenanceId ? { ...x, lastCompleted: latest } : x))
}

// One-off job done (or undone). Done ones move to "Fullført"; the toast can bring it straight back.
// One-off job: done asks who and what first (like recurring ones); undone goes straight back to the list.
async function toggleOnce(todo) {

  if (!userName.value) {
    askName.value = true
    return
  }
  if (!todo.done) {
    doing.value = { x: todo }
    return
  }
  const updated = await api.put('/todos', { ...todo, done: false, doneBy: null, doneAt: null, doneWith: null, doneNote: null })
  todos.value = todos.value.map(x => (x.id === updated.id ? updated : x))
}

async function finishOnce(todo, participants, note) {
  const updated = await api.put('/todos', { ...todo, done: true, doneBy: userName.value, doneAt: new Date().toISOString(), doneWith: participants, doneNote: note })
  todos.value = todos.value.map(x => (x.id === updated.id ? updated : x))
  undoToast(toast, t('todo.doneToast', { name: updated.title }), () => toggleOnce(updated))
}

// "Kari og Ola", in the app's language
const names = list => new Intl.ListFormat(localeTag(), { type: 'conjunction' }).format(list)

// A suggested job someone said yes to becomes a one-off task.
async function acceptSuggestion(s) {
  const saved = await api.post('/todos', { title: s.title, description: s.description, done: false, createdBy: s.author })
  todos.value = [saved, ...todos.value]
  showTask(`t-${saved.id}`)
}

// `item` is a recurring task or a one-off (todo); a new task starts as one-off, "Gjentas jevnlig" makes it recurring.
function openForm(item) {
  const recurringDefaults = { intervalValue: 1, intervalUnit: 'YEARS', lastYear: '', lastMon: '' }
  if (!item) {
    form.value = { recurring: false, name: '', description: '', ...recurringDefaults }
  } else if (item.intervalUnit) {
    form.value = { ...item, recurring: true, lastYear: item.lastCompleted?.slice(0, 4) ?? '', lastMon: item.lastCompleted?.slice(5, 7) ?? '' }
  } else {
    form.value = { recurring: false, todo: item, id: item.id, name: item.title, description: item.description }
  }
  formOpen.value = true
}

// "Sist utført": a month and a year (ten years back, plus the task's own year if older). No future months.
const YEARS_BACK = 10
const yearChoices = computed(() => {
  const list = [...Array(YEARS_BACK + 1)].map((_, i) => String(thisYear.value - i))
  const current = form.value.lastYear
  return current && !list.includes(current) ? [...list, current] : list
})
const monthChoices = computed(() => [...Array(12)].map((_, i) => ({
  value: String(i + 1).padStart(2, '0'),
  label: new Date(2000, i, 1).toLocaleDateString(localeTag(), { month: 'long' }),
  future: `${form.value.lastYear}-${String(i + 1).padStart(2, '0')}` > today.value.slice(0, 7),
})))
watch(() => form.value.lastYear, () => {
  if (monthChoices.value.find(c => c.value === form.value.lastMon)?.future) form.value.lastMon = ''
})

// Unchanged month on an existing task keeps its exact date; a newly picked month counts from the 1st.
const lastCompletedFromForm = () => {
  if (!form.value.lastYear || !form.value.lastMon) return null
  const month = `${form.value.lastYear}-${form.value.lastMon}`
  return form.value.lastCompleted?.startsWith(month) ? form.value.lastCompleted : `${month}-01`
}

const unitOptions = computed(() => UNITS.map(u => ({ value: u, label: t(`maintenance.units.${u}`, form.value.intervalValue || 1) })))

// Live answer to "so when is it next?" while filling in the form.
const preview = computed(() => {
  if (!form.value.intervalValue) return null
  const base = lastCompletedFromForm() || (form.value.createdAt ? toIsoDate(new Date(form.value.createdAt)) : today.value)
  const task = { intervalValue: form.value.intervalValue, intervalUnit: form.value.intervalUnit, lastCompleted: base }
  const date = dueText(task, nextDue(task))
  return status(task, today.value).kind === 'overdue'
    ? { text: t('maintenance.nextPreviewPast', { date }), past: true }
    : { text: t('maintenance.nextPreview', { date }), past: false }
})

async function save() {

  if (!form.value.recurring) {
    const { todo, name, description } = form.value
    const body = todo
      ? { ...todo, title: name.trim(), description }
      : { title: name.trim(), description, done: false, createdBy: userName.value }
    const saved = await (todo ? api.put('/todos', body) : api.post('/todos', body))
    todos.value = todo ? todos.value.map(x => (x.id === saved.id ? saved : x)) : [saved, ...todos.value]
    formOpen.value = false
    return
  }

  const { lastYear, lastMon, recurring, ...fields } = form.value
  const body = { ...fields, name: fields.name.trim(), lastCompleted: lastCompletedFromForm() }
  const saved = await (body.id ? api.put('/maintenance', body) : api.post('/maintenance', body))

  tasks.value = body.id ? tasks.value.map(x => (x.id === saved.id ? saved : x)) : [...tasks.value, saved]
  // A typed date becomes a history entry on the server; reload the history so the year overview shows it.
  if (body.lastCompleted) completions.value = await api.get('/maintenance-completions')
  formOpen.value = false
}

function remove(item) {
  confirm.require({
    header: t('maintenance.deleteTitle'),
    message: t('maintenance.deleteMessage', { name: item.name }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      if (item.recurring) {
        await api.del(`/maintenance/${item.id}`)
        tasks.value = tasks.value.filter(x => x.id !== item.id)
        completions.value = completions.value.filter(c => c.maintenanceId !== item.id)
      } else {
        await api.del(`/todos/${item.id}`)
        todos.value = todos.value.filter(x => x.id !== item.id)
      }
      formOpen.value = false
    },
  })
}
</script>

<template>
  <!-- Tasks and year overview side by side at the same height, each scrolling on its own -->
  <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
    <!-- All tasks, recurring and one-off, most urgent first -->
    <DashCard id="lists-gjoremal" scroll class="scroll-mt-36 lg:col-span-2 lg:h-[40rem]" :title="t('tabs.maintenance')" icon="pi pi-wrench" tint="bg-zinc-100 text-zinc-700"
              :count="overdueCount" count-class="bg-rose-50 text-rose-700" :count-title="overdueBadge(overdueCount)">
      <template #action>
        <Button icon="pi pi-plus" :label="t('common.new')" size="small" @click="openForm()" />
      </template>

      <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
        <SelectButton v-model="filter" :options="FILTERS" :option-label="f => t(`maintenance.filter.${f}`)" :allow-empty="false" />
        <Button v-if="anythingDone" :label="t('maintenance.seeCompleted', { n: doneThisYear })" text size="small" severity="secondary"
                class="!min-h-11" @click="doneYear = thisYear; doneOpen = true" />
      </div>

      <ul v-if="shownRows.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
        <li v-for="r in shownRows" :id="`task-${r.key}`" :key="r.key" class="flex scroll-mt-40 items-center gap-3 py-2.5 pl-3 pr-4 transition-colors duration-700"
            :class="highlighted === r.key && 'bg-amber-50'">
          <span :class="(r.m ? STATUS_STYLE[r.kind] : ONCE_STYLE).bar" class="w-1 self-stretch rounded-full" />
          <!-- The whole name area opens the task; the chevron says so -->
          <button class="group flex min-h-11 min-w-0 flex-1 items-center gap-2 text-left" @click="openForm(r.m || r.x)">
            <span v-if="r.m" class="min-w-0 flex-1">
              <span class="block font-medium group-hover:underline">{{ r.m.name }}</span>
              <span v-if="r.kind !== 'later'" class="block text-sm font-medium" :class="STATUS_STYLE[r.kind].text">{{ r.label }}</span>
              <span class="block text-xs text-zinc-500">
                <template v-if="r.kind === 'later'">{{ r.label }} · </template>{{ intervalText(r.m) }} · {{ lastText(r.m) }}
              </span>
            </span>
            <span v-else class="min-w-0 flex-1">
              <span class="block font-medium group-hover:underline">{{ r.x.title }}</span>
              <span v-if="r.x.description" class="block line-clamp-1 text-sm text-zinc-500">{{ r.x.description }}</span>
              <span class="block text-xs text-zinc-500">{{ t('maintenance.once') }}<template v-if="r.x.createdBy"> · {{ t('todo.addedBy', { name: r.x.createdBy }) }}</template></span>
            </span>
            <i class="pi pi-chevron-right shrink-0 text-xs text-zinc-400" />
          </button>
          <!-- Always outlined: testers read a borderless "Utført" as plain text -->
          <Button :label="t('maintenance.done')" icon="pi pi-check" size="small" severity="secondary" class="max-sm:!min-h-11 shrink-0"
                  outlined @click="r.m ? complete(r.m) : toggleOnce(r.x)" />
        </li>
      </ul>
      <EmptyState v-else-if="!rows.length" icon="pi pi-wrench" :text="t('maintenance.empty')">
        <Button :label="t('maintenance.new')" icon="pi pi-plus" size="small" @click="openForm()" />
      </EmptyState>
      <p v-else class="py-2 text-sm text-zinc-500">{{ t('maintenance.noneInFilter') }}</p>
    </DashCard>

    <!-- Right column: year overview above ideas, together exactly as tall as the task list; each scrolls on its own -->
    <div class="flex flex-col gap-6 lg:h-[40rem]">
    <DashCard scroll class="lg:min-h-0 lg:flex-[3]" :title="t('maintenance.year')" icon="pi pi-calendar" tint="bg-zinc-100 text-zinc-700">
      <template #action>
        <div class="flex items-center">
          <Button icon="pi pi-chevron-left" text rounded severity="secondary" class="!size-11" :aria-label="t('maintenance.prevYear')" @click="year--" />
          <span class="w-11 text-center text-sm font-medium tabular-nums text-zinc-600">{{ year }}</span>
          <Button icon="pi pi-chevron-right" text rounded severity="secondary" class="!size-11" :aria-label="t('maintenance.nextYear')" @click="year++" />
        </div>
      </template>

      <div class="-mx-5 border-t border-zinc-100 px-5 pt-3">
        <button v-if="year !== thisYear" class="mb-2 inline-flex min-h-11 items-center text-sm font-medium underline underline-offset-4" @click="year = thisYear">
          {{ t('maintenance.backToThisYear') }}
        </button>

        <div v-if="yearGroups.length" class="space-y-3">
          <section v-for="[key, group] in yearGroups" :key="key">
            <h3 class="mb-0.5 text-xs font-medium uppercase tracking-wide" :class="key === 'overdue' ? 'text-rose-700' : 'text-zinc-500'">{{ group.label }}</h3>
            <ul>
              <li v-for="e in group.entries" :key="e.key" class="flex min-h-9 items-center gap-2.5">
                <i :class="[ENTRY_STYLE[e.kind].icon, ENTRY_STYLE[e.kind].color]" class="text-sm" />
                <span class="w-16 shrink-0 whitespace-nowrap text-sm tabular-nums text-zinc-500">{{ e.monthOnly ? monthLabel(e.date) : dayLabel(e.date) }}</span>
                <span class="min-w-0 flex-1 truncate text-sm" :class="{ 'font-medium text-rose-700': e.kind === 'overdue', 'text-zinc-500': e.kind === 'done' }">
                  {{ e.task.name }}<template v-if="e.kind === 'done'"> · {{ e.completion.completedBy ? t('maintenance.doneBy', { name: e.completion.completedBy }) : t('maintenance.kind.done').toLowerCase() }}</template>
                </span>
                <Button v-if="e.kind === 'done'" :label="t('maintenance.undo')" text size="small" severity="secondary" class="!min-h-11 !px-2" @click="undo(e.completion)" />
              </li>
            </ul>
          </section>
        </div>
        <p v-else class="py-2 text-sm text-zinc-500">{{ t('maintenance.yearEmpty', { year }) }}</p>

        <div class="mt-3 flex flex-wrap gap-x-4 gap-y-1 border-t border-zinc-100 pt-3 text-xs text-zinc-500">
          <span v-for="(style, kind) in ENTRY_STYLE" :key="kind" class="flex items-center gap-1.5"><i :class="[style.icon, style.color]" />{{ t(`maintenance.kind.${kind}`) }}</span>
        </div>
      </div>
    </DashCard>

    <!-- Ideas for things to do at the cabin; "Godta" turns one into a task -->
    <DashCard id="lists-forslag" scroll class="scroll-mt-36 lg:min-h-0 lg:flex-[2]" :title="t('suggestions.title')" icon="pi pi-lightbulb" tint="bg-amber-50 text-amber-700">
      <template #action>
        <Button :label="t('suggestions.propose')" icon="pi pi-lightbulb" size="small" outlined severity="secondary" @click="ideas.openForm()" />
      </template>
      <SuggestionList ref="ideas" category="DO" :titled="false" @accept="acceptSuggestion" />
    </DashCard>
    </div>
  </div>

  <!-- Finished one-off jobs (recurring tasks keep their history in the year overview) -->
  <DoneDialog :task="doing ? (doing.m?.name || doing.x?.title) : null" :people="people" @save="saveDone" @cancel="doing = null" />

  <Drawer v-model:visible="doneOpen" position="right" :header="t('maintenance.completed')" class="!w-full sm:!w-[26rem]">
    <div class="-mt-2 mb-3 flex items-center justify-between">
      <Button icon="pi pi-chevron-left" text rounded severity="secondary" class="!size-11" :aria-label="t('maintenance.prevYear')" @click="doneYear--" />
      <span class="font-semibold tabular-nums">{{ doneYear }}</span>
      <Button icon="pi pi-chevron-right" text rounded severity="secondary" class="!size-11" :aria-label="t('maintenance.nextYear')" :disabled="doneYear >= thisYear" @click="doneYear++" />
    </div>
    <button v-if="doneYear !== thisYear" class="mb-2 inline-flex min-h-11 items-center text-sm font-medium underline underline-offset-4" @click="doneYear = thisYear">
      {{ t('maintenance.backToThisYear') }}
    </button>

    <ul v-if="doneInYear.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
      <li v-for="d in doneInYear" :key="d.key" class="flex items-center gap-3 py-2.5 pl-5 pr-4">
        <i :class="d.task ? 'pi pi-refresh text-zinc-500' : 'pi pi-check text-emerald-600'" class="shrink-0 text-xs" :title="d.task ? t('maintenance.kindRecurring') : t('maintenance.once')" />
        <button class="min-w-0 flex-1 text-left" @click="openForm(d.task || d.todo)">
          <span class="block truncate font-medium">{{ d.name }}</span>
          <span class="block text-xs text-zinc-500">
            {{ d.task ? t('maintenance.kindRecurring') : t('maintenance.once') }} ·
            {{ d.with?.length || d.by ? t('todo.doneBy', { name: d.with?.length ? names(d.with) : d.by, date: formatDate(d.date) }) : t('maintenance.doneOn', { date: formatDate(d.date) }) }}
          </span>
          <span v-if="d.note" class="mt-0.5 block whitespace-pre-line text-sm text-zinc-600">{{ d.note }}</span>
        </button>
        <Button :label="t('maintenance.undo')" text size="small" severity="secondary" class="!min-h-11 shrink-0"
                @click="d.completion ? undo(d.completion) : toggleOnce(d.todo)" />
      </li>
    </ul>
    <EmptyState v-else icon="pi pi-inbox" :text="t('maintenance.noneInYear', { year: doneYear })" />
  </Drawer>

  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('maintenance.edit') : t('maintenance.new')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <form id="maintenance-form" class="flex flex-col gap-4" @submit.prevent="save">
      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('maintenance.name') }}
        <InputText v-model="form.name" required autofocus :placeholder="t('maintenance.namePlaceholder')" />
      </label>

      <!-- One-off by default; recurring adds how often and when it was last done. The kind is fixed once saved. -->
      <label v-if="!form.id" class="flex cursor-pointer items-center justify-between gap-3 rounded-lg border border-zinc-200 px-3 py-2.5">
        <span class="text-sm">
          <span class="block font-medium">{{ t('maintenance.repeats') }}</span>
          <span class="block text-xs text-zinc-500">{{ t('maintenance.repeatsHint') }}</span>
        </span>
        <ToggleSwitch v-model="form.recurring" />
      </label>

      <template v-if="form.recurring">
      <div class="flex flex-col gap-1.5 text-sm font-medium" role="group" aria-labelledby="interval-label">
        <span id="interval-label">{{ t('maintenance.interval') }}</span>
        <div class="flex gap-2">
          <InputNumber v-model="form.intervalValue" :min="1" :max="999" input-class="!w-20" :aria-label="t('maintenance.intervalCount')" required />
          <Select v-model="form.intervalUnit" :options="unitOptions" option-label="label" option-value="value" class="flex-1" :aria-label="t('maintenance.intervalUnit')" />
        </div>
        <span v-if="preview" class="text-xs font-medium" :class="preview.past ? 'text-rose-700' : 'text-zinc-500'">{{ preview.text }}</span>
      </div>

      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('maintenance.lastCompleted') }}
        <!-- Month is enough (people remember "June", not the 15th). Native selects open the phone's own picker. -->
        <span class="flex gap-2">
          <select v-model="form.lastMon" :required="!!form.lastYear" :aria-label="t('maintenance.lastMonth')"
                  class="min-h-11 flex-1 rounded-md border border-zinc-300 bg-surface px-3 text-base font-normal text-zinc-900 sm:text-sm">
            <option value="">{{ form.lastYear ? t('maintenance.pickMonth') : t('maintenance.lastCompletedPlaceholder') }}</option>
            <option v-for="c in monthChoices" :key="c.value" :value="c.value" :disabled="c.future">{{ c.label }}</option>
          </select>
          <select v-model="form.lastYear" :required="!!form.lastMon" :aria-label="t('maintenance.lastYear')"
                  class="min-h-11 w-28 rounded-md border border-zinc-300 bg-surface px-3 text-base font-normal text-zinc-900 sm:text-sm">
            <option value="">{{ t('maintenance.pickYear') }}</option>
            <option v-for="y in yearChoices" :key="y" :value="y">{{ y }}</option>
          </select>
        </span>
        <span v-if="!lastCompletedFromForm()" class="text-xs font-normal text-zinc-500">{{ form.id ? t('maintenance.lastCompletedHintEdit') : t('maintenance.lastCompletedHintNew') }}</span>
      </label>
      </template>

      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('maintenance.description') }}
        <Textarea v-model="form.description" rows="3" auto-resize :placeholder="t('maintenance.descriptionPlaceholder')" />
      </label>
      <p v-if="form.todo?.done" class="text-sm text-emerald-600"><i class="pi pi-check-circle mr-1" />{{ t('todo.doneBy', { name: form.todo.doneBy, date: formatDate(form.todo.doneAt) }) }}</p>
    </form>
    <template #footer>
      <Button v-if="form.id" :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove(form)" />
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="maintenance-form" :label="t('common.save')" icon="pi pi-check" :disabled="!form.name?.trim() || (form.recurring && !form.intervalValue)" />
    </template>
  </Dialog>
</template>
