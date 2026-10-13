<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useDeepLink } from '../deepLink'
import { useConfirm } from 'primevue/useconfirm'
import Avatar from 'primevue/avatar'
import AvatarGroup from 'primevue/avatargroup'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Drawer from 'primevue/drawer'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import EmptyState from '../components/EmptyState.vue'
import RichText from '../components/RichText.vue'
import PhotoLightbox from '../components/PhotoLightbox.vue'
import { api, photoUrl, uploadPhoto } from '../api'
import { askName, userName } from '../user'
import { colorFor, formatDay, initials, parseIsoDate, timeAgo, toIsoDate } from '../utils'
import { t } from '../i18n'

const MAX_GRID = 6

// Lets the photo column next to the feed refresh after a post adds or removes photos.
const emit = defineEmits(['photos-changed'])

const confirm = useConfirm()
const entries = ref([])
const photos = ref([])
const bookings = ref([])
const composing = ref(false)
const posting = ref(false)
const form = ref({})
const newPhotos = ref([])
const existingPhotos = ref([])
const nameInput = ref('')
const lightbox = ref({ photos: [], index: null })

const takeQuery = useDeepLink()
const highlighted = ref(null)

onMounted(async () => {
  [entries.value, photos.value, bookings.value] = await Promise.all(['/log', '/photos', '/bookings'].map(api.get))

  const query = takeQuery()
  if (query.new) {
    compose()
    if (query.stay) pickStay(Number(query.stay))
  }
  if (query.id) {
    highlighted.value = Number(query.id)
    await nextTick()
    document.getElementById(`post-${query.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setTimeout(() => { highlighted.value = null }, 2000)
  }
})

const photosByEntry = computed(() => {
  const map = new Map()
  for (const p of [...photos.value].reverse()) {
    if (p.logEntryId) map.set(p.logEntryId, [...(map.get(p.logEntryId) || []), p])
  }
  return map
})
const photosOf = e => photosByEntry.value.get(e.id) || []
const stays = computed(() => [...bookings.value].sort((a, b) => b.startDate.localeCompare(a.startDate))
  .map(b => ({ ...b, label: `${b.guestName} · ${dateRange(b)}` })))
const stayOf = e => e.bookingId && bookings.value.find(b => b.id === e.bookingId)

// Choosing a stay fills in its dates and adds the guest to "who was there".
function pickStay(id) {
  form.value.bookingId = id
  const stay = bookings.value.find(b => b.id === id)
  if (!stay) return
  form.value.range = [parseIsoDate(stay.startDate), parseIsoDate(stay.endDate)]
  addName(stay.guestName)
}

const canEdit = e => e.author === userName.value || e.participants.includes(userName.value)

// Everyone who has posted, been tagged or booked: one tap to add them.
const knownNames = computed(() => [...new Set([
  userName.value,
  ...entries.value.flatMap(e => [e.author, ...e.participants]),
  ...bookings.value.map(b => b.guestName),
].filter(Boolean))].sort((a, b) => a.localeCompare(b)))
const suggestedNames = computed(() => knownNames.value.filter(n => !form.value.participants.includes(n)))

function dateRange(e) {
  const start = formatDay(parseIsoDate(e.startDate))
  return !e.endDate || e.endDate === e.startDate ? start : `${start} – ${formatDay(parseIsoDate(e.endDate))}`
}

function compose(entry) {

  if (!userName.value) {
    askName.value = true
    return
  }

  newPhotos.value = []
  if (entry) {
    form.value = {
      ...entry,
      content: entry.content || '',
      participants: [...entry.participants],
      range: entry.startDate ? [parseIsoDate(entry.startDate), parseIsoDate(entry.endDate || entry.startDate)] : null,
    }
    existingPhotos.value = photosOf(entry).map(photo => ({ photo, caption: photo.caption || '', removed: false }))
    composing.value = true
    return
  }

  // Pre-fill the dates from the writer's most recent booking that has started.
  const today = toIsoDate(new Date())
  const lastTrip = bookings.value
    .filter(b => b.guestName === userName.value && b.startDate <= today)
    .sort((a, b) => b.startDate.localeCompare(a.startDate))[0]

  form.value = {
    title: '',
    content: '',
    participants: [userName.value],
    bookingId: lastTrip?.id ?? null,
    range: lastTrip ? [parseIsoDate(lastTrip.startDate), parseIsoDate(lastTrip.endDate)] : null,
  }
  existingPhotos.value = []
  composing.value = true
}

function addName(name = nameInput.value) {

  // "ola" becomes "Ola" if that spelling is already in use, and is never added twice.
  const typed = name.trim().toLowerCase()
  const clean = knownNames.value.find(n => n.toLowerCase() === typed) || name.trim()
  if (clean && !form.value.participants.some(n => n.toLowerCase() === typed)) form.value.participants.push(clean)
  nameInput.value = ''
}

// Typed names: space or comma separates, so "Kari Ola Per" becomes three people. Suggested and
// booking names go through addName() directly, so a full name like "Ola Nordmann" stays whole.
function onNameInput() {
  const parts = nameInput.value.split(/[\s,]+/)
  const partial = parts.pop()
  parts.forEach(n => addName(n))
  nameInput.value = partial
}

const removeName = name => { form.value.participants = form.value.participants.filter(n => n !== name) }

function pickFiles(e) {
  newPhotos.value.push(...[...e.target.files].map(file => ({ file, url: URL.createObjectURL(file), caption: '' })))
  e.target.value = ''
}

async function post() {

  addName()
  posting.value = true
  try {
    const { range, ...fields } = form.value
    const [start, end] = range || []
    const body = {
      ...fields,
      author: fields.author || userName.value,
      title: fields.title.trim(),
      content: form.value.content.trim() || null,
      participants: form.value.participants,
      startDate: start ? toIsoDate(start) : null,
      endDate: start ? toIsoDate(end || start) : null,
    }
    const entry = await (body.id ? api.put('/log', body) : api.post('/log', body))

    // Existing photos: removed ones are deleted, changed captions saved.
    const removedIds = new Set()
    for (const p of existingPhotos.value) {
      if (p.removed) {
        await api.del(`/photos/${p.photo.id}`)
        removedIds.add(p.photo.id)
      } else if (p.caption.trim() !== (p.photo.caption || '')) {
        Object.assign(p.photo, await api.put(`/photos/${p.photo.id}/caption`, { caption: p.caption.trim() || null }))
      }
    }

    const uploaded = []
    for (const p of newPhotos.value) {
      uploaded.push(await uploadPhoto(p.file, p.caption.trim(), userName.value, entry.id))
    }

    photos.value = [...uploaded.reverse(), ...photos.value.filter(p => !removedIds.has(p.id))]
    entries.value = body.id ? entries.value.map(e => (e.id === entry.id ? entry : e)) : [entry, ...entries.value]
    composing.value = false
    emit('photos-changed')
  } finally {
    posting.value = false
  }
}

function remove(entry) {
  confirm.require({
    header: t('log.deleteTitle'),
    message: t('log.deleteMessage'),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/log/${entry.id}`)
      entries.value = entries.value.filter(e => e.id !== entry.id)
    },
  })
}
</script>

