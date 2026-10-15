<script setup>
import { computed, nextTick, ref } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Textarea from 'primevue/textarea'
import ReactionButton, { REACTIONS } from './ReactionButton.vue'
import RichText from './RichText.vue'
import { api } from '../api'
import { auth } from '../auth'
import { askName, userName } from '../user'
import { colorFor, formatDate, initials, timeAgo } from '../utils'
import { localeTag, t } from '../i18n'

// One post in the forum feed: text, reactions, and its comments (folded away behind a count until opened;
// opened, the newest few show and the rest wait behind "Vis tidligere").
const SHOWN = 3

const props = defineProps({ post: Object, open: Boolean, link: String })
const emit = defineEmits(['edit', 'removed'])
const confirm = useConfirm()

const showComments = ref(props.open)
const showAll = ref(false)
const draft = ref('')
const editing = ref(null) // { id, body } of the comment being edited
const busy = ref(false)
const input = ref()

const comments = computed(() => props.post.comments)
const hidden = computed(() => (showAll.value ? 0 : Math.max(0, comments.value.length - SHOWN)))
const visible = computed(() => comments.value.slice(hidden.value))

// Your own posts and comments can be edited and deleted; owners can also delete anyone's (but not edit their words).
const own = item => !!item.author && item.author === userName.value
const canDelete = item => auth.value?.canManage || own(item)

const mineOf = reactions => reactions.find(r => r.author === userName.value)?.emoji
const names = list => new Intl.ListFormat(localeTag(), { type: 'conjunction' }).format(list)
// The most used reactions first, as Facebook shows them: "👍❤️ 5", with who in the tooltip.
const summary = reactions => ({
  top: Object.entries(reactions.reduce((n, r) => ({ ...n, [r.emoji]: (n[r.emoji] || 0) + 1 }), {})).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([emoji]) => emoji),
  who: names(reactions.map(r => `${r.author} ${r.emoji}`)),
})

function needName() {
  if (userName.value) return false
  askName.value = true
  return true
}

async function react(target, url, emoji) {
  if (needName()) return
  target.reactions = await api.put(url, { emoji, author: userName.value })
}

async function openComments() {
  showComments.value = true
  await nextTick()
  input.value?.$el.focus()
}

async function send() {
  if (!draft.value.trim() || needName()) return
  busy.value = true
  try {
    const saved = await api.post(`/forum/${props.post.id}/replies`, { body: draft.value, author: userName.value })
    props.post.comments.push({ ...saved, reactions: [] })
    draft.value = ''
  } finally {
    busy.value = false
  }
}

// Enter sends (like a chat); Shift+Enter makes a new line.
const enterSends = e => { if (!e.shiftKey && !e.isComposing) { e.preventDefault(); send() } }

