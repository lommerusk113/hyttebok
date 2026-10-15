<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import Textarea from 'primevue/textarea'
import { auth, loadAuth, logout } from '../auth'
import { t } from '../i18n'

// Logged in, but not a member of the cabin yet: ask for access (owners and admins approve it on "Tilgang"),
// or, for a system admin, go and give yourself access.
const message = ref('')
const sending = ref(false)

async function grantSelf() {
  sending.value = true
  try {
    await fetch('/api/access/self', { method: 'POST' })
    await loadAuth()
  } finally {
    sending.value = false
  }
}

async function requestAccess() {
  sending.value = true
  try {
    await fetch('/api/access/requests', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: message.value }) })
    await loadAuth()
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="grid min-h-screen place-items-center px-4">
    <div class="w-full max-w-sm text-center">
      <img src="/pwa-192.png" alt="" class="mx-auto size-16 rounded-[1.25rem] shadow-lg">
      <h1 class="mt-5 text-xl font-semibold">{{ t('access.noAccessTitle') }}</h1>
      <p class="mt-1 text-sm text-zinc-500">{{ t('login.signedInAs') }} {{ auth.user.email }}</p>

      <!-- System admin: let yourself in (then invite or approve the others from Tilgang) -->
      <div v-if="auth.admin" class="mt-6 rounded-lg bg-zinc-100 p-4 text-left text-sm">
        <p class="mb-3">{{ t('access.adminHint') }}</p>
        <Button :label="t('access.grantSelf')" icon="pi pi-key" :loading="sending" @click="grantSelf" />
      </div>

      <div v-else-if="auth.requestPending" class="mt-6 rounded-lg bg-emerald-50 p-4 text-sm text-emerald-900" role="status">
        <i class="pi pi-check-circle mr-1" />{{ t('access.requestSent') }}
        <button class="mt-2 block w-full min-h-11 font-medium underline underline-offset-4" @click="loadAuth">{{ t('access.checkAgain') }}</button>
      </div>

      <form v-else class="mt-6 flex flex-col gap-3 text-left" @submit.prevent="requestAccess">
        <p class="text-sm text-zinc-600">{{ t('access.noAccessText') }}</p>
        <Textarea v-model="message" rows="2" auto-resize :placeholder="t('access.messagePlaceholder')" :aria-label="t('access.messagePlaceholder')" />
        <Button type="submit" :label="t('access.request')" icon="pi pi-send" :loading="sending" />
      </form>

      <button class="mt-8 inline-flex min-h-11 items-center gap-2 text-sm text-zinc-500 hover:text-zinc-900" @click="logout"><i class="pi pi-sign-out" />{{ t('login.logout') }}</button>
    </div>
  </div>
</template>
