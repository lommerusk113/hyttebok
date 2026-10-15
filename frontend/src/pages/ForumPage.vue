<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import EmptyState from '../components/EmptyState.vue'
import ForumPostCard from '../components/ForumPostCard.vue'
import { api } from '../api'
import { askName, userName } from '../user'
import { colorFor, initials } from '../utils'
import { t } from '../i18n'

// The cabin's forum as a feed (/h/1/forum), or one post with its comments open (/h/1/forum/12, e.g. from a notification).
const route = useRoute()
const router = useRouter()

const SORTS = ['active', 'new', 'old', 'replies']
const sort = ref('active')
const query = ref('')
const feed = ref(null)
const single = ref(null)
const draft = ref('')
const busy = ref(false)
const form = ref(null) // the post being edited: { id, title, body }

const postId = computed(() => route.params.sub)
const base = computed(() => `/h/${route.params.cabinId}/forum`)

async function loadFeed() {
  const params = new URLSearchParams({ sort: sort.value, ...(query.value.trim() && { q: query.value.trim() }) })
  feed.value = await api.get(`/forum?${params}`)
}

async function loadSingle() {
  single.value = null
  single.value = await api.get(`/forum/${postId.value}`).catch(() => ({ missing: true }))
}

// Typing searches after a short pause instead of on every key.
let timer
watch(query, () => { clearTimeout(timer); timer = setTimeout(loadFeed, 300) })
watch(sort, loadFeed)
watch(postId, id => (id ? loadSingle() : loadFeed()), { immediate: true })

async function publish() {
  if (!draft.value.trim()) return
  if (!userName.value) {
    askName.value = true
    return
  }
  busy.value = true
  try {
    const saved = await api.post('/forum', { body: draft.value, author: userName.value })
    feed.value = [{ ...saved, reactions: [], comments: [] }, ...feed.value]
    draft.value = ''
  } finally {
    busy.value = false
  }
}

const edit = post => { form.value = { id: post.id, title: post.title || '', body: post.body || '', hadTitle: !!post.title } }

async function saveEdit() {
  busy.value = true
  try {
    const saved = await api.put(`/forum/${form.value.id}`, form.value)
    const post = single.value?.id === saved.id ? single.value : feed.value.find(p => p.id === saved.id)
    Object.assign(post, { title: saved.title, body: saved.body, editedAt: saved.editedAt })
    form.value = null
  } finally {
    busy.value = false
  }
}

function removed(post) {
  if (postId.value) return router.push(base.value)
  feed.value = feed.value.filter(p => p.id !== post.id)
}
</script>

<template>
  <div class="mx-auto max-w-2xl space-y-4">
    <!-- One post (from a link or a notification) -->
    <template v-if="postId">
      <RouterLink :to="base" class="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-zinc-500 hover:text-zinc-950">
        <i class="pi pi-arrow-left text-xs" />{{ t('tabs.forum') }}
      </RouterLink>
      <EmptyState v-if="single?.missing" icon="pi pi-comments" :text="t('forum.missing')" />
      <ForumPostCard v-else-if="single" :key="single.id" :post="single" open @edit="edit" @removed="removed(single)" />
    </template>

    <template v-else>
      <!-- Write a post -->
      <form class="card flex gap-3 p-4" @submit.prevent="publish">
        <span :class="[colorFor(userName).bg, colorFor(userName).text]" class="grid size-10 shrink-0 place-items-center rounded-full font-semibold">{{ initials(userName) }}</span>
        <div class="min-w-0 flex-1 space-y-2">
          <Textarea v-model="draft" rows="1" auto-resize maxlength="10000" :aria-label="t('forum.new')"
                    :placeholder="userName ? t('forum.whatsUp', { name: userName.split(' ')[0] }) : t('forum.whatsUpAnon')" class="w-full !rounded-2xl !bg-zinc-100" />
          <div v-if="draft.trim()" class="flex justify-end">
            <Button type="submit" :label="t('forum.publish')" icon="pi pi-send" size="small" :loading="busy" />
          </div>
        </div>
      </form>

      <!-- Search and order -->
      <div class="flex flex-wrap gap-2">
        <IconField class="min-w-48 flex-1">
          <InputIcon class="pi pi-search" />
          <InputText v-model="query" type="search" :placeholder="t('forum.search')" :aria-label="t('forum.search')" class="w-full" />
        </IconField>
        <Select v-model="sort" :options="SORTS" :option-label="s => t(`forum.sort.${s}`)" :aria-label="t('forum.sortBy')" class="w-48" />
      </div>

      <ForumPostCard v-for="p in feed" :key="p.id" :post="p" :link="`${base}/${p.id}`" @edit="edit" @removed="removed(p)" />
      <EmptyState v-if="feed && !feed.length" icon="pi pi-comments" :text="query.trim() ? t('forum.noMatch') : t('forum.empty')" />
    </template>
  </div>

  <Dialog :visible="!!form" modal dismissable-mask :header="t('forum.editPost')" class="w-[34rem] max-w-[calc(100vw-2rem)]" @update:visible="v => !v && (form = null)">
    <form v-if="form" id="forum-form" class="flex flex-col gap-4" @submit.prevent="saveEdit">
      <InputText v-if="form.hadTitle" v-model="form.title" maxlength="200" :aria-label="t('forum.title')" class="!font-semibold" />
      <Textarea v-model="form.body" rows="6" auto-resize maxlength="10000" autofocus :aria-label="t('forum.body')" />
    </form>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="form = null" />
      <Button type="submit" form="forum-form" :label="t('common.save')" icon="pi pi-check" :loading="busy" :disabled="!form?.title?.trim() && !form?.body?.trim()" />
    </template>
  </Dialog>
</template>
