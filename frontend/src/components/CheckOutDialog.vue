<script setup>
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import { api } from '../api'
import { addShoppingItem } from '../actions'
import { cabin } from '../cabin'
import { checkOut, complete, openTasks, steps } from '../stay'
import { t } from '../i18n'

// Leaving, in three steps on one page: what you got done (ticked tasks are marked done), what's running out (onto
// the shopping list), and "Avreise" as a checklist. Then a thank-you with a nudge to write in the cabin book.
const props = defineProps({ booking: Object })
const emit = defineEmits(['saved'])
const visible = defineModel('visible', { type: Boolean })

const tasks = ref([])
const doneTasks = ref([])
const checklist = ref([])
const shopping = ref([])
const extra = ref('')
const ticked = ref([])
const busy = ref(false)
const finished = ref(false)

watch(visible, async open => {
  if (!open) return
  doneTasks.value = []
  ticked.value = []
  finished.value = false
  ;[tasks.value, checklist.value, shopping.value] = await Promise.all([openTasks(), api.get('/checklist'), api.get('/shopping')])
})

const departure = computed(() => (cabin.value.hiddenSections.includes('departureInfo') ? [] : steps(cabin.value.departureInfo)))
const sameName = (a, b) => a.trim().toLowerCase() === b.trim().toLowerCase()
const onList = name => shopping.value.some(s => !s.bought && sameName(s.name, name))

async function missing(name) {
  if (!name.trim() || onList(name)) return
  shopping.value = [await addShoppingItem(name, ''), ...shopping.value]
}

async function addExtra() {
  await missing(extra.value)
  extra.value = ''
}

async function save() {
  busy.value = true
  try {
    for (const task of tasks.value.filter(x => doneTasks.value.includes(x.key))) await complete(task)
    emit('saved', await checkOut(props.booking))
    finished.value = true
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal dismissable-mask :header="finished ? t('stay.thanks') : t('stay.checkOutTitle')" class="w-[34rem] max-w-[calc(100vw-2rem)]">
    <div v-if="finished" class="flex flex-col items-center gap-4 py-4 text-center">
      <span class="grid size-14 place-items-center rounded-full bg-emerald-50 text-emerald-700"><i class="pi pi-check text-2xl" /></span>
      <p class="text-sm text-zinc-600">{{ t('stay.thanksText', { cabin: cabin?.name }) }}</p>
      <RouterLink to="/hyttebok" class="inline-flex min-h-11 items-center gap-2 rounded-md bg-zinc-950 px-4 text-sm font-medium text-surface" @click="visible = false">
        <i class="pi pi-book text-xs" />{{ t('stay.writeLog') }}
      </RouterLink>
    </div>

    <div v-else class="flex flex-col gap-6">
      <section v-if="tasks.length">
        <h3 class="mb-1 text-sm font-semibold">{{ t('stay.didAnything') }}</h3>
        <p class="mb-2 text-xs text-zinc-500">{{ t('stay.didAnythingHint') }}</p>
        <label v-for="x in tasks" :key="x.key" class="flex min-h-10 cursor-pointer items-center gap-3 rounded-md px-1 hover:bg-zinc-50">
          <Checkbox v-model="doneTasks" :value="x.key" />
          <span class="min-w-0 flex-1 truncate text-sm">{{ x.name }}</span>
          <span v-if="x.overdue" class="text-xs font-medium text-rose-700">{{ t('stay.overdue') }}</span>
        </label>
      </section>

      <section>
        <h3 class="mb-1 text-sm font-semibold">{{ t('stay.runningOut') }}</h3>
        <p class="mb-2 text-xs text-zinc-500">{{ t('stay.runningOutHint') }}</p>
        <div class="flex flex-wrap gap-2">
          <button v-for="c in checklist" :key="c.id" type="button" class="flex min-h-9 items-center gap-1.5 rounded-full border px-3 text-sm transition"
                  :class="onList(c.name) ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-zinc-200 hover:border-zinc-400'"
                  :aria-pressed="onList(c.name)" @click="missing(c.name)">
            <i class="pi text-[10px]" :class="onList(c.name) ? 'pi-check' : 'pi-plus'" />{{ c.name }}
          </button>
        </div>
        <form class="mt-3 flex gap-2" @submit.prevent="addExtra">
          <InputText v-model="extra" :placeholder="t('stay.somethingElse')" class="min-w-0 flex-1" />
          <Button type="submit" icon="pi pi-cart-plus" :aria-label="t('stay.addToShopping')" severity="secondary" outlined :disabled="!extra.trim()" />
        </form>
      </section>

      <section v-if="departure.length">
        <h3 class="mb-2 text-sm font-semibold">{{ t('stay.beforeYouGo') }} <span class="font-normal tabular-nums text-zinc-500">{{ ticked.length }}/{{ departure.length }}</span></h3>
        <label v-for="(s, i) in departure" :key="i" class="flex min-h-10 cursor-pointer items-start gap-3 rounded-md px-1 py-1.5 hover:bg-zinc-50">
          <Checkbox v-model="ticked" :value="i" class="mt-0.5" />
          <span class="text-sm" :class="ticked.includes(i) && 'text-zinc-400 line-through'">{{ s }}</span>
        </label>
      </section>
    </div>

    <template v-if="!finished" #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="visible = false" />
      <Button :label="t('stay.checkOut')" icon="pi pi-sign-out" :loading="busy" @click="save" />
    </template>
  </Dialog>
</template>
