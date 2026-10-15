<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import Password from 'primevue/password'
import { t } from '../i18n'

// /nytt-passord/<token>: a one-time link from an owner or admin. Works without being logged in.
const MIN_PASSWORD = 10
const token = useRoute().params.token
const info = ref(null)
const password = ref('')
const repeat = ref('')
const error = ref(null)
const done = ref(false)

onMounted(async () => {
  info.value = await (await fetch(`/api/auth/password-reset/${encodeURIComponent(token)}`)).json()
})

async function save() {
  error.value = null
  if (password.value !== repeat.value) {
    error.value = 'login.passwordsDiffer'
    return
  }
  const res = await fetch(`/api/auth/password-reset/${encodeURIComponent(token)}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password: password.value }),
  })
  if (res.ok) done.value = true
  else error.value = res.status === 410 ? 'login.resetInvalid' : 'login.signupFailed'
}
</script>

<template>
  <div class="grid min-h-[70vh] place-items-center">
    <div v-if="info" class="w-full max-w-sm">
      <img src="/pwa-192.png" alt="" class="mx-auto size-16 rounded-[1.25rem] shadow-lg">
      <template v-if="done">
        <h1 class="mt-5 text-center text-xl font-semibold">{{ t('login.passwordSaved') }}</h1>
        <a href="/" class="mt-6 flex min-h-11 items-center justify-center rounded-md bg-zinc-950 font-medium text-surface">{{ t('login.toLogin') }}</a>
      </template>
      <template v-else-if="info.valid">
        <h1 class="mt-5 text-center text-xl font-semibold">{{ t('login.newPassword') }}</h1>
        <p class="mt-1 text-center text-sm text-zinc-500">{{ info.email }}</p>
        <form class="mt-6 flex flex-col gap-3" @submit.prevent="save">
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.password') }}
            <Password v-model="password" :feedback="false" toggle-mask required :minlength="MIN_PASSWORD" :input-props="{ autocomplete: 'new-password' }" class="w-full" input-class="w-full" />
            <span class="text-xs font-normal text-zinc-500">{{ t('login.passwordHint', { n: MIN_PASSWORD }) }}</span>
          </label>
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.repeatPassword') }}
            <Password v-model="repeat" :feedback="false" toggle-mask required :input-props="{ autocomplete: 'new-password' }" class="w-full" input-class="w-full" />
          </label>
          <p v-if="error" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t(error) }}</p>
          <Button type="submit" :label="t('login.savePassword')" />
        </form>
      </template>
      <template v-else>
        <h1 class="mt-5 text-center text-xl font-semibold">{{ t('login.resetInvalid') }}</h1>
        <p class="mt-1 text-center text-zinc-600">{{ t('login.forgotHint') }}</p>
      </template>
    </div>
  </div>
</template>
