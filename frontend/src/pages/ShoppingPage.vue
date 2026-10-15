<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import Drawer from 'primevue/drawer'
import InputText from 'primevue/inputtext'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import SuggestionList from '../components/SuggestionList.vue'
import ChecklistCard from '../components/ChecklistCard.vue'
import { api } from '../api'
import { addShoppingItem, toggleShopping, undoToast } from '../actions'
import { useToast } from 'primevue/usetoast'
import { timeAgo } from '../utils'
import { t } from '../i18n'

const items = ref([])
const name = ref('')
const quantity = ref('')
const boughtOpen = ref(false)
// "New" opens a dialog that stays open for adding several items in a row; what was added shows in it.
const adding = ref(false)
const addedNow = ref([])

// /handleliste/<section> (e.g. "Gå gjennom sjekklista" from Avreise) scrolls to that card once the list is in.
const route = useRoute()
onMounted(async () => {
  items.value = await api.get('/shopping')
  const section = route.params.sub
  if (section) setTimeout(() => document.getElementById(`lists-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300)
})

const toBuy = computed(() => items.value.filter(i => !i.bought))
const bought = computed(() => items.value.filter(i => i.bought))

async function add() {
  if (!name.value.trim()) return
  const saved = await addShoppingItem(name.value, quantity.value)
  items.value = [saved, ...items.value]
  addedNow.value = [...addedNow.value, saved.name]
  name.value = ''
  quantity.value = ''
  nameInput.value.$el.focus() // ready for the next item
}

const toast = useToast()
const nameInput = ref()
const ideas = ref()

function openAdding() {
  addedNow.value = []
  adding.value = true
}

async function toggle(item) {
  const updated = await toggleShopping(item)
  if (!updated) return
  items.value = items.value.map(i => (i.id === updated.id ? updated : i))
  if (updated.bought) undoToast(toast, t('shopping.boughtToast', { name: updated.name }), () => toggle(updated))
}

async function remove(item) {
  await api.del(`/shopping/${item.id}`)
  items.value = items.value.filter(i => i.id !== item.id)
}

// A suggested purchase someone said yes to goes on the list.
async function acceptSuggestion(s) {
  items.value = [await addShoppingItem(s.title, ''), ...items.value]
}

async function clearBought() {
  await Promise.all(bought.value.map(i => api.del(`/shopping/${i.id}`)))
  items.value = toBuy.value
}
</script>

<template>
  <!-- The list and the purchase ideas side by side (stacked on phones): neither needs the full width -->
  <div class="grid grid-cols-1 gap-6 lg:grid-cols-2 lg:items-start">
  <DashCard :title="t('tabs.shopping')" icon="pi pi-shopping-cart" tint="bg-amber-50 text-amber-700" :count="toBuy.length">
    <template #action>
      <Button icon="pi pi-plus" :label="t('common.new')" size="small" @click="openAdding" />
    </template>

    <!-- What's missing; what's been bought waits in a slide-in -->
    <div v-if="bought.length" class="-mt-1 mb-2 flex justify-end">
      <Button :label="t('shopping.seeBought', { n: bought.length })" text size="small" severity="secondary" class="!min-h-11" @click="boughtOpen = true" />
    </div>

    <ul v-if="toBuy.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
      <li v-for="i in toBuy" :key="i.id" class="group flex items-center gap-3 px-5 py-1">
        <label class="flex min-h-11 min-w-0 flex-1 cursor-pointer items-center gap-3">
          <Checkbox :model-value="false" binary @update:model-value="toggle(i)" />
          <span class="min-w-0 flex-1">
            <span class="block font-medium">{{ i.name }}</span>
            <span v-if="i.addedBy" class="block text-xs text-zinc-500">{{ i.addedBy }} · {{ timeAgo(i.createdAt) }}</span>
          </span>
        </label>
        <span v-if="i.quantity" class="rounded-full bg-zinc-100 px-2.5 py-0.5 text-sm text-zinc-600">{{ i.quantity }}</span>
        <Button icon="pi pi-times" text rounded severity="secondary" size="small" :aria-label="t('shopping.remove')" class="max-sm:!size-10 sm:opacity-0 sm:group-hover:opacity-100" @click="remove(i)" />
      </li>
    </ul>
    <EmptyState v-else icon="pi pi-shopping-bag" :text="t('shopping.empty')" />

  </DashCard>

  <!-- Right column: what should always be there, and purchase ideas -->
  <div class="flex flex-col gap-6">
  <ChecklistCard :shopping="items" @added="item => (items = [item, ...items])" />

  <DashCard id="lists-forslag" class="scroll-mt-36" :title="t('suggestions.title')" icon="pi pi-lightbulb" tint="bg-amber-50 text-amber-700">
    <template #action>
      <Button :label="t('suggestions.propose')" icon="pi pi-lightbulb" size="small" outlined severity="secondary" @click="ideas.openForm()" />
    </template>
    <SuggestionList ref="ideas" category="BUY" :titled="false" @accept="acceptSuggestion" />
  </DashCard>
  </div>
  </div>

  <Dialog v-model:visible="adding" modal dismissable-mask :header="t('shopping.addTitle')" class="w-[26rem] max-w-[calc(100vw-2rem)]">
    <form id="shopping-add" class="grid grid-cols-[1fr_5rem] gap-3" @submit.prevent="add">
      <label class="flex min-w-0 flex-col gap-1.5 text-sm font-medium">{{ t('shopping.item') }}
        <InputText ref="nameInput" v-model="name" autofocus :placeholder="t('shopping.placeholder')" />
      </label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('shopping.quantityShort') }}
        <InputText v-model="quantity" :aria-label="t('shopping.quantity')" class="text-center" />
      </label>
    </form>
    <!-- What went on the list while the dialog was open -->
    <div v-if="addedNow.length" class="mt-4 flex flex-wrap gap-1.5" aria-live="polite">
      <span v-for="(n, i) in addedNow" :key="i" class="flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs text-emerald-800"><i class="pi pi-check text-[10px]" />{{ n }}</span>
    </div>
    <template #footer>
      <Button :label="t('common.done')" text severity="secondary" @click="adding = false" />
      <Button type="submit" form="shopping-add" :label="t('shopping.add')" icon="pi pi-plus" :disabled="!name.trim()" />
    </template>
  </Dialog>

  <Drawer v-model:visible="boughtOpen" position="right" :header="t('shopping.boughtTab')" class="!w-full sm:!w-[26rem]">
    <ul v-if="bought.length" class="-mx-5 divide-y divide-zinc-100">
      <li v-for="i in bought" :key="i.id">
        <label class="flex min-h-11 cursor-pointer items-center gap-3 px-5 py-1">
          <Checkbox :model-value="true" binary @update:model-value="toggle(i)" />
          <span class="flex-1 text-zinc-500 line-through">{{ i.name }}</span>
          <span class="text-xs text-zinc-500">{{ i.boughtBy }}</span>
        </label>
      </li>
    </ul>
    <EmptyState v-else icon="pi pi-inbox" :text="t('shopping.noneBought')" />
    <Button v-if="bought.length" :label="t('common.clear')" icon="pi pi-trash" text size="small" severity="secondary" class="mt-3" @click="clearBought" />
  </Drawer>
</template>
