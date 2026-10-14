<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { api } from '../api'
import { addShoppingItem, toggleShopping, undoToast } from '../actions'
import { useToast } from 'primevue/usetoast'
import { timeAgo } from '../utils'
import { t } from '../i18n'

const items = ref([])
const name = ref('')
const quantity = ref('')
const filter = ref('toBuy')
// The add fields stay out of the way until "New" is tapped, then stay open for adding several items.
const adding = ref(false)

onMounted(async () => { items.value = await api.get('/shopping') })

const toBuy = computed(() => items.value.filter(i => !i.bought))
const bought = computed(() => items.value.filter(i => i.bought))

async function add() {
  if (!name.value.trim()) return
  const saved = await addShoppingItem(name.value, quantity.value)
  items.value = [saved, ...items.value]
  name.value = ''
  quantity.value = ''
  nameInput.value.$el.focus() // ready for the next item
}

const toast = useToast()
const nameInput = ref()

async function toggleAdding() {
  adding.value = !adding.value
  filter.value = 'toBuy' // new items land in "to buy"; show them
  if (adding.value) {
    await nextTick()
    nameInput.value.$el.focus()
  }
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

async function clearBought() {
  await Promise.all(bought.value.map(i => api.del(`/shopping/${i.id}`)))
  items.value = toBuy.value
}
</script>

<template>
  <DashCard :title="t('tabs.shopping')" icon="pi pi-shopping-cart" tint="bg-amber-50 text-amber-700" :count="toBuy.length">
    <template #action>
      <Button :icon="adding ? 'pi pi-times' : 'pi pi-plus'" :label="adding ? t('common.done') : t('common.new')" size="small"
              :outlined="adding" :severity="adding ? 'secondary' : undefined" @click="toggleAdding" />
    </template>

    <form v-if="adding" class="mb-3 grid grid-cols-[1fr_3.5rem_auto] gap-2" @submit.prevent="add" @keydown.esc="adding = false">
      <InputText ref="nameInput" v-model="name" :placeholder="t('shopping.placeholder')" :aria-label="t('shopping.item')" class="min-w-0" />
      <InputText v-model="quantity" :placeholder="t('shopping.quantityShort')" :aria-label="t('shopping.quantity')" class="!px-2 text-center" />
      <Button type="submit" :label="t('shopping.add')" :disabled="!name.trim()" />
    </form>

    <!-- Same two views as Gjøremål: what's missing, and what's been bought -->
    <div class="mb-3 flex items-center justify-between gap-2">
      <SelectButton v-model="filter" :options="['toBuy', 'bought']" :allow-empty="false">
        <template #option="{ option }">
          {{ t(`shopping.${option}Tab`) }}
          <span class="ml-1.5 rounded-full bg-zinc-200/70 px-1.5 text-xs">{{ option === 'toBuy' ? toBuy.length : bought.length }}</span>
        </template>
      </SelectButton>
      <Button v-if="filter === 'bought' && bought.length" :label="t('common.clear')" icon="pi pi-trash" text size="small" severity="secondary" @click="clearBought" />
    </div>

    <template v-if="filter === 'toBuy'">
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
    </template>

    <template v-else>
      <ul v-if="bought.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
        <li v-for="i in bought" :key="i.id">
          <label class="flex min-h-11 cursor-pointer items-center gap-3 px-5 py-1">
            <Checkbox :model-value="true" binary @update:model-value="toggle(i)" />
            <span class="flex-1 text-zinc-500 line-through">{{ i.name }}</span>
            <span class="text-xs text-zinc-500">{{ i.boughtBy }}</span>
          </label>
        </li>
      </ul>
      <EmptyState v-else icon="pi pi-inbox" :text="t('shopping.noneBought')" />
    </template>
  </DashCard>
</template>
