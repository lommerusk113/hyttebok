<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { api } from '../api'
import { askName, userName } from '../user'
import { formatDate, parseIsoDate, toIsoDate } from '../utils'
import { addInterval, nextDue, status, UNITS, yearEntries } from '../maintenance'
import { dueLabel, overdueBadge, STATUS_STYLE } from '../maintenanceLabels'
import { localeTag, t } from '../i18n'
import { useDeepLink } from '../deepLink'

const toast = useToast()
const confirm = useConfirm()
const takeQuery = useDeepLink()
const tasks = ref([])
const completions = ref([])
const formOpen = ref(false)
const form = ref({})

// "Today" refreshes when the app comes back to the foreground, so an app left open overnight stays right.
const today = ref(toIsoDate(new Date()))
const thisYear = computed(() => Number(today.value.slice(0, 4)))
const year = ref(thisYear.value)
const refreshToday = () => { today.value = toIsoDate(new Date()) }
onMounted(() => document.addEventListener('visibilitychange', refreshToday))
onUnmounted(() => document.removeEventListener('visibilitychange', refreshToday))

onMounted(async () => {
  [tasks.value, completions.value] = await Promise.all([api.get('/maintenance'), api.get('/maintenance-completions')])
  if (takeQuery().new === 'vedlikehold') openForm()
})

// Most urgent first: overdue, then whatever is due soonest.
const rows = computed(() => tasks.value
  .map(m => ({ m, kind: status(m, today.value).kind, label: dueLabel(m, today.value) }))
  .sort((a, b) => nextDue(a.m).localeCompare(nextDue(b.m))))
const overdueCount = computed(() => rows.value.filter(r => r.kind === 'overdue').length)

const intervalText = m => t(`maintenance.every.${m.intervalUnit}`, m.intervalValue)
const lastText = m => (m.lastCompleted ? t('maintenance.lastDone', { date: formatDate(parseIsoDate(m.lastCompleted)) }) : t('maintenance.neverDone'))

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

const ENTRY_STYLE = {
  done: { icon: 'pi pi-check-circle', color: 'text-emerald-600' },
  overdue: { icon: 'pi pi-exclamation-circle', color: 'text-rose-600' },
  planned: { icon: 'pi pi-circle', color: 'text-zinc-500' },
}

// ---- actions ----

async function complete(m) {

  if (!userName.value) {
    askName.value = true
    return
  }

  const completedOn = toIsoDate(new Date())
  const saved = await api.post('/maintenance-completions', { maintenanceId: m.id, completedOn, completedBy: userName.value })
  completions.value = [saved, ...completions.value]
  tasks.value = tasks.value.map(x => (x.id === m.id ? { ...x, lastCompleted: completedOn } : x))
  toast.add({ group: 'action', summary: t('maintenance.markedDone', { name: m.name }), actionLabel: t('maintenance.undo'), action: () => undo(saved), life: 6000 })
}

async function undo(completion) {

  await api.del(`/maintenance-completions/${completion.id}`)
  completions.value = completions.value.filter(c => c.id !== completion.id)
  const latest = completions.value.filter(c => c.maintenanceId === completion.maintenanceId).map(c => c.completedOn).sort().at(-1) ?? null
  tasks.value = tasks.value.map(x => (x.id === completion.maintenanceId ? { ...x, lastCompleted: latest } : x))
}

function openForm(m) {
  form.value = m
    ? { ...m, last: m.lastCompleted ? parseIsoDate(m.lastCompleted) : null }
    : { name: '', description: '', intervalValue: 1, intervalUnit: 'YEARS', last: null }
  formOpen.value = true
}

const unitOptions = computed(() => UNITS.map(u => ({ value: u, label: t(`maintenance.units.${u}`, form.value.intervalValue || 1) })))

// Live answer to "so when is it next?" while filling in the form.
const preview = computed(() => {
  if (!form.value.intervalValue) return null
  const base = form.value.last ? toIsoDate(form.value.last) : form.value.createdAt ? toIsoDate(new Date(form.value.createdAt)) : today.value
  const next = addInterval(base, form.value.intervalValue, form.value.intervalUnit)
  const date = formatDate(parseIsoDate(next))
  return next < today.value ? { text: t('maintenance.nextPreviewPast', { date }), past: true } : { text: t('maintenance.nextPreview', { date }), past: false }
})

async function save() {

  const { last, ...fields } = form.value
  const body = { ...fields, name: fields.name.trim(), lastCompleted: last ? toIsoDate(last) : null }
  const saved = await (body.id ? api.put('/maintenance', body) : api.post('/maintenance', body))

  tasks.value = body.id ? tasks.value.map(x => (x.id === saved.id ? saved : x)) : [...tasks.value, saved]
  // A typed date becomes a history entry on the server; reload the history so the year overview shows it.
  if (body.lastCompleted) completions.value = await api.get('/maintenance-completions')
  formOpen.value = false
}

