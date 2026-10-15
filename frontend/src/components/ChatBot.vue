<script setup>
import { nextTick, ref } from 'vue'
import Button from 'primevue/button'
import Drawer from 'primevue/drawer'
import Textarea from 'primevue/textarea'
import RichText from './RichText.vue'
import { userName } from '../user'
import { dataVersion } from '../offline'
import { cabinHeaders } from '../hytte'
import { locale, t } from '../i18n'

// Chat with the cabin (Gemini, via /api/chat). It can answer from the app's data and make changes
// (shopping list, to-dos, bookings, maintenance); after a change the open views reload.
// ponytail: the conversation lives in memory only; closing the tab starts a new one.
const open = ref(false)
const messages = ref([]) // { role: 'user' | 'model', text, actions? }
const draft = ref('')
const busy = ref(false)
const list = ref()

const EXAMPLES = ['chat.example1', 'chat.example2', 'chat.example3']

async function scrollDown() {
  await nextTick()
  list.value?.scrollTo({ top: list.value.scrollHeight, behavior: 'smooth' })
}

async function send(text = draft.value) {

  text = text.trim()
  if (!text || busy.value) return
  messages.value.push({ role: 'user', text })
  draft.value = ''
  busy.value = true
  scrollDown()

  try {
    // Straight fetch, not api.post: a chat question shouldn't wait in the offline queue.
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...cabinHeaders() },
      body: JSON.stringify({ messages: messages.value.map(({ role, text }) => ({ role, text })), name: userName.value || null, lang: locale.value }),
    })
    if (!res.ok) throw new Error(res.status === 503 ? 'chat.notSetUp' : res.status === 429 ? 'chat.quota' : res.status === 502 ? 'chat.busy' : 'chat.failed')
    const reply = await res.json()
    messages.value.push({ role: 'model', text: reply.text || t('chat.noAnswer'), actions: reply.actions })
    if (reply.actions.length) dataVersion.value++ // show the changes in the app right away
  } catch (err) {
    messages.value.push({ role: 'model', text: t(err.message.startsWith('chat.') ? err.message : navigator.onLine ? 'chat.failed' : 'chat.offline'), error: true })
  } finally {
    busy.value = false
    scrollDown()
  }
}

// Enter sends, Shift+Enter makes a new line (phones get a send button).
function onKey(e) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <button v-if="!open" class="fixed bottom-5 right-5 z-20 grid size-14 place-items-center rounded-full bg-zinc-950 text-surface shadow-lg transition hover:scale-105"
          :aria-label="t('chat.title')" v-tooltip.left="t('chat.title')" @click="open = true">
    <i class="pi pi-comments text-xl" />
  </button>

  <Drawer v-model:visible="open" position="right" :header="t('chat.title')" class="chat-drawer !w-full sm:!w-[28rem]" :pt="{ content: { class: '!p-0 flex flex-col' } }">
    <div ref="list" class="flex-1 space-y-3 overflow-y-auto px-5 py-4">
      <div v-if="!messages.length" class="space-y-3">
        <p class="text-sm text-zinc-600">{{ t('chat.intro') }}</p>
        <div class="flex flex-col items-start gap-2">
          <button v-for="key in EXAMPLES" :key="key" class="min-h-11 rounded-full border border-zinc-200 px-4 py-2 text-left text-sm transition hover:border-zinc-400"
                  @click="send(t(key))">{{ t(key) }}</button>
        </div>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="flex" :class="m.role === 'user' ? 'justify-end' : 'justify-start'">
        <div class="max-w-[85%] rounded-2xl px-4 py-2.5 text-sm"
             :class="m.role === 'user' ? 'rounded-br-md bg-zinc-950 text-surface' : m.error ? 'rounded-bl-md bg-amber-50 text-amber-900' : 'rounded-bl-md bg-zinc-100 text-zinc-900'">
          <RichText v-if="m.role === 'model'" :text="m.text" />
          <p v-else class="whitespace-pre-line">{{ m.text }}</p>
          <ul v-if="m.actions?.length" class="mt-2 space-y-1 border-t border-zinc-200 pt-2">
            <li v-for="a in m.actions" :key="a" class="flex items-start gap-1.5 text-xs font-medium text-emerald-700"><i class="pi pi-check-circle mt-0.5 text-[11px]" />{{ a }}</li>
          </ul>
        </div>
      </div>

      <div v-if="busy" class="flex justify-start" role="status" :aria-label="t('chat.thinking')">
        <div class="flex gap-1 rounded-2xl rounded-bl-md bg-zinc-100 px-4 py-3.5">
          <span v-for="n in 3" :key="n" class="size-1.5 animate-bounce rounded-full bg-zinc-400" :style="{ animationDelay: `${n * 120}ms` }" />
        </div>
      </div>
    </div>

    <form class="flex items-end gap-2 border-t border-zinc-200 p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]" @submit.prevent="send()">
      <Textarea v-model="draft" rows="1" auto-resize :placeholder="t('chat.placeholder')" :aria-label="t('chat.placeholder')"
                class="max-h-40 flex-1 !text-base sm:!text-sm" @keydown="onKey" />
      <Button type="submit" icon="pi pi-send" :aria-label="t('chat.send')" :disabled="!draft.trim() || busy" class="!size-11 shrink-0" />
    </form>
  </Drawer>
</template>
