<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { api } from '../api'
import { auth } from '../auth'
import { adminMode, cabinId } from '../hytte'
import { t } from '../i18n'

// "Mine hytter": the cabins you belong to, a new cabin of your own, and asking for access with a cabin's
// join code. Other people's cabins are never listed or searchable (admins in admin mode excepted).
const router = useRouter()
const cabins = ref(null)
const waiting = ref([])
const creating = ref(false)
const newName = ref('')
const code = ref('')
const message = ref('')
const result = ref(null) // { status, cabinName } or { error }
const busy = ref(false)

// Opening the app ("/") goes straight back to the cabin you were in; the menu's "Mine hytter" (?alle) stays here.
let firstVisit = true

onMounted(async () => {
  ;[cabins.value, waiting.value] = await Promise.all([api.get('/cabins'), auth.value?.user ? api.get('/cabins/waiting') : []])
  const resume = cabins.value.find(c => c.id === cabinId.value) || (cabins.value.length === 1 && !adminMode.value && cabins.value[0])
  // Only if still here: after a login the app may already have gone on to where you were heading.
  const stillHere = router.currentRoute.value.path === '/'
  if (firstVisit && stillHere && !router.currentRoute.value.query.alle && resume) router.replace(`/h/${resume.id}`)
  firstVisit = false
})

async function create() {
  if (!newName.value.trim()) return
  busy.value = true
  try {
    const cabin = await api.post('/cabins', { name: newName.value.trim() })
    creating.value = false
    router.push(`/h/${cabin.id}`)
  } finally {
    busy.value = false
  }
}

async function join() {
  result.value = null
  busy.value = true
  try {
    const res = await fetch('/api/cabins/join', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code: code.value, message: message.value }),
    })
    if (!res.ok) {
      result.value = { error: res.status === 404 ? 'cabins.codeUnknown' : res.status === 429 ? 'cabins.tooManyCodes' : 'common.tryAgain' }
      return
    }
    const joined = await res.json()
    if (joined.status === 'MEMBER') return router.push(`/h/${joined.cabinId}`)
    result.value = joined
    code.value = ''
    message.value = ''
    waiting.value = await api.get('/cabins/waiting')
  } finally {
    busy.value = false
  }
}

const ROLE = { OWNER: 'access.roles.OWNER', MEMBER: 'access.roles.MEMBER' }
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-xl font-semibold">{{ t('cabins.title') }}</h1>
      <Button v-if="auth?.user" :label="t('cabins.new')" icon="pi pi-plus" @click="creating = true; newName = ''" />
    </div>

    <p v-if="adminMode" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800"><i class="pi pi-shield mr-1" />{{ t('cabins.adminModeOn') }}</p>

    <!-- Your cabins -->
    <div v-if="cabins?.length" class="grid gap-3 sm:grid-cols-2">
      <RouterLink v-for="c in cabins" :key="c.id" :to="`/h/${c.id}`"
                  class="card flex items-center gap-4 p-4 transition hover:-translate-y-0.5 hover:shadow-md">
        <img v-if="c.heroPhotoId" :src="`/api/photos/${c.heroPhotoId}/file?hytte=${c.id}`" alt="" class="size-16 shrink-0 rounded-lg object-cover">
        <span v-else class="grid size-16 shrink-0 place-items-center rounded-lg bg-zinc-100 text-zinc-500"><i class="pi pi-home text-xl" /></span>
        <span class="min-w-0 flex-1">
          <span class="block truncate font-semibold">{{ c.name }}</span>
          <span v-if="c.tagline" class="block truncate text-sm text-zinc-500">{{ c.tagline }}</span>
          <span v-if="c.role" class="mt-1 inline-block rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600">{{ t(ROLE[c.role]) }}</span>
        </span>
        <i class="pi pi-chevron-right text-xs text-zinc-400" />
      </RouterLink>
    </div>
    <p v-else-if="cabins" class="card p-5 text-zinc-600">{{ t('cabins.none') }}</p>

    <!-- Asked with a code, no answer yet -->
    <div v-if="waiting.length" class="rounded-lg bg-zinc-100 p-4 text-sm" role="status">
      <p v-for="w in waiting" :key="w.cabinName"><i class="pi pi-clock mr-1.5 text-zinc-500" />{{ t('cabins.waitingFor', { name: w.cabinName }) }}</p>
    </div>

    <!-- Someone else's cabin: the owners share its code -->
    <form v-if="auth?.user" class="card space-y-3 p-5" @submit.prevent="join">
      <div>
        <h2 class="font-semibold">{{ t('cabins.haveCode') }}</h2>
        <p class="text-sm text-zinc-500">{{ t('cabins.codeHint') }}</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <InputText v-model="code" required maxlength="20" :placeholder="t('cabins.codePlaceholder')" :aria-label="t('cabins.code')"
                   class="w-44 font-mono uppercase tracking-wider" autocapitalize="characters" autocomplete="off" />
        <Button type="submit" :label="t('cabins.ask')" icon="pi pi-send" :loading="busy" :disabled="!code.trim()" />
      </div>
      <Textarea v-model="message" rows="1" auto-resize maxlength="500" :placeholder="t('access.messagePlaceholder')" :aria-label="t('access.messagePlaceholder')" class="w-full" />
      <p v-if="result?.error" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t(result.error) }}</p>
      <p v-else-if="result" class="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900" role="status">
        <i class="pi pi-check-circle mr-1" />{{ t(result.status === 'PENDING' ? 'cabins.alreadyAsked' : 'cabins.asked', { name: result.cabinName }) }}
      </p>
    </form>

    <Dialog v-model:visible="creating" modal dismissable-mask :header="t('cabins.new')" class="w-[26rem] max-w-[calc(100vw-2rem)]">
      <form id="new-cabin" @submit.prevent="create">
        <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('cabins.name') }}
          <InputText v-model="newName" required autofocus maxlength="80" :placeholder="t('cabins.namePlaceholder')" />
        </label>
        <p class="mt-2 text-xs text-zinc-500">{{ t('cabins.newHint') }}</p>
      </form>
      <template #footer>
        <Button :label="t('common.cancel')" text severity="secondary" @click="creating = false" />
        <Button type="submit" form="new-cabin" :label="t('cabins.create')" icon="pi pi-check" :loading="busy" :disabled="!newName.trim()" />
      </template>
    </Dialog>
  </div>
</template>
