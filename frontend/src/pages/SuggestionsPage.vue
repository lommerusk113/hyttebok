<script setup>
import { computed, onMounted, ref } from 'vue'
import { useDeepLink } from '../deepLink'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import SelectButton from 'primevue/selectbutton'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { api } from '../api'
import { askName, userName } from '../user'
import { timeAgo } from '../utils'
import { t } from '../i18n'

const CATEGORIES = {
  DO: { label: 'suggestions.do', icon: 'pi pi-sun', tint: 'bg-sky-50 text-sky-700' },
  BUY: { label: 'suggestions.buy', icon: 'pi pi-tag', tint: 'bg-amber-50 text-amber-700' },
}
const FILTERS = computed(() => [{ label: t('suggestions.all'), value: 'ALL' }, { label: t('suggestions.do'), value: 'DO' }, { label: t('suggestions.buy'), value: 'BUY' }])

const confirm = useConfirm()
const suggestions = ref([])
const filter = ref('ALL')
const showDone = ref(false)
const formOpen = ref(false)
const form = ref({})

const takeQuery = useDeepLink()

onMounted(async () => {
  suggestions.value = await api.get('/suggestions')
  if (takeQuery().new === 'forslag') openForm()
})

const filtered = computed(() => suggestions.value.filter(s => filter.value === 'ALL' || s.category === filter.value))
const active = computed(() => filtered.value.filter(s => !s.done).sort((a, b) => b.voters.length - a.voters.length))
const done = computed(() => filtered.value.filter(s => s.done))

function requireName() {
  if (!userName.value) askName.value = true
  return !!userName.value
}

function openForm(s) {
  if (!requireName()) return
  form.value = s ? { ...s } : { title: '', description: '', category: filter.value === 'ALL' ? null : filter.value, author: userName.value, voters: [userName.value], done: false }
  formOpen.value = true
}

async function put(s) {
  const saved = await api.put('/suggestions', s)
  suggestions.value = suggestions.value.map(x => (x.id === saved.id ? saved : x))
}

async function save() {
  if (form.value.id) {
    await put(form.value)
  } else {
    suggestions.value = [await api.post('/suggestions', form.value), ...suggestions.value]
  }
  formOpen.value = false
}

function vote(s) {
  if (!requireName()) return
  const voted = s.voters.includes(userName.value)
  put({ ...s, voters: voted ? s.voters.filter(v => v !== userName.value) : [...s.voters, userName.value] })
}

function remove(s) {
  confirm.require({
    header: t('suggestions.deleteTitle'),
    message: s.title,
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/suggestions/${s.id}`)
      suggestions.value = suggestions.value.filter(x => x.id !== s.id)
      formOpen.value = false
    },
  })
}
</script>

<template>
  <DashCard :title="t('tabs.suggestions')" icon="pi pi-lightbulb" tint="bg-amber-50 text-amber-700" :count="active.length">
    <template #action>
      <Button icon="pi pi-plus" :label="t('common.new')" size="small" @click="openForm()" />
    </template>

    <SelectButton v-model="filter" :options="FILTERS" option-label="label" option-value="value" :allow-empty="false" class="mb-3" />

    <div v-if="active.length" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      <article v-for="s in active" :key="s.id" class="flex flex-col rounded-lg border border-zinc-200 p-4">
        <div class="mb-2 flex items-center justify-between">
          <span :class="CATEGORIES[s.category].tint" class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium">
            <i :class="CATEGORIES[s.category].icon" class="text-[10px]" />{{ t(CATEGORIES[s.category].label) }}
          </span>
          <Button icon="pi pi-ellipsis-h" text rounded severity="secondary" size="small" class="max-sm:!size-10" :aria-label="t('common.edit')" @click="openForm(s)" />
        </div>
        <h3 class="font-semibold">{{ s.title }}</h3>
        <p v-if="s.description" class="mt-1 line-clamp-3 whitespace-pre-line text-sm text-zinc-600">{{ s.description }}</p>
        <div class="mt-auto flex items-center justify-between pt-3">
          <span class="text-xs text-zinc-500">{{ s.author }} · {{ timeAgo(s.createdAt) }}</span>
          <button class="flex min-h-9 items-center gap-1.5 rounded-full border px-3 text-sm font-medium transition"
                  :class="s.voters.includes(userName) ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-zinc-200 text-zinc-500 hover:bg-zinc-100'"
                  :title="s.voters.join(', ')" @click="vote(s)">
            <i class="pi" :class="s.voters.includes(userName) ? 'pi-heart-fill' : 'pi-heart'" />{{ s.voters.length }}
          </button>
        </div>
      </article>
    </div>
    <EmptyState v-else icon="pi pi-lightbulb" :text="t('suggestions.empty')" />

    <div v-if="done.length" class="mt-4">
      <button class="mb-2 flex min-h-9 items-center gap-2 text-sm font-medium text-zinc-600" @click="showDone = !showDone">
        <i class="pi text-xs" :class="showDone ? 'pi-chevron-down' : 'pi-chevron-right'" />
        {{ t('suggestions.completedCount', { n: done.length }) }}
      </button>
      <ul v-if="showDone" class="divide-y divide-zinc-100 rounded-lg border border-zinc-200">
        <li v-for="s in done" :key="s.id" class="flex items-center gap-3 px-4 py-2">
          <i class="pi pi-check-circle text-emerald-600" />
          <span class="flex-1">{{ s.title }}</span>
          <Button :label="t('suggestions.undo')" text size="small" severity="secondary" @click="put({ ...s, done: false })" />
        </li>
      </ul>
    </div>
  </DashCard>

  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('suggestions.suggestion') : t('suggestions.new')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <form id="suggestion-form" class="flex flex-col gap-4" @submit.prevent="save">
      <div class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('suggestions.kind') }}
        <SelectButton v-model="form.category" :options="FILTERS.slice(1)" option-label="label" option-value="value" :allow-empty="false" />
      </div>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('suggestions.suggestion') }}<InputText v-model="form.title" required autofocus :placeholder="t('suggestions.placeholder')" /></label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('suggestions.why') }}<Textarea v-model="form.description" rows="3" auto-resize /></label>
    </form>
    <template #footer>
      <template v-if="form.id">
        <Button :label="t('common.delete')" icon="pi pi-trash" text severity="danger" @click="remove(form)" />
        <Button :label="t('suggestions.completed')" icon="pi pi-check" text class="mr-auto" @click="form.done = true; save()" />
      </template>
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="suggestion-form" :label="t('common.save')" icon="pi pi-check" :disabled="!form.category || !form.title?.trim()" />
    </template>
  </Dialog>
</template>
