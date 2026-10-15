<script setup>
import { onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DashCard from './DashCard.vue'
import { api } from '../api'
import { addShoppingItem } from '../actions'
import { t } from '../i18n'

// Things that should always be at the cabin. "Mangler" puts one on the shopping list (once: while it's there
// unbought the row says "På lista"). `shopping` is the shopping list; `added` hands back the new shopping item.
// Collapsed by default (it's a reference list); `startOpen` when coming from "Gå gjennom sjekklista".
const props = defineProps({ shopping: Array, startOpen: Boolean })
const emit = defineEmits(['added'])
const toast = useToast()

const items = ref([])
const expanded = ref(props.startOpen)
const loaded = ref(false)
const adding = ref(false)
const name = ref('')
const nameInput = ref()

const STARTERS = ['Toalettpapir', 'Tørkepapir', 'Såpe', 'Oppvaskmiddel', 'Kaffe', 'Søppelposer', 'Batterier', 'Fyrstikker']

onMounted(async () => {
  items.value = await api.get('/checklist')
  loaded.value = true
})

const sameName = (a, b) => a.trim().toLowerCase() === b.trim().toLowerCase()
const onList = item => props.shopping.some(s => !s.bought && sameName(s.name, item.name))
const sorted = list => [...list].sort((a, b) => a.name.localeCompare(b.name))

async function missing(item) {
  if (onList(item)) return
  emit('added', await addShoppingItem(item.name, ''))
  toast.add({ severity: 'success', summary: t('checklist.addedToShopping', { name: item.name }), life: 2000 })
}

async function add() {
  const value = name.value.trim()
  if (!value) return
  if (!items.value.some(i => sameName(i.name, value))) items.value = sorted([...items.value, await api.post('/checklist', { name: value })])
  name.value = ''
  nameInput.value.$el.focus()
}

async function addStarters() {
  for (const n of STARTERS) items.value.push(await api.post('/checklist', { name: n }))
  items.value = sorted(items.value)
}

async function remove(item) {
  await api.del(`/checklist/${item.id}`)
  items.value = items.value.filter(i => i.id !== item.id)
}
</script>

<template>
  <DashCard id="lists-sjekkliste" class="scroll-mt-36" :title="t('checklist.title')" icon="pi pi-list-check" tint="bg-emerald-50 text-emerald-700"
            :count="items.length">
    <template #action>
      <Button v-if="expanded" icon="pi pi-plus" :label="t('common.new')" size="small" outlined severity="secondary" @click="adding = true" />
      <Button :icon="expanded ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" text rounded severity="secondary" class="!size-11"
              :aria-label="expanded ? t('checklist.collapse') : t('checklist.expand')" :aria-expanded="expanded" aria-controls="checklist-body"
              @click="expanded = !expanded" />
    </template>
    <div v-show="expanded" id="checklist-body">
    <p class="-mt-1 mb-2 text-sm text-zinc-500">{{ t('checklist.hint') }}</p>

    <ul v-if="items.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
      <li v-for="i in items" :key="i.id" class="group flex min-h-12 items-center gap-2 py-1 pl-5 pr-3">
        <span class="min-w-0 flex-1 truncate">{{ i.name }}</span>
        <span v-if="onList(i)" class="flex items-center gap-1 px-2 text-xs font-medium text-emerald-700"><i class="pi pi-check text-[10px]" />{{ t('checklist.onList') }}</span>
        <Button v-else :label="t('checklist.missing')" icon="pi pi-cart-plus" size="small" outlined severity="secondary" class="!min-h-10 shrink-0" @click="missing(i)" />
        <Button icon="pi pi-times" text rounded severity="secondary" size="small" :aria-label="t('checklist.remove', { name: i.name })"
                class="max-sm:!size-10 sm:opacity-0 sm:group-hover:opacity-100 sm:focus:opacity-100" @click="remove(i)" />
      </li>
    </ul>
    <div v-else-if="loaded" class="py-2 text-sm text-zinc-500">
      <p>{{ t('checklist.empty') }}</p>
      <Button :label="t('checklist.starters')" icon="pi pi-sparkles" size="small" outlined severity="secondary" class="mt-3" @click="addStarters" />
    </div>
    </div>
  </DashCard>

  <Dialog v-model:visible="adding" modal dismissable-mask :header="t('checklist.addTitle')" class="w-[24rem] max-w-[calc(100vw-2rem)]" @show="name = ''">
    <form id="checklist-add" @submit.prevent="add">
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('checklist.name') }}
        <InputText ref="nameInput" v-model="name" autofocus maxlength="100" :placeholder="t('checklist.placeholder')" />
      </label>
    </form>
    <template #footer>
      <Button :label="t('common.done')" text severity="secondary" @click="adding = false" />
      <Button type="submit" form="checklist-add" :label="t('shopping.add')" icon="pi pi-plus" :disabled="!name.trim()" />
    </template>
  </Dialog>
</template>