function remove(m) {
  confirm.require({
    header: t('maintenance.deleteTitle'),
    message: t('maintenance.deleteMessage', { name: m.name }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/maintenance/${m.id}`)
      tasks.value = tasks.value.filter(x => x.id !== m.id)
      completions.value = completions.value.filter(c => c.maintenanceId !== m.id)
      formOpen.value = false
    },
  })
}
</script>

<template>
  <div class="grid grid-cols-1 gap-6 lg:grid-cols-3 lg:items-start">
    <!-- Tasks, most urgent first -->
    <DashCard class="lg:col-span-2" :title="t('maintenance.tasks')" icon="pi pi-wrench" tint="bg-zinc-100 text-zinc-700"
              :count-label="overdueBadge(overdueCount)" count-class="bg-rose-50 text-rose-700">
      <template #action>
        <Button icon="pi pi-plus" :label="t('common.new')" size="small" @click="openForm()" />
      </template>

      <ul v-if="rows.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
        <li v-for="{ m, kind, label } in rows" :key="m.id" class="flex items-center gap-3 py-2.5 pl-3 pr-4">
          <span :class="STATUS_STYLE[kind].bar" class="w-1 self-stretch rounded-full" />
          <!-- The whole name area opens the task; the chevron says so -->
          <button class="group flex min-h-11 min-w-0 flex-1 items-center gap-2 text-left" @click="openForm(m)">
            <span class="min-w-0 flex-1">
              <span class="block font-medium group-hover:underline">{{ m.name }}</span>
              <span v-if="kind !== 'later'" class="block text-sm font-medium" :class="STATUS_STYLE[kind].text">{{ label }}</span>
              <span class="block text-xs text-zinc-500">
                <template v-if="kind === 'later'">{{ label }} · </template>{{ intervalText(m) }} · {{ lastText(m) }}
              </span>
            </span>
            <i class="pi pi-chevron-right shrink-0 text-xs text-zinc-400" />
          </button>
          <!-- Urgent tasks get the visible button; the rest a quieter one -->
          <Button :label="t('maintenance.done')" icon="pi pi-check" size="small" severity="secondary" class="max-sm:!min-h-11 shrink-0"
                  :outlined="kind !== 'later'" :text="kind === 'later'" @click="complete(m)" />
        </li>
      </ul>
      <EmptyState v-else icon="pi pi-wrench" :text="t('maintenance.empty')">
        <Button :label="t('maintenance.new')" icon="pi pi-plus" size="small" @click="openForm()" />
      </EmptyState>
    </DashCard>

    <!-- Year overview -->
    <DashCard :title="t('maintenance.year')" icon="pi pi-calendar" tint="bg-zinc-100 text-zinc-700">
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
                <span class="w-16 shrink-0 whitespace-nowrap text-sm tabular-nums text-zinc-500">{{ dayLabel(e.date) }}</span>
                <span class="min-w-0 flex-1 truncate text-sm" :class="{ 'font-medium text-rose-700': e.kind === 'overdue', 'text-zinc-500': e.kind === 'done' }">
                  {{ e.task.name }}<template v-if="e.kind === 'done' && e.completion.completedBy"> · {{ e.completion.completedBy }}</template>
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
  </div>

  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('maintenance.edit') : t('maintenance.new')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <form id="maintenance-form" class="flex flex-col gap-4" @submit.prevent="save">
      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('maintenance.name') }}
        <InputText v-model="form.name" required autofocus :placeholder="t('maintenance.namePlaceholder')" />
      </label>

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
        <DatePicker v-model="form.last" :max-date="new Date()" show-icon icon-display="input" show-button-bar :manual-input="false" :placeholder="t('maintenance.lastCompletedPlaceholder')" />
        <span v-if="!form.last" class="text-xs font-normal text-zinc-500">{{ form.id ? t('maintenance.lastCompletedHintEdit') : t('maintenance.lastCompletedHintNew') }}</span>
      </label>

      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('maintenance.description') }}
        <Textarea v-model="form.description" rows="3" auto-resize :placeholder="t('maintenance.descriptionPlaceholder')" />
      </label>
    </form>
    <template #footer>
      <Button v-if="form.id" :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove(form)" />
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="maintenance-form" :label="t('common.save')" icon="pi pi-check" :disabled="!form.name?.trim() || !form.intervalValue" />
    </template>
  </Dialog>
</template>
