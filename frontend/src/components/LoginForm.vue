<script setup>
import { ref } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import SelectButton from 'primevue/selectbutton'
import { auth, login } from '../auth'
import { t } from '../i18n'

// Log in or sign up with email + password, or with Google (when it's set up). Used on the login page and on
// invitation links. A new account starts without access, like a first Google login.
const MIN_PASSWORD = 10
const mode = ref('login')
const form = ref({ name: '', email: '', password: '' })
const error = ref(null)
const busy = ref(false)
const showForgot = ref(false)

async function passwordLogin() {
  // The backend answers with a redirect: to "/" when it worked, to "/?login=failed" when not.
  const res = await fetch('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: form.value.email.trim(), password: form.value.password }),
  })
  return res.ok && !res.url.includes('login=failed')
}

async function submit() {

  error.value = null
  busy.value = true
  try {
    if (mode.value === 'signup') {
      const res = await fetch('/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: form.value.name.trim(), email: form.value.email.trim(), password: form.value.password }),
      })
      if (!res.ok) {
        error.value = res.status === 409 ? 'login.emailTaken' : 'login.signupFailed'
        return
      }
    }
    if (!(await passwordLogin())) {
      error.value = 'login.wrongPassword'
      return
    }
    // Same page again, now logged in (an invitation waiting in this browser is accepted on the way).
    location.replace(location.pathname + location.search)
  } catch {
    error.value = 'login.offline'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="text-left">
    <template v-if="auth?.googleEnabled">
      <!-- Google's own button style: white, their "G", their wording -->
      <button type="button" class="flex min-h-12 w-full items-center justify-center gap-3 rounded-full border border-zinc-300 bg-white px-5 text-[15px] font-medium text-[#1f1f1f] shadow-sm transition hover:bg-zinc-50"
              @click="login">
        <svg class="size-5" viewBox="0 0 48 48" aria-hidden="true">
          <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
          <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
          <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
          <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
        </svg>
        {{ t('login.google') }}
      </button>
      <div class="my-5 flex items-center gap-3 text-xs text-zinc-500"><span class="h-px flex-1 bg-zinc-200" />{{ t('login.or') }}<span class="h-px flex-1 bg-zinc-200" /></div>
    </template>

    <SelectButton v-model="mode" :options="['login', 'signup']" :allow-empty="false" class="mb-4 w-full [&>*]:flex-1" @change="error = null">
      <template #option="{ option }">{{ t(`login.${option}`) }}</template>
    </SelectButton>

    <form class="flex flex-col gap-3" @submit.prevent="submit">
      <label v-if="mode === 'signup'" class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.name') }}
        <InputText v-model="form.name" required autocomplete="name" maxlength="80" />
      </label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.email') }}
        <InputText v-model="form.email" type="email" required autocomplete="email" inputmode="email" />
      </label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.password') }}
        <Password v-model="form.password" :feedback="false" toggle-mask required :minlength="mode === 'signup' ? MIN_PASSWORD : undefined"
                  :input-props="{ autocomplete: mode === 'signup' ? 'new-password' : 'current-password' }" class="w-full" input-class="w-full" />
        <span v-if="mode === 'signup'" class="text-xs font-normal text-zinc-500">{{ t('login.passwordHint', { n: MIN_PASSWORD }) }}</span>
      </label>

      <p v-if="error" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t(error) }}</p>

      <Button type="submit" :label="mode === 'signup' ? t('login.createAccount') : t('login.login')" :loading="busy" class="mt-1" />
      <template v-if="mode === 'login'">
        <button type="button" class="min-h-11 text-sm text-zinc-500 underline-offset-4 hover:underline" @click="showForgot = !showForgot">{{ t('login.forgot') }}</button>
        <p v-if="showForgot" class="text-center text-sm text-zinc-600">{{ t('login.forgotHint') }}</p>
      </template>
    </form>
  </div>
</template>
