<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import RichText from '../components/RichText.vue'
import { api } from '../api'
import { auth } from '../auth'
import { askName, userName } from '../user'
import { colorFor, formatDate, initials, timeAgo } from '../utils'
import { t } from '../i18n'

// The cabin's forum: a searchable list of posts (/h/1/forum) and one post with its replies (/h/1/forum/12).
const route = useRoute()
const router = useRouter()
const confirm = useConfirm()

const SORTS = ['active', 'new', 'old', 'replies']
const sort = ref('active')
const query = ref('')
const list = ref(null)
const thread = ref(null)
const reply = ref('')
const formOpen = ref(false)
const form = ref({})
const busy = ref(false)

const postId = computed(() => route.params.sub)
const base = computed(() => `/h/${route.params.cabinId}/forum`)

// Your own posts and replies can be deleted (owners can delete anything).
const mine = item => auth.value?.canManage || (item.author && item.author === userName.value)

async function loadList() {
  const params = new URLSearchParams({ sort: sort.value, ...(query.value.trim() && { q: query.value.trim() }) })
  list.value = await api.get(`/forum?${params}`)
}

async function loadThread() {
  thread.value = null
  thread.value = await api.get(`/forum/${postId.value}`).catch(() => ({ missing: true }))
}

// Typing searches after a short pause instead of on every key.
let timer
watch(query, () => { clearTimeout(timer); timer = setTimeout(loadList, 300) })
watch(sort, loadList)
watch(postId, id => (id ? loadThread() : loadList()), { immediate: true })

function needName() {
  if (userName.value) return false
  askName.value = true
  return true
}

function openForm() {
  if (needName()) return
  form.value = { title: '', body: '' }
  formOpen.value = true
}

async function create() {
  busy.value = true
  try {
    const post = await api.post('/forum', { ...form.value, author: userName.value })
    formOpen.value = false
    router.push(`${base.value}/${post.id}`)
  } finally {
    busy.value = false
  }
}

async function sendReply() {
  if (!reply.value.trim() || needName()) return
  busy.value = true
  try {
    const saved = await api.post(`/forum/${postId.value}/replies`, { body: reply.value, author: userName.value })
    thread.value.replies.push(saved)
    reply.value = ''
  } finally {
    busy.value = false
  }
}

function ask(message, accept) {
  confirm.require({
    header: t('forum.deleteTitle'),
    message,
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept,
  })
}

const removePost = () => ask(thread.value.post.title, async () => {
  await api.del(`/forum/${thread.value.post.id}`)
  router.push(base.value)
})

const removeReply = r => ask(r.body.slice(0, 80), async () => {
  await api.del(`/forum/replies/${r.id}`)
  thread.value.replies = thread.value.replies.filter(x => x.id !== r.id)
})
</script>

