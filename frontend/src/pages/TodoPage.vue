<script setup>
import { computed, onMounted, ref } from 'vue'
import { useDeepLink } from '../deepLink'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import SelectButton from 'primevue/selectbutton'
import Avatar from 'primevue/avatar'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { api } from '../api'
import { userName } from '../user'
import { toggleTodo } from '../actions'
import { colorFor, formatDate, initials } from '../utils'
import { t } from '../i18n'

const confirm = useConfirm()
const todos = ref([])
const filter = ref('open')
const formOpen = ref(false)
const form = ref({})

const takeQuery = useDeepLink()

onMounted(async () => {
  todos.value = await api.get('/todos')
  if (takeQuery().new === 'gjoremal') openForm()
})

const open = computed(() => todos.value.filter(x => !x.done))
const done = computed(() => todos.value.filter(x => x.done).sort((a, b) => b.doneAt.localeCompare(a.doneAt)))
const shown = computed(() => (filter.value === 'open' ? open.value : done.value))

function openForm(todo) {
  form.value = todo ? { ...todo } : { title: '', description: '', done: false, createdBy: userName.value }
  formOpen.value = true
}

async function save() {
  const saved = await (form.value.id ? api.put('/todos', form.value) : api.post('/todos', form.value))
  todos.value = form.value.id ? todos.value.map(x => (x.id === saved.id ? saved : x)) : [saved, ...todos.value]
  formOpen.value = false
}

async function toggle(todo) {
  const updated = await toggleTodo(todo)
  if (updated) todos.value = todos.value.map(x => (x.id === updated.id ? updated : x))
}

function remove(todo) {
  confirm.require({
    header: t('todo.deleteTitle'),
    message: todo.title,
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/todos/${todo.id}`)
      todos.value = todos.value.filter(x => x.id !== todo.id)
      formOpen.value = false
    },
  })
}
</script>

<template>
  <DashCard :title="t('tabs.todos')" icon="pi pi-check-square" tint="bg-amber-50 text-amber-700" :count="open.length">
    <template #action>
      <Button icon="pi pi-plus" :label="t('common.new')" size="small" @click="openForm()" />
    </template>

    <SelectButton v-model="filter" :options="['open', 'done']" :allow-empty="false" class="mb-3">
      <template #option="{ option }">
        {{ t(`todo.${option}`) }}
        <span class="ml-1.5 rounded-full bg-zinc-200/70 px-1.5 text-xs">{{ option === 'open' ? open.length : done.length }}</span>
      </template>
    </SelectButton>

    <ul v-if="shown.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
      <li v-for="todo in shown" :key="todo.id" class="group flex items-start gap-3 px-5 py-3">
        <Checkbox :model-value="todo.done" binary class="mt-0.5" :aria-label="todo.title" @update:model-value="toggle(todo)" />
        <button class="min-w-0 flex-1 text-left" @click="openForm(todo)">
          <p class="font-medium" :class="todo.done && 'text-zinc-500 line-through'">{{ todo.title }}</p>
          <p v-if="todo.description" class="line-clamp-1 text-sm text-zinc-500">{{ todo.description }}</p>
          <p v-if="!todo.done && todo.createdBy" class="mt-0.5 text-xs text-zinc-500">{{ t('todo.addedBy', { name: todo.createdBy }) }}</p>
        </button>
        <div v-if="todo.done" class="flex shrink-0 items-center gap-2 rounded-full bg-emerald-50 py-1 pl-1 pr-3 text-xs text-emerald-700">
          <Avatar :label="initials(todo.doneBy)" shape="circle" size="small" :class="[colorFor(todo.doneBy).bg, colorFor(todo.doneBy).text]" class="!size-6 !text-[10px] !font-semibold" />
          <span><b>{{ todo.doneBy }}</b><span class="hidden sm:inline"> · {{ formatDate(todo.doneAt) }}</span></span>
        </div>
      </li>
    </ul>
    <EmptyState v-else :icon="filter === 'open' ? 'pi pi-sparkles' : 'pi pi-inbox'"
                :text="filter === 'open' ? t('todo.allDone') : t('todo.noneDone')" />
  </DashCard>

  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('todo.task') : t('todo.new')" class="w-[28rem] max-w-[calc(100vw-2rem)]">
    <form id="todo-form" class="flex flex-col gap-4" @submit.prevent="save">
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('todo.whatToDo') }}<InputText v-model="form.title" required autofocus /></label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('todo.details') }}<Textarea v-model="form.description" rows="3" auto-resize /></label>
      <p v-if="form.done" class="text-sm text-emerald-600"><i class="pi pi-check-circle mr-1" />{{ t('todo.doneBy', { name: form.doneBy, date: formatDate(form.doneAt) }) }}</p>
    </form>
    <template #footer>
      <Button v-if="form.id" :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove(form)" />
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="todo-form" :label="t('common.save')" icon="pi pi-check" />
    </template>
  </Dialog>
</template>
