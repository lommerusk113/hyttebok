<script setup>
import { computed, ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { userName } from '../user'
import { t } from '../i18n'

// "Utført": who took part (you to start with; the cabin's members one tap away, anyone else typed) and a
// few words about it. Opens when `task` is set; `save` gets { participants, note }.
const props = defineProps({ task: String, people: { type: Array, default: () => [] } })
const emit = defineEmits(['save', 'cancel'])

const participants = ref([])
const note = ref('')
const typed = ref('')
const busy = ref(false)

watch(() => props.task, task => {
  if (!task) return
  participants.value = userName.value ? [userName.value] : []
  note.value = ''
  typed.value = ''
  busy.value = false
}, { immediate: true })

const same = (a, b) => a.trim().toLowerCase() === b.trim().toLowerCase()
const has = name => participants.value.some(p => same(p, name))
const others = computed(() => props.people.filter(p => !has(p)))

function add(name = typed.value) {
  const clean = name.trim()
  if (clean && !has(clean)) participants.value = [...participants.value, clean]
  typed.value = ''
}

const remove = name => { participants.value = participants.value.filter(p => p !== name) }

function save() {
  add() // a name still in the field counts too
  busy.value = true
  emit('save', { participants: participants.value, note: note.value.trim() || null })
}
</script>

<template>
  <Dialog :visible="!!task" modal dismissable-mask :header="t('done.title', { task })" class="w-[28rem] max-w-[calc(100vw-2rem)]"
          @update:visible="v => !v && emit('cancel')">
    <form id="done-form" class="flex flex-col gap-4" @submit.prevent="save">
      <div class="flex flex-col gap-2 text-sm font-medium">
        {{ t('done.who') }}
        <!-- Chosen: tap ✕ to take someone off -->
        <div class="flex flex-wrap gap-1.5">
          <span v-for="p in participants" :key="p" class="flex items-center gap-1 rounded-full bg-zinc-950 py-1 pl-3 pr-1 text-sm font-normal text-surface">
            {{ p }}
            <button type="button" class="grid size-6 place-items-center rounded-full hover:bg-white/20" :aria-label="t('done.remove', { name: p })" @click="remove(p)">
              <i class="pi pi-times text-[10px]" />
            </button>
          </span>
          <span v-if="!participants.length" class="py-1 text-sm font-normal text-zinc-500">{{ t('done.nobody') }}</span>
        </div>
        <!-- The cabin's members, one tap each -->
        <div v-if="others.length" class="flex flex-wrap gap-1.5">
          <button v-for="p in others" :key="p" type="button" class="flex min-h-9 items-center gap-1 rounded-full border border-zinc-300 px-3 text-sm font-normal transition hover:border-zinc-500"
                  @click="add(p)"><i class="pi pi-plus text-[10px]" />{{ p }}</button>
        </div>
        <!-- Someone else (a guest, or no accounts at all) -->
        <div class="flex gap-2">
          <InputText v-model="typed" :placeholder="t('done.addName')" :aria-label="t('done.addName')" class="min-w-0 flex-1 font-normal" maxlength="40"
                     @keydown.enter.prevent="add()" />
          <Button type="button" icon="pi pi-plus" outlined severity="secondary" :aria-label="t('done.add')" :disabled="!typed.trim()" @click="add()" />
        </div>
      </div>

      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('done.note') }}
        <Textarea v-model="note" rows="2" auto-resize maxlength="2000" :placeholder="t('done.notePlaceholder')" class="font-normal" />
      </label>
    </form>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="emit('cancel')" />
      <Button type="submit" form="done-form" :label="t('done.save')" icon="pi pi-check" :loading="busy" />
    </template>
  </Dialog>
</template>