async function saveComment() {
  if (!editing.value.body.trim()) return
  busy.value = true
  try {
    const saved = await api.put(`/forum/replies/${editing.value.id}`, { body: editing.value.body })
    Object.assign(props.post.comments.find(c => c.id === saved.id), { body: saved.body, editedAt: saved.editedAt })
    editing.value = null
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

const removePost = () => ask((props.post.title || props.post.body || '').slice(0, 80), async () => {
  await api.del(`/forum/${props.post.id}`)
  emit('removed')
})

const removeComment = c => ask(c.body.slice(0, 80), async () => {
  await api.del(`/forum/replies/${c.id}`)
  props.post.comments = props.post.comments.filter(x => x.id !== c.id)
})
</script>

<template>
  <article class="card">
    <!-- Who and when -->
    <header class="flex items-center gap-3 px-4 pt-4">
      <span :class="[colorFor(post.author).bg, colorFor(post.author).text]" class="grid size-10 shrink-0 place-items-center rounded-full font-semibold">{{ initials(post.author) }}</span>
      <div class="min-w-0 flex-1">
        <p class="truncate font-semibold">{{ post.author || '?' }}</p>
        <p class="text-xs text-zinc-500">
          <RouterLink v-if="link" :to="link" class="hover:underline" :title="formatDate(post.createdAt)">{{ timeAgo(post.createdAt) }}</RouterLink>
          <span v-else :title="formatDate(post.createdAt)">{{ timeAgo(post.createdAt) }}</span>
          <span v-if="post.editedAt" :title="formatDate(post.editedAt)"> · {{ t('forum.edited') }}</span>
        </p>
      </div>
      <Button v-if="own(post)" icon="pi pi-pencil" text rounded severity="secondary" :aria-label="t('common.edit')" @click="emit('edit', post)" />
      <Button v-if="canDelete(post)" icon="pi pi-trash" text rounded severity="secondary" :aria-label="t('common.delete')" @click="removePost" />
    </header>

    <div class="px-4 pt-3">
      <h2 v-if="post.title" class="mb-1 text-lg font-semibold">{{ post.title }}</h2>
      <RichText v-if="post.body" :text="post.body" />
    </div>

    <!-- 👍❤️ 5 ··· 3 kommentarer -->
    <div v-if="post.reactions.length || comments.length" class="mx-4 mt-3 flex items-center gap-2 text-sm text-zinc-500">
      <span v-if="post.reactions.length" class="flex items-center gap-1.5" :title="summary(post.reactions).who">
        <span class="flex -space-x-1" aria-hidden="true">
          <span v-for="e in summary(post.reactions).top" :key="e" class="grid size-5 place-items-center rounded-full bg-surface text-sm ring-2 ring-surface">{{ e }}</span>
        </span>
        <span :aria-label="t('forum.reactedBy', { names: summary(post.reactions).who })">{{ post.reactions.length }}</span>
      </span>
      <span class="flex-1" />
      <button v-if="comments.length" type="button" class="min-h-8 hover:underline" :aria-expanded="showComments" @click="showComments = !showComments">
        {{ t('forum.commentCount', { n: comments.length }, comments.length) }}
      </button>
    </div>

    <!-- Liker · Kommenter -->
    <div class="mx-4 mt-2 flex gap-1 border-t border-zinc-200 py-1">
      <ReactionButton :mine="mineOf(post.reactions)" @pick="e => react(post, `/forum/${post.id}/reaction`, e)" />
      <button type="button" class="flex min-h-10 flex-1 items-center justify-center gap-1.5 rounded-md px-3 text-sm font-medium text-zinc-600 transition hover:bg-zinc-100"
              @click="openComments">
        <i class="pi pi-comment" />{{ t('forum.comment') }}
      </button>
    </div>

    <!-- Comments -->
    <div v-if="showComments" class="space-y-3 border-t border-zinc-200 px-4 py-3">
      <button v-if="hidden" type="button" class="text-sm font-medium text-zinc-600 hover:underline" @click="showAll = true">
        {{ t('forum.showEarlier', { n: hidden }, hidden) }}
      </button>

      <div v-for="c in visible" :key="c.id" class="flex gap-2">
        <span :class="[colorFor(c.author).bg, colorFor(c.author).text]" class="grid size-8 shrink-0 place-items-center rounded-full text-xs font-semibold">{{ initials(c.author) }}</span>
        <div class="min-w-0 flex-1">
          <form v-if="editing?.id === c.id" class="space-y-2" @submit.prevent="saveComment">
            <Textarea v-model="editing.body" rows="2" auto-resize maxlength="10000" autofocus :aria-label="t('forum.comment')" class="w-full" />
            <div class="flex justify-end gap-2">
              <Button :label="t('common.cancel')" text severity="secondary" size="small" @click="editing = null" />
              <Button type="submit" :label="t('common.save')" icon="pi pi-check" size="small" :loading="busy" :disabled="!editing.body.trim()" />
            </div>
          </form>
          <template v-else>
            <div class="inline-block max-w-full rounded-2xl bg-zinc-100 px-3 py-2">
              <p class="text-sm font-semibold">{{ c.author || '?' }}</p>
              <RichText :text="c.body" class="text-sm" />
            </div>
            <div class="flex flex-wrap items-center gap-x-3 px-3 text-xs text-zinc-500">
              <span :title="formatDate(c.createdAt)">{{ timeAgo(c.createdAt) }}<template v-if="c.editedAt"> · {{ t('forum.edited') }}</template></span>
              <ReactionButton small :mine="mineOf(c.reactions)" @pick="e => react(c, `/forum/replies/${c.id}/reaction`, e)" />
              <button v-if="own(c)" type="button" class="min-h-8 font-medium hover:underline" @click="editing = { id: c.id, body: c.body }">{{ t('common.edit') }}</button>
              <button v-if="canDelete(c)" type="button" class="min-h-8 font-medium hover:underline" @click="removeComment(c)">{{ t('common.delete') }}</button>
              <span v-if="c.reactions.length" :title="summary(c.reactions).who" class="flex items-center gap-0.5">
                <span aria-hidden="true">{{ summary(c.reactions).top.join('') }}</span>
                <span :aria-label="t('forum.reactedBy', { names: summary(c.reactions).who })">{{ c.reactions.length }}</span>
              </span>
            </div>
          </template>
        </div>
      </div>

      <!-- Write a comment -->
      <form class="flex items-end gap-2" @submit.prevent="send">
        <span :class="[colorFor(userName).bg, colorFor(userName).text]" class="grid size-8 shrink-0 place-items-center rounded-full text-xs font-semibold">{{ initials(userName) }}</span>
        <Textarea ref="input" v-model="draft" rows="1" auto-resize maxlength="10000" :placeholder="t('forum.commentPlaceholder')" :aria-label="t('forum.comment')"
                  class="min-w-0 flex-1 !rounded-2xl !bg-zinc-100 !py-2" @keydown.enter="enterSends" />
        <Button type="submit" icon="pi pi-send" rounded text :aria-label="t('forum.send')" :loading="busy" :disabled="!draft.trim()" />
      </form>
    </div>
  </article>
</template>
