<script setup>
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import { auth } from '../auth'
import { cabin } from '../cabin'
import { checkIn, openTasks, steps } from '../stay'
import { userName } from '../user'
import { t } from '../i18n'

// Arriving: a welcome, how to get the cabin going (from "Ankomst"), WiFi, what needs doing, and the chat if it's on.
// `booking` is the stay being checked in to; `saved` hands back the checked-in booking.
const props = defineProps({ booking: Object })
const emit = defineEmits(['saved'])
const visible = defineModel('visible', { type: Boolean })

const tasks = ref([])
const ticked = ref([])
const busy = ref(false)

watch(visible, async open => {
  if (!open) return
  ticked.value = []
  tasks.value = await openTasks()
})

const hidden = key => cabin.value.hiddenSections.includes(key)
const arrival = computed(() => (hidden('arrivalInfo') ? [] : steps(cabin.value.arrivalInfo)))
const wifi = computed(() => !hidden('wifi') && cabin.value.wifiName)

async function save() {
  busy.value = true
  try {
    emit('saved', await checkIn(props.booking))
    visible.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal dismissable-mask :header="t('stay.welcome', { cabin: cabin?.name, name: userName })" class="w-[34rem] max-w-[calc(100vw-2rem)]">
    <div class="flex flex-col gap-5">
      <p class="text-sm text-zinc-600">{{ t('stay.welcomeText') }}</p>

      <section v-if="arrival.length">
        <h3 class="mb-2 text-xs font-medium uppercase tracking-wider text-zinc-500">{{ t('stay.getStarted') }}</h3>
        <label v-for="(s, i) in arrival" :key="i" class="flex min-h-10 cursor-pointer items-start gap-3 rounded-md px-1 py-1.5 hover:bg-zinc-50">
          <Checkbox v-model="ticked" :value="i" class="mt-0.5" />
          <span class="text-sm" :class="ticked.includes(i) && 'text-zinc-400 line-through'">{{ s }}</span>
        </label>
      </section>

      <p v-if="wifi" class="flex items-center gap-2 rounded-lg bg-zinc-50 px-3 py-2 text-sm">
        <i class="pi pi-wifi text-zinc-500" />{{ cabin.wifiName }}<span v-if="cabin.wifiPassword" class="text-zinc-400">·</span><span class="font-mono">{{ cabin.wifiPassword }}</span>
      </p>

      <section v-if="tasks.length">
        <h3 class="mb-2 text-xs font-medium uppercase tracking-wider text-zinc-500">{{ t('stay.needsDoing', { n: tasks.length }) }}</h3>
        <ul class="divide-y divide-zinc-100 rounded-lg border border-zinc-200">
          <li v-for="x in tasks.slice(0, 6)" :key="x.key" class="flex items-center gap-3 px-3 py-2 text-sm">
            <i class="pi text-xs" :class="x.overdue ? 'pi-exclamation-circle text-rose-600' : x.todo ? 'pi-circle text-zinc-400' : 'pi-wrench text-zinc-400'" />
            <span class="min-w-0 flex-1 truncate">{{ x.name }}</span>
            <span v-if="x.overdue" class="text-xs font-medium text-rose-700">{{ t('stay.overdue') }}</span>
            <span v-else-if="x.claimedBy" class="text-xs text-sky-700">{{ t('todo.claimedBy', { name: x.claimedBy }) }}</span>
          </li>
        </ul>
        <RouterLink to="/vedlikehold" class="mt-2 inline-flex min-h-11 items-center gap-1.5 text-sm font-medium underline-offset-4 hover:underline" @click="visible = false">
          {{ t('stay.seeTasks') }}<i class="pi pi-arrow-right text-xs" />
        </RouterLink>
      </section>

      <p v-if="auth?.chatEnabled" class="flex gap-3 rounded-lg bg-violet-50 p-3 text-sm text-violet-900">
        <i class="pi pi-comments mt-0.5" /><span>{{ t('stay.chatHint') }}</span>
      </p>
    </div>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="visible = false" />
      <Button :label="t('stay.checkIn')" icon="pi pi-sign-in" :loading="busy" @click="save" />
    </template>
  </Dialog>
</template>
