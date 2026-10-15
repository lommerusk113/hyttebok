<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { acceptInvite, auth, loadAuth, rememberInvite } from '../auth'
import LoginForm from '../components/LoginForm.vue'
import { t } from '../i18n'

// /invitasjon/<token>: says which cabin and who invited you. Logged in = joins straight away;
// otherwise the token waits in the browser through the login (Google or password) and is accepted after.
const route = useRoute()
const router = useRouter()
const token = route.params.token
const info = ref(null)
const failed = ref(false)

onMounted(async () => {
  info.value = await (await fetch(`/api/access/invites/${encodeURIComponent(token)}/info`)).json()
  if (!info.value.valid) return
  // Not logged in yet: whichever way they log in (Google or password), the invitation waits in this browser.
  if (!auth.value?.user) return rememberInvite(token)
  // Logged in: join (or, if already a member, just go there) and open the invitation's cabin.
  const joined = await acceptInvite(token)
  if (!joined) return (failed.value = true)
  await loadAuth()
  router.replace(`/h/${joined}`)
})

</script>

<template>
  <div class="grid min-h-[70vh] place-items-center">
    <div v-if="info" class="w-full max-w-sm text-center">
      <img src="/pwa-192.png" alt="" class="mx-auto size-16 rounded-[1.25rem] shadow-lg">
      <template v-if="info.valid && !failed">
        <h1 class="mt-5 text-xl font-semibold">{{ t('access.invitedTo', { cabin: info.cabinName }) }}</h1>
        <p v-if="info.invitedBy" class="mt-1 text-zinc-600">{{ t('access.invitedBy', { name: info.invitedBy }) }}</p>
        <LoginForm v-if="!auth?.user" class="mt-6" />
        <p v-else class="mt-6 text-sm text-zinc-500"><i class="pi pi-spin pi-spinner mr-2" />{{ t('access.joining') }}</p>
      </template>
      <template v-else>
        <h1 class="mt-5 text-xl font-semibold">{{ t('access.inviteInvalid') }}</h1>
        <p class="mt-1 text-zinc-600">{{ t('access.inviteInvalidHint') }}</p>
        <RouterLink to="/?alle=1" class="mt-6 inline-flex min-h-11 items-center font-medium underline underline-offset-4">{{ t('access.toStart') }}</RouterLink>
      </template>
    </div>
  </div>
</template>
