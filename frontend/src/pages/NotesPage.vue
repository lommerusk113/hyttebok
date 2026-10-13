<script setup>
import { computed, onMounted, ref } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { api } from '../api'
import { timeAgo } from '../utils'
import { t } from '../i18n'

const COLORS = {
  amber: 'bg-amber-50 border-amber-200',
  sky: 'bg-sky-50 border-sky-200',
  emerald: 'bg-emerald-50 border-emerald-200',
  rose: 'bg-rose-50 border-rose-200',
  violet: 'bg-violet-50 border-violet-200',
  stone: 'bg-surface border-zinc-200',
}
const SWATCH = { amber: 'bg-amber-300', sky: 'bg-sky-300', emerald: 'bg-emerald-300', rose: 'bg-rose-300', violet: 'bg-violet-300', stone: 'bg-zinc-100' }

const confirm = useConfirm()
const notes = ref([])
const formOpen = ref(false)
const form = ref({})

onMounted(async () => { notes.value = await api.get('/notes') })

const sorted = computed(() => [...notes.value].sort((a, b) => b.pinned - a.pinned))

function openForm(note) {
  form.value = note ? { ...note } : { title: '', content: '', color: 'amber', pinned: false }
  formOpen.value = true
}

async function save() {
  const saved = await (form.value.id ? api.put('/notes', form.value) : api.post('/notes', form.value))
  notes.value = [saved, ...notes.value.filter(n => n.id !== saved.id)]
  formOpen.value = false
}

function remove(note) {
  confirm.require({
    header: t('notes.deleteTitle'),
    message: note.title,
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/notes/${note.id}`)
      notes.value = notes.value.filter(n => n.id !== note.id)
      formOpen.value = false
    },
  })
}
</script>

<template>
  <DashCard :title="t('tabs.notes')" icon="pi pi-pen-to-square" tint="bg-amber-50 text-amber-700" :count="notes.length">
    <template #action>
      <Button icon="pi pi-plus" :label="t('common.new')" size="small" @click="openForm()" />
    </template>

    <div v-if="notes.length" class="columns-1 gap-3 sm:columns-2 lg:columns-3 xl:columns-4">
      <button v-for="n in sorted" :key="n.id" :class="COLORS[n.color] || COLORS.stone"
              class="mb-3 block w-full break-inside-avoid rounded-lg border p-4 text-left transition hover:-translate-y-0.5 hover:shadow-md"
              @click="openForm(n)">
        <div class="flex items-start justify-between gap-2">
          <h3 class="font-semibold">{{ n.title }}</h3>
          <i v-if="n.pinned" class="pi pi-thumbtack text-sm text-zinc-500" />
        </div>
        <p class="mt-2 line-clamp-[8] whitespace-pre-line text-sm leading-relaxed text-zinc-600">{{ n.content }}</p>
        <p class="mt-3 text-xs text-zinc-500">{{ t('notes.edited', { time: timeAgo(n.updatedAt) }) }}</p>
      </button>
    </div>
    <EmptyState v-else icon="pi pi-pen-to-square" :text="t('notes.empty')" />
  </DashCard>

  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('notes.note') : t('notes.new')" class="w-[34rem] max-w-[calc(100vw-2rem)]">
    <form id="note-form" class="flex flex-col gap-4" @submit.prevent="save">
      <InputText v-model="form.title" required :placeholder="t('notes.titlePlaceholder')" class="!text-lg !font-semibold" />
      <Textarea v-model="form.content" rows="8" auto-resize :placeholder="t('notes.contentPlaceholder')" />
      <div class="flex items-center justify-between">
        <div class="flex gap-2">
          <button v-for="(cls, c) in SWATCH" :key="c" type="button" :aria-label="c"
                  :class="[cls, form.color === c && 'ring-2 ring-zinc-800 ring-offset-2 ring-offset-surface']" class="size-7 rounded-full transition"
                  @click="form.color = c" />
        </div>
        <Button type="button" :icon="'pi pi-thumbtack'" :label="form.pinned ? t('notes.pinned') : t('notes.pin')" size="small"
                :outlined="!form.pinned" severity="secondary" @click="form.pinned = !form.pinned" />
      </div>
    </form>
    <template #footer>
      <Button v-if="form.id" :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove(form)" />
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="note-form" :label="t('common.save')" icon="pi pi-check" />
    </template>
  </Dialog>
</template>
