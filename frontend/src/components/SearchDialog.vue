<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import { api } from '../api'
import { inCabin } from '../hytte'
import { formatDate } from '../utils'
import { t } from '../i18n'

// Search the cabin on screen: bookings, the cabin book, forum, wiki (and its files), tasks, maintenance, ideas,
// shopping and expenses. Results come from the server, grouped by kind; a hit goes to where it lives.
const visible = defineModel('visible', { type: Boolean })
const router = useRouter()
const query = ref('')
const hits = ref(null)
const busy = ref(false)

const KINDS = {
  booking: { icon: 'pi pi-calendar', link: h => `/booking?id=${h.id}` },
  log: { icon: 'pi pi-book', link: h => `/hyttebok?id=${h.id}` },
  forum: { icon: 'pi pi-comments', link: h => `/forum/${h.id}` },
  reply: { icon: 'pi pi-comment', link: h => `/forum/${h.ref}` },
  wiki: { icon: 'pi pi-info-circle', link: h => `/wiki/${h.id}` },
  file: { icon: 'pi pi-paperclip', link: h => `/wiki/${h.ref}` },
  todo: { icon: 'pi pi-check-square', link: h => `/vedlikehold?todo=${h.id}` },
  maintenance: { icon: 'pi pi-wrench', link: h => `/vedlikehold?id=${h.id}` },
  idea: { icon: 'pi pi-lightbulb', link: h => (h.ref === 'BUY' ? '/handleliste' : '/vedlikehold/forslag') },
  shopping: { icon: 'pi pi-shopping-cart', link: () => '/handleliste' },
  expense: { icon: 'pi pi-wallet', link: () => '/utgifter' },
}

// Typing searches after a short pause; an answer for an older query is dropped.
let timer
let asked = 0
watch(query, q => {
  clearTimeout(timer)
  if (q.trim().length < 2) {
    hits.value = null
    return
  }
  timer = setTimeout(async () => {
    const mine = ++asked
    busy.value = true
    try {
      const found = await api.get(`/search?q=${encodeURIComponent(q.trim())}`)
      if (mine === asked) hits.value = found
    } finally {
      if (mine === asked) busy.value = false
    }
  }, 250)
})
watch(visible, open => { if (open) { query.value = ''; hits.value = null } })

const groups = computed(() => {
  const by = {}
  for (const h of hits.value || []) (by[h.type] ??= []).push(h)
  return Object.keys(KINDS).filter(k => by[k]).map(k => ({ kind: k, hits: by[k] }))
})

function go(h) {
  visible.value = false
  router.push(inCabin(KINDS[h.type].link(h)))
}

// The search word in bold, so you see why it's a hit.
function parts(text) {
  const q = query.value.trim().toLowerCase()
  const at = text.toLowerCase().indexOf(q)
  return at < 0 ? [{ text }] : [{ text: text.slice(0, at) }, { text: text.slice(at, at + q.length), match: true }, { text: text.slice(at + q.length) }]
}
</script>

<template>
  <Dialog v-model:visible="visible" modal dismissable-mask position="top" :show-header="false" class="mt-16 w-[38rem] max-w-[calc(100vw-2rem)]"
          :pt="{ content: { class: '!p-0' } }">
    <div class="border-b border-zinc-200 p-3">
      <IconField>
        <InputIcon :class="busy ? 'pi pi-spin pi-spinner' : 'pi pi-search'" />
        <InputText v-model="query" type="search" autofocus :placeholder="t('search.placeholder')" :aria-label="t('search.title')" class="w-full !border-0 !shadow-none" />
      </IconField>
    </div>
    <div class="max-h-[60vh] overflow-y-auto">
      <p v-if="!hits" class="p-5 text-sm text-zinc-500">{{ t('search.hint') }}</p>
      <p v-else-if="!hits.length" class="p-5 text-sm text-zinc-500">{{ t('search.nothing', { q: query.trim() }) }}</p>
      <section v-for="g in groups" :key="g.kind" class="py-2">
        <h3 class="px-4 pb-1 text-xs font-medium uppercase tracking-wider text-zinc-500">{{ t(`search.kinds.${g.kind}`) }}</h3>
        <button v-for="h in g.hits" :key="`${h.type}-${h.id}`" type="button" class="flex w-full items-start gap-3 px-4 py-2 text-left transition hover:bg-zinc-50 focus:bg-zinc-50 focus:outline-none"
                @click="go(h)">
          <i :class="KINDS[h.type].icon" class="mt-1 text-sm text-zinc-400" />
          <span class="min-w-0 flex-1">
            <span class="block truncate text-sm font-medium"><template v-for="(p, i) in parts(h.title || '')" :key="i"><mark v-if="p.match" class="rounded bg-amber-100 text-inherit">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></span>
            <span v-if="h.snippet" class="line-clamp-2 block text-xs text-zinc-500"><template v-for="(p, i) in parts(h.snippet)" :key="i"><mark v-if="p.match" class="rounded bg-amber-100 text-inherit">{{ p.text }}</mark><template v-else>{{ p.text }}</template></template></span>
          </span>
          <span v-if="h.date" class="shrink-0 text-xs text-zinc-400">{{ formatDate(h.date) }}</span>
        </button>
      </section>
    </div>
  </Dialog>
</template>
