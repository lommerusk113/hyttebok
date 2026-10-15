<script setup>
import { computed, onMounted, ref } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { api } from '../api'
import { askName, userName } from '../user'
import { timeAgo } from '../utils'
import { t } from '../i18n'
import { useDeepLink } from '../deepLink'
import RichText from './RichText.vue'

// Ideas nobody has agreed to yet, shown inside the list they belong to: DO under Vedlikehold, BUY on the
// shopping list. People vote with hearts; "Godta" hands it to `onAccept` (which adds the real task/item)
// and marks the suggestion done.
// `titled`: false when the list has its own card (whose header already says "Forslag").
const props = defineProps({ category: String, onAccept: Function, titled: { type: Boolean, default: true } })

const confirm = useConfirm()

// Each description opens on "Les mer"; short ones never need it.
const expanded = ref(new Set())
const isLong = text => text.length > 90 || text.includes('\n')
// Collapsed: one flowing line, bullets and line breaks turned into "·".
const preview = text => text.split('\n').map(line => line.replace(/^\s*[-*•]\s+/, '').trim()).filter(Boolean).join(' · ')
function toggle(id) {
  const next = new Set(expanded.value)
  next.has(id) ? next.delete(id) : next.add(id)
  expanded.value = next
}
const takeQuery = useDeepLink()
const suggestions = ref([])
const formOpen = ref(false)
const form = ref({})

onMounted(async () => {
  suggestions.value = await api.get('/suggestions')
  if (takeQuery().new === 'forslag' && props.category === 'DO') openForm()
})

const open = computed(() => suggestions.value.filter(s => !s.done && s.category === props.category).sort((a, b) => b.voters.length - a.voters.length))

function requireName() {
  if (!userName.value) askName.value = true
  return !!userName.value
}

function openForm(s) {
  if (!requireName()) return
  form.value = s ? { ...s } : { title: '', description: '', category: props.category, author: userName.value, voters: [userName.value], done: false }
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

async function accept(s) {
  if (!requireName()) return
  await props.onAccept(s)
  await put({ ...s, done: true })
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

defineExpose({ openForm })
</script>

<template>
  <section>
    <!-- In its own card the card header has the title and the "Foreslå" button (via openForm) -->
    <div v-if="titled" class="flex items-center justify-between gap-2">
      <h3 class="text-xs font-medium uppercase tracking-wide text-zinc-500">{{ t('suggestions.title') }}</h3>
      <Button :label="t('suggestions.propose')" icon="pi pi-lightbulb" text size="small" severity="secondary" class="!min-h-11" @click="openForm()" />
    </div>
    <ul v-if="open.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
      <li v-for="s in open" :key="s.id" class="flex items-start gap-3 py-2 pl-3 pr-4">
        <span class="w-1 self-stretch rounded-full bg-amber-300" />
        <div class="min-w-0 flex-1 py-1">
          <button class="min-h-9 text-left font-medium hover:underline" @click="openForm(s)">{{ s.title }}</button>
          <!-- Two lines to start with; "Les mer" shows it all, with bullet points and clickable links -->
          <template v-if="s.description">
            <RichText v-if="expanded.has(s.id)" :text="s.description" class="text-sm text-zinc-600" />
            <p v-else class="line-clamp-2 text-sm text-zinc-500">{{ preview(s.description) }}</p>
            <button v-if="isLong(s.description)" class="min-h-9 text-xs font-medium text-zinc-600 underline-offset-2 hover:underline" :aria-expanded="expanded.has(s.id)"
                    @click="toggle(s.id)">{{ expanded.has(s.id) ? t('suggestions.showLess') : t('suggestions.readMore') }}</button>
          </template>
          <span class="block text-xs text-zinc-500">{{ t('suggestions.suggestion') }} · {{ s.author }} · {{ timeAgo(s.createdAt) }}</span>
        </div>
        <button class="mt-1 flex min-h-11 shrink-0 items-center gap-1.5 rounded-full px-2 text-sm font-medium transition"
                :class="s.voters.includes(userName) ? 'text-rose-600' : 'text-zinc-500 hover:text-zinc-900'"
                :title="s.voters.join(', ')" :aria-label="t('suggestions.vote')" @click="vote(s)">
          <i class="pi" :class="s.voters.includes(userName) ? 'pi-heart-fill' : 'pi-heart'" />{{ s.voters.length }}
        </button>
        <Button :label="t('suggestions.accept')" size="small" severity="secondary" outlined class="mt-2 max-sm:!min-h-11 shrink-0" @click="accept(s)" />
      </li>
    </ul>
    <p v-else class="text-sm text-zinc-500">{{ t('suggestions.empty') }}</p>

  <Dialog dismissable-mask v-model:visible="formOpen" modal :header="form.id ? t('suggestions.suggestion') : t('suggestions.new')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <form :id="`suggestion-form-${category}`" class="flex flex-col gap-4" @submit.prevent="save">
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('suggestions.suggestion') }}<InputText v-model="form.title" required autofocus :placeholder="t(`suggestions.placeholder${category}`)" /></label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('suggestions.why') }}<Textarea v-model="form.description" rows="3" auto-resize />
        <span class="text-xs font-normal text-zinc-500">{{ t('home.bulletHint') }}</span></label>
    </form>
    <template #footer>
      <Button v-if="form.id" :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove(form)" />
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" :form="`suggestion-form-${category}`" :label="t('common.save')" icon="pi pi-check" :disabled="!form.title?.trim()" />
    </template>
  </Dialog>
  </section>
</template>
