<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import { api } from '../api'
import { auth } from '../auth'
import { cabinId } from '../hytte'
import { t } from '../i18n'

// "Mine hytter": the cabins you belong to and a new cabin of your own. Other people's cabins are never listed
// or searchable: an invitation link is the way into one.
const router = useRouter()
const cabins = ref(null)
const creating = ref(false)
const newName = ref('')
const busy = ref(false)

// Opening the app ("/") goes straight back to the cabin you were in; the menu's "Mine hytter" (?alle) stays here.
let firstVisit = true

onMounted(async () => {
  cabins.value = await api.get('/cabins')
  const resume = cabins.value.find(c => c.id === cabinId.value) || (cabins.value.length === 1 && cabins.value[0])
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

const ROLE = { OWNER: 'access.roles.OWNER', MEMBER: 'access.roles.MEMBER' }
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="text-xl font-semibold">{{ t('cabins.title') }}</h1>
      <Button v-if="auth?.user && cabins?.length" :label="t('cabins.new')" icon="pi pi-plus" @click="creating = true; newName = ''" />
    </div>

    <!-- Your cabins -->
    <div v-if="cabins?.length" class="grid gap-3 sm:grid-cols-2">
      <RouterLink v-for="c in cabins" :key="c.id" :to="`/h/${c.id}`"
                  class="card flex items-center gap-4 p-4 transition hover:-translate-y-0.5 hover:shadow-md">
        <img v-if="c.heroPhotoId" :src="`/api/photos/${c.heroPhotoId}/file?hytte=${c.id}`" alt="" class="size-16 shrink-0 rounded-lg object-cover">
        <span v-else class="grid size-16 shrink-0 place-items-center rounded-lg bg-zinc-100 text-zinc-500"><i class="pi pi-home text-xl" /></span>
        <span class="min-w-0 flex-1">
          <span class="block truncate font-semibold">{{ c.name }}</span>
          <span v-if="c.tagline" class="block truncate text-sm text-zinc-500">{{ c.tagline }}</span>
          <span v-if="c.role" class="mr-1 mt-1 inline-block rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600">{{ t(ROLE[c.role]) }}</span>
        </span>
        <i class="pi pi-chevron-right text-xs text-zinc-400" />
      </RouterLink>
    </div>
    <!-- No cabins yet: making one is the main thing; an invitation link takes you straight into someone else's -->
    <form v-else-if="cabins && auth?.user" class="card flex flex-col items-center gap-4 px-5 py-8 text-center" @submit.prevent="create">
      <span class="grid size-14 place-items-center rounded-2xl bg-emerald-50 text-emerald-700"><i class="pi pi-home text-2xl" /></span>
      <div>
        <h2 class="text-lg font-semibold">{{ t('cabins.firstTitle') }}</h2>
        <p class="mt-1 text-sm text-zinc-500">{{ t('cabins.newHint') }}</p>
      </div>
      <div class="flex w-full max-w-sm flex-col gap-2 sm:flex-row">
        <InputText v-model="newName" maxlength="80" :placeholder="t('cabins.namePlaceholder')" :aria-label="t('cabins.name')" class="min-w-0 flex-1" />
        <Button type="submit" :label="t('cabins.create')" icon="pi pi-plus" :loading="busy" :disabled="!newName.trim()" />
      </div>
      <p class="flex items-start gap-2 text-left text-sm text-zinc-500"><i class="pi pi-link mt-1 text-xs" />{{ t('cabins.inviteHint') }}</p>
    </form>
    <p v-else-if="cabins" class="card p-5 text-zinc-600">{{ t('cabins.none') }}</p>

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
