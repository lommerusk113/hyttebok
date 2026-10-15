<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import EmptyState from '../components/EmptyState.vue'
import RichText from '../components/RichText.vue'
import { api } from '../api'
import { auth } from '../auth'
import { askName, userName } from '../user'
import { formatDate, timeAgo } from '../utils'
import { t } from '../i18n'

// The cabin's wiki (/h/1/wiki, one page at /h/1/wiki/3): how things work, where things are. Anyone in the cabin
// writes and edits; the chatbot reads it all. Phones show the list or a page; wider screens show both.
const route = useRoute()
const router = useRouter()
const confirm = useConfirm()

const pages = ref(null)
const query = ref('')
const form = ref(null) // { id?, title, content } while writing
const busy = ref(false)
const titleInput = ref()

const base = computed(() => `/h/${route.params.cabinId}/wiki`)
const pageId = computed(() => Number(route.params.sub) || null)
const page = computed(() => pages.value?.find(p => p.id === pageId.value) ?? null)

onMounted(async () => { pages.value = await api.get('/wiki') })

const shown = computed(() => {
  const q = query.value.trim().toLowerCase()
  return (pages.value || []).filter(p => !q || p.title.toLowerCase().includes(q) || p.content?.toLowerCase().includes(q))
})

async function write(p) {
  if (!userName.value) {
    askName.value = true
    return
  }
  form.value = p ? { id: p.id, title: p.title, content: p.content || '' } : { title: '', content: '' }
  if (!p) router.push(base.value) // a new page: no page behind the form
  await nextTick()
  titleInput.value?.$el.focus()
}

async function save() {
  busy.value = true
  try {
    const body = { ...form.value, title: form.value.title.trim(), content: form.value.content.trim() || null, updatedBy: userName.value }
    const saved = await (form.value.id ? api.put('/wiki', { ...page.value, ...body }) : api.post('/wiki', body))
    pages.value = [...pages.value.filter(p => p.id !== saved.id), saved].sort((a, b) => a.title.localeCompare(b.title))
    form.value = null
    router.push(`${base.value}/${saved.id}`)
  } finally {
    busy.value = false
  }
}

function remove() {
  confirm.require({
    header: t('wiki.deleteTitle'),
    message: page.value.title,
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/wiki/${page.value.id}`)
      pages.value = pages.value.filter(p => p.id !== page.value.id)
      router.push(base.value)
    },
  })
}
</script>

<template>
  <div class="space-y-4">
    <p v-if="auth?.chatEnabled" class="flex items-start gap-2 rounded-lg bg-sky-50 p-3 text-sm text-sky-900">
      <i class="pi pi-sparkles mt-0.5" />{{ t('wiki.chatHint') }}
    </p>

    <div class="grid gap-4 lg:grid-cols-[18rem_1fr] lg:items-start">
      <!-- Pages (on phones only when no page is open) -->
      <section :class="(pageId || form) && 'max-lg:hidden'" class="card p-4 lg:sticky lg:top-36">
        <div class="mb-3 flex items-center justify-between gap-2">
          <h2 class="font-semibold">{{ t('wiki.title') }}</h2>
          <Button icon="pi pi-plus" :label="t('wiki.new')" size="small" @click="write()" />
        </div>
        <IconField v-if="pages?.length > 5" class="mb-2">
          <InputIcon class="pi pi-search" />
          <InputText v-model="query" type="search" :placeholder="t('wiki.search')" :aria-label="t('wiki.search')" class="w-full" size="small" />
        </IconField>
        <nav v-if="shown.length" class="-mx-2 flex flex-col">
          <RouterLink v-for="p in shown" :key="p.id" :to="`${base}/${p.id}`" :aria-current="p.id === pageId ? 'page' : undefined"
                      :class="p.id === pageId && !form ? 'bg-zinc-100 font-semibold' : 'text-zinc-700'"
                      class="flex min-h-10 items-center gap-2 rounded-md px-2 text-sm hover:bg-zinc-100">
            <i class="pi pi-file text-xs text-zinc-400" /><span class="truncate">{{ p.title }}</span>
          </RouterLink>
        </nav>
        <p v-else-if="pages && query.trim()" class="py-2 text-sm text-zinc-500">{{ t('wiki.noMatch') }}</p>
        <p v-else-if="pages" class="py-2 text-sm text-zinc-500">{{ t('wiki.empty') }}</p>
      </section>

      <!-- Writing -->
      <form v-if="form" class="card space-y-3 p-5" @submit.prevent="save">
        <InputText ref="titleInput" v-model="form.title" required maxlength="200" :placeholder="t('wiki.titlePlaceholder')" :aria-label="t('wiki.pageTitle')"
                   class="w-full !text-lg !font-semibold" />
        <Textarea v-model="form.content" rows="12" auto-resize maxlength="20000" :placeholder="t('wiki.contentPlaceholder')" :aria-label="t('wiki.content')" class="w-full" />
        <p class="text-xs text-zinc-500">{{ t('wiki.formatHint') }}</p>
        <div class="flex justify-end gap-2">
          <Button :label="t('common.cancel')" text severity="secondary" @click="form = null" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="busy" :disabled="!form.title.trim()" />
        </div>
      </form>

      <!-- Reading -->
      <article v-else-if="page" class="card p-5">
        <RouterLink :to="base" class="mb-2 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-zinc-500 hover:text-zinc-950 lg:hidden">
          <i class="pi pi-arrow-left text-xs" />{{ t('wiki.title') }}
        </RouterLink>
        <div class="flex items-start gap-2">
          <h1 class="min-w-0 flex-1 text-xl font-semibold">{{ page.title }}</h1>
          <Button icon="pi pi-pencil" text rounded severity="secondary" :aria-label="t('common.edit')" @click="write(page)" />
          <Button icon="pi pi-trash" text rounded severity="secondary" :aria-label="t('common.delete')" @click="remove" />
        </div>
        <p class="mt-1 text-xs text-zinc-500" :title="formatDate(page.updatedAt)">
          {{ page.updatedBy ? t('wiki.updatedBy', { name: page.updatedBy, time: timeAgo(page.updatedAt) }) : timeAgo(page.updatedAt) }}
        </p>
        <RichText v-if="page.content" :text="page.content" class="mt-4" />
        <p v-else class="mt-4 text-sm text-zinc-500">{{ t('wiki.blank') }}</p>
      </article>

      <EmptyState v-else-if="pageId && pages" icon="pi pi-file" :text="t('wiki.missing')" class="card" />
      <EmptyState v-else-if="pages" icon="pi pi-info-circle" :text="t('wiki.pick')" class="card max-lg:hidden" />
    </div>
  </div>
</template>