<template>
  <!-- One post with its replies -->
  <div v-if="postId" class="mx-auto max-w-3xl space-y-4">
    <RouterLink :to="base" class="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-zinc-500 hover:text-zinc-950">
      <i class="pi pi-arrow-left text-xs" />{{ t('tabs.forum') }}
    </RouterLink>

    <EmptyState v-if="thread?.missing" icon="pi pi-comments" :text="t('forum.missing')" />
    <template v-else-if="thread">
      <article class="card p-5">
        <div class="flex items-start justify-between gap-3">
          <h1 class="text-xl font-semibold">{{ thread.post.title }}</h1>
          <Button v-if="mine(thread.post)" icon="pi pi-trash" text rounded severity="secondary" :aria-label="t('common.delete')" @click="removePost" />
        </div>
        <p class="mt-1 text-sm text-zinc-500">{{ t('forum.by', { name: thread.post.author || '?', date: formatDate(thread.post.createdAt) }) }}</p>
        <RichText v-if="thread.post.body" :text="thread.post.body" class="mt-4" />
      </article>

      <h2 class="px-1 pt-2 text-sm font-medium text-zinc-500">{{ t('forum.replyCount', { n: thread.replies.length }, thread.replies.length) }}</h2>
      <ul class="space-y-3">
        <li v-for="r in thread.replies" :key="r.id" class="card flex gap-3 p-4">
          <span :class="[colorFor(r.author).bg, colorFor(r.author).text]" class="grid size-9 shrink-0 place-items-center rounded-full text-sm font-semibold">{{ initials(r.author) }}</span>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="font-medium">{{ r.author || '?' }}</span>
              <span class="text-xs text-zinc-500" :title="formatDate(r.createdAt)">{{ timeAgo(r.createdAt) }}</span>
              <span class="flex-1" />
              <Button v-if="mine(r)" icon="pi pi-trash" text rounded size="small" severity="secondary" :aria-label="t('common.delete')" @click="removeReply(r)" />
            </div>
            <RichText :text="r.body" class="mt-1" />
          </div>
        </li>
      </ul>

      <form class="card space-y-3 p-4" @submit.prevent="sendReply">
        <Textarea v-model="reply" rows="3" auto-resize maxlength="10000" :placeholder="t('forum.replyPlaceholder')" :aria-label="t('forum.reply')" class="w-full" />
        <div class="flex justify-end">
          <Button type="submit" :label="t('forum.reply')" icon="pi pi-send" :loading="busy" :disabled="!reply.trim()" />
        </div>
      </form>
    </template>
  </div>

  <!-- All posts -->
  <DashCard v-else :title="t('tabs.forum')" icon="pi pi-comments" tint="bg-sky-50 text-sky-700" :count="list?.length">
    <template #action>
      <Button icon="pi pi-plus" :label="t('forum.new')" size="small" @click="openForm" />
    </template>

    <div class="mb-4 flex flex-wrap gap-2">
      <IconField class="min-w-48 flex-1">
        <InputIcon class="pi pi-search" />
        <InputText v-model="query" type="search" :placeholder="t('forum.search')" :aria-label="t('forum.search')" class="w-full" />
      </IconField>
      <Select v-model="sort" :options="SORTS" :option-label="s => t(`forum.sort.${s}`)" :aria-label="t('forum.sortBy')" class="w-44" />
    </div>

    <ul v-if="list?.length" class="-mx-5 divide-y divide-zinc-200">
      <li v-for="s in list" :key="s.post.id">
        <RouterLink :to="`${base}/${s.post.id}`" class="flex gap-4 px-5 py-3 transition hover:bg-zinc-50">
          <div class="min-w-0 flex-1">
            <p class="truncate font-medium">{{ s.post.title }}</p>
            <p v-if="s.post.body" class="line-clamp-1 text-sm text-zinc-600">{{ s.post.body }}</p>
            <p class="mt-0.5 text-xs text-zinc-500">
              {{ t('forum.by', { name: s.post.author || '?', date: formatDate(s.post.createdAt) }) }}
              <template v-if="s.lastReplyAt"> · {{ t('forum.lastReply', { name: s.lastReplyBy || '?', time: timeAgo(s.lastReplyAt) }) }}</template>
            </p>
          </div>
          <span class="flex shrink-0 items-center gap-1.5 self-center text-sm" :class="s.replies ? 'text-zinc-700' : 'text-zinc-400'"
                :aria-label="t('forum.replyCount', { n: s.replies }, s.replies)">
            <i class="pi pi-comment text-xs" />{{ s.replies }}
          </span>
        </RouterLink>
      </li>
    </ul>
    <EmptyState v-else-if="list" icon="pi pi-comments" :text="query.trim() ? t('forum.noMatch') : t('forum.empty')" />
  </DashCard>

  <Dialog v-model:visible="formOpen" modal dismissable-mask :header="t('forum.new')" class="w-[34rem] max-w-[calc(100vw-2rem)]">
    <form id="forum-form" class="flex flex-col gap-4" @submit.prevent="create">
      <InputText v-model="form.title" required maxlength="200" autofocus :placeholder="t('forum.titlePlaceholder')" :aria-label="t('forum.title')" class="!font-semibold" />
      <Textarea v-model="form.body" rows="6" auto-resize maxlength="10000" :placeholder="t('forum.bodyPlaceholder')" :aria-label="t('forum.body')" />
    </form>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
      <Button type="submit" form="forum-form" :label="t('forum.publish')" icon="pi pi-send" :loading="busy" :disabled="!form.title?.trim()" />
    </template>
  </Dialog>
</template>