<template>
  <div class="mb-4 flex items-center justify-between gap-3">
    <h2 class="text-lg font-semibold">{{ t('tabs.posts') }}</h2>
    <Button :label="t('log.new')" icon="pi pi-plus" @click="compose()" />
  </div>

  <div class="space-y-4">
    <article v-for="e in entries" :id="`post-${e.id}`" :key="e.id" class="card overflow-hidden transition-shadow duration-500"
             :class="highlighted === e.id && 'ring-2 ring-zinc-950'">
      <div class="p-5">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <h3 class="text-lg font-semibold leading-snug">{{ e.title || t('log.untitled') }}</h3>
            <div class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-zinc-500">
              <span v-if="e.startDate"><i class="pi pi-calendar mr-1.5 text-xs" />{{ dateRange(e) }}</span>
              <RouterLink v-if="stayOf(e)" to="/booking" class="hover:text-zinc-900"><i class="pi pi-home mr-1.5 text-xs" />{{ t('log.stayOf', { name: stayOf(e).guestName }) }}</RouterLink>
              <span><i class="pi pi-pencil mr-1.5 text-xs" />{{ e.author }} · {{ timeAgo(e.createdAt) }}</span>
            </div>
          </div>
          <div class="flex shrink-0">
            <Button v-if="canEdit(e)" icon="pi pi-pencil" text rounded severity="secondary" size="small" :aria-label="t('common.edit')" @click="compose(e)" />
            <Button v-if="e.author === userName" icon="pi pi-trash" text rounded severity="secondary" size="small" :aria-label="t('common.delete')" @click="remove(e)" />
          </div>
        </div>

        <div v-if="e.participants.length" class="mt-3 flex items-center gap-2.5">
          <AvatarGroup>
            <Avatar v-for="n in e.participants.slice(0, 5)" :key="n" :label="initials(n)" shape="circle" size="small"
                    :class="[colorFor(n).bg, colorFor(n).text]" class="!border-2 !border-surface !text-[10px] !font-semibold" />
          </AvatarGroup>
          <span class="text-sm text-zinc-600">{{ e.participants.join(', ') }}</span>
        </div>

        <RichText v-if="e.content" :text="e.content" class="mt-3 text-zinc-700" />
      </div>

      <!-- Photos: one fills the width, more become a grid with a "+N" tile -->
      <div v-if="photosOf(e).length" class="grid gap-0.5"
           :class="photosOf(e).length === 1 ? '' : [2, 4].includes(photosOf(e).length) ? 'grid-cols-2' : 'grid-cols-3'">
        <button v-for="(p, i) in photosOf(e).slice(0, MAX_GRID)" :key="p.id" class="group relative overflow-hidden bg-zinc-100"
                :class="photosOf(e).length === 1 ? 'max-h-[28rem]' : 'aspect-square'"
                @click="lightbox = { photos: photosOf(e), index: i }">
          <img :src="photoUrl(p.id)" :alt="p.caption || ''" loading="lazy" class="h-full w-full object-cover transition duration-300 group-hover:scale-105">
          <span v-if="i === MAX_GRID - 1 && photosOf(e).length > MAX_GRID"
                class="absolute inset-0 grid place-items-center bg-black/50 text-2xl font-semibold text-white">+{{ photosOf(e).length - MAX_GRID }}</span>
          <span v-else-if="p.caption && photosOf(e).length === 1"
                class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent p-3 pt-8 text-left text-sm text-white">{{ p.caption }}</span>
        </button>
      </div>
    </article>

    <EmptyState v-if="!entries.length" class="card" icon="pi pi-book" :text="t('log.empty')" />
  </div>

  <PhotoLightbox v-model:index="lightbox.index" :photos="lightbox.photos" />

  <Drawer v-model:visible="composing" position="right" :header="form.id ? t('log.edit') : t('log.new')" class="!w-full sm:!w-[34rem]">
    <form id="log-form" class="flex flex-col gap-5" @submit.prevent="post">
      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('log.titleLabel') }}
        <InputText v-model="form.title" required autofocus :placeholder="t('log.titlePlaceholder')" />
      </label>

      <div class="flex flex-col gap-2 text-sm font-medium">
        {{ t('log.who') }}
        <div class="flex flex-wrap items-center gap-1.5 rounded-md border border-zinc-300 p-1.5 focus-within:border-zinc-900">
          <span v-for="n in form.participants" :key="n" :class="[colorFor(n).bg, colorFor(n).text]"
                class="flex items-center gap-1 rounded-full py-1 pl-3 pr-1.5 text-sm">
            {{ n }}
            <button type="button" class="-my-1 grid size-7 place-items-center rounded-full hover:bg-black/10" :aria-label="t('shopping.remove')" @click="removeName(n)">
              <i class="pi pi-times text-[10px]" />
            </button>
          </span>
          <input v-model="nameInput" class="min-w-32 flex-1 bg-transparent px-2 py-1 text-base font-normal outline-none sm:text-sm" :placeholder="t('log.addName')"
                 @input="onNameInput" @keydown.enter.prevent="addName()" @blur="addName()">
        </div>
        <div v-if="suggestedNames.length" class="flex flex-wrap gap-1.5">
          <button v-for="n in suggestedNames" :key="n" type="button"
                  class="rounded-full border border-dashed border-zinc-300 px-2.5 py-0.5 text-xs font-normal text-zinc-600 hover:border-zinc-500"
                  @click="addName(n)">
            + {{ n }}
          </button>
        </div>
      </div>

      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('log.stay') }}
        <Select :model-value="form.bookingId" :options="stays" option-label="label" option-value="id" show-clear
                :placeholder="t('log.stayPlaceholder')" @update:model-value="pickStay" />
      </label>

      <label v-if="!form.bookingId" class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('log.when') }}
        <DatePicker v-model="form.range" selection-mode="range" :manual-input="false" show-icon icon-display="input" :placeholder="t('log.whenPlaceholder')" />
      </label>

      <label class="flex flex-col gap-1.5 text-sm font-medium">
        {{ t('log.what') }}
        <Textarea v-model="form.content" rows="5" auto-resize :placeholder="t('log.placeholder')" />
        <span class="text-xs font-normal text-zinc-500">{{ t('home.bulletHint') }}</span>
      </label>

      <div class="flex flex-col gap-2 text-sm font-medium">
        {{ t('log.photos') }}
        <div v-for="p in existingPhotos" :key="p.photo.id" class="flex items-center gap-3 rounded-md border border-zinc-200 p-2" :class="p.removed && 'opacity-50'">
          <img :src="photoUrl(p.photo.id)" alt="" class="size-16 shrink-0 rounded object-cover" :class="p.removed && 'grayscale'">
          <InputText v-model="p.caption" :disabled="p.removed" :placeholder="t('log.captionPlaceholder')" class="min-w-0 flex-1" />
          <Button :icon="p.removed ? 'pi pi-undo' : 'pi pi-times'" text rounded severity="secondary" size="small"
                  :aria-label="p.removed ? t('suggestions.undo') : t('log.removePhoto')" @click="p.removed = !p.removed" />
        </div>
        <div v-for="(p, i) in newPhotos" :key="p.url" class="flex items-center gap-3 rounded-md border border-zinc-200 p-2">
          <img :src="p.url" alt="" class="size-16 shrink-0 rounded object-cover">
          <InputText v-model="p.caption" :placeholder="t('log.captionPlaceholder')" class="min-w-0 flex-1" />
          <Button icon="pi pi-times" text rounded severity="secondary" size="small" :aria-label="t('log.removePhoto')" @click="newPhotos.splice(i, 1)" />
        </div>
        <label class="flex cursor-pointer items-center justify-center gap-2 rounded-md border-2 border-dashed border-zinc-300 p-4 font-normal text-zinc-600 transition hover:border-zinc-500 hover:bg-zinc-100">
          <i class="pi pi-images" />{{ t('log.addPhotos') }}
          <input type="file" accept="image/*" multiple class="hidden" @change="pickFiles">
        </label>
      </div>
    </form>

    <template #footer>
      <div class="flex justify-end gap-2">
        <Button :label="t('common.cancel')" text severity="secondary" @click="composing = false" />
        <Button type="submit" form="log-form" :label="form.id ? t('common.save') : t('log.publish')" :icon="form.id ? 'pi pi-check' : 'pi pi-send'" :loading="posting" :disabled="!form.title?.trim()" />
      </div>
    </template>
  </Drawer>
</template>
