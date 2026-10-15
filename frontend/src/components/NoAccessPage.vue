<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import { auth, loadAuth } from '../auth'
import { cabinHeaders } from '../hytte'
import { t } from '../i18n'

// In a cabin you're not a member of (an old link, or access was removed). Ordinary users go back to their
// own cabins (or ask with the cabin's code there); a system admin can give themselves access.
const busy = ref(false)

async function grantSelf() {
  busy.value = true
  try {
    await fetch('/api/access/self', { method: 'POST', headers: cabinHeaders() })
    await loadAuth()
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="grid min-h-[60vh] place-items-center">
    <div class="w-full max-w-sm text-center">
      <span class="mx-auto grid size-16 place-items-center rounded-2xl bg-zinc-100 text-zinc-500"><i class="pi pi-lock text-2xl" /></span>
      <h1 class="mt-5 text-xl font-semibold">{{ t('access.noAccessTitle') }}</h1>
      <p class="mt-1 text-sm text-zinc-500">{{ t('access.noAccessText') }}</p>

      <div v-if="auth.admin" class="mt-6 rounded-lg bg-zinc-100 p-4 text-left text-sm">
        <p class="mb-3">{{ t('access.adminHint') }}</p>
        <Button :label="t('access.grantSelf')" icon="pi pi-key" :loading="busy" @click="grantSelf" />
      </div>

      <RouterLink to="/?alle=1" class="mt-6 inline-flex min-h-11 items-center gap-2 font-medium underline-offset-4 hover:underline">
        <i class="pi pi-arrow-left text-xs" />{{ t('cabins.title') }}
      </RouterLink>
    </div>
  </div>
</template>
