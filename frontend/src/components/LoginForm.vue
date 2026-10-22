<script setup>
import { ref, watch } from 'vue'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import SelectButton from 'primevue/selectbutton'
import { auth, login } from '../auth'
import { locale, t } from '../i18n'

// Log in or sign up with email + password, or with Google (when it's set up). Used on the login page and on
// invitation links. A new account starts without access, like a first Google login.
// What's wrong is shown under the field it's about, checked here first and again by the server.
const MIN_PASSWORD = 6
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// `initialMode`: 'signup' to open on "Lag konto" (the landing page's "Kom i gang").
const props = defineProps({ initialMode: { type: String, default: 'login' } })
const mode = ref(props.initialMode)
const form = ref({ name: '', email: '', password: '' })
const fieldErrors = ref({}) // name / email / password -> text key
const error = ref(null) // about the whole form (wrong password, no connection)
const busy = ref(false)
const showForgot = ref(false)
const code = ref('')
const codeError = ref(null)
const emailSent = ref(false)

// Forgot the password, with email set up on the server: a one-time link to the address typed above.
async function emailLink() {
  codeError.value = null
  const email = form.value.email.trim()
  if (!EMAIL.test(email)) {
    codeError.value = 'login.errors.emailInvalid'
    return
  }
  try {
    const res = await fetch('/api/auth/forgot', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, lang: locale.value }) })
    if (!res.ok) throw new Error(res.status)
    emailSent.value = true
  } catch {
    codeError.value = 'login.offline'
  }
}

// Forgot the password: a recovery code saved on "Min profil" opens the one-time new-password page.
async function recover() {

  codeError.value = null
  if (!form.value.email.trim() || !code.value.trim()) {
    codeError.value = 'login.codeMissing'
    return
  }
  try {
    const res = await fetch('/api/auth/recover', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: form.value.email.trim(), code: code.value.trim() }),
    })
    if (!res.ok) {
      codeError.value = 'login.codeWrong'
      return
    }
    location.href = `/nytt-passord/${(await res.json()).token}`
  } catch {
    codeError.value = 'login.offline'
  }
}

// Typing in a field clears its message; switching between log in and sign up clears all.
watch(() => ({ ...form.value }), (now, before) => {
  for (const field of Object.keys(now)) if (now[field] !== before[field]) delete fieldErrors.value[field]
})
watch(mode, () => { fieldErrors.value = {}; error.value = null })

function check() {
  const errors = {}
  const { name, email, password } = form.value
  if (mode.value === 'signup' && !name.trim()) errors.name = 'login.errors.nameMissing'
  if (!email.trim()) errors.email = 'login.errors.emailMissing'
  else if (!EMAIL.test(email.trim())) errors.email = 'login.errors.emailInvalid'
  if (!password) errors.password = 'login.errors.passwordMissing'
  else if (mode.value === 'signup' && password.length < MIN_PASSWORD) errors.password = 'login.errors.passwordShort'
  fieldErrors.value = errors
  return !Object.keys(errors).length
}

// The server names the field ("body.password: size must be ..."); show it in our words under that field.
const SERVER_FIELD = { name: 'login.errors.nameMissing', email: 'login.errors.emailInvalid', password: 'login.errors.passwordShort' }
async function serverErrors(res) {
  try {
    const errors = {}
    for (const e of (await res.json())._embedded.errors) {
      const field = e.message.match(/^body\.(\w+):/)?.[1]
      if (SERVER_FIELD[field]) errors[field] = SERVER_FIELD[field]
    }
    if (Object.keys(errors).length) return (fieldErrors.value = errors)
  } catch { /* not the usual error body */ }
  error.value = 'login.signupFailed'
}

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
  if (!check()) return
  busy.value = true
  try {
    if (mode.value === 'signup') {
      const res = await fetch('/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: form.value.name.trim(), email: form.value.email.trim(), password: form.value.password }),
      })
      if (res.status === 409) {
        fieldErrors.value = { email: 'login.errors.emailTaken' }
        return
      }
      if (!res.ok) return serverErrors(res)
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

    <!-- novalidate: our own messages in the app's language instead of the browser's pop-ups -->
    <form class="flex flex-col gap-3" novalidate @submit.prevent="submit">
      <label v-if="mode === 'signup'" class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.name') }}
        <InputText v-model="form.name" autocomplete="name" maxlength="80" :invalid="!!fieldErrors.name" aria-describedby="login-name-error" />
        <span v-if="fieldErrors.name" id="login-name-error" class="text-xs font-normal text-rose-700" role="alert">{{ t(fieldErrors.name) }}</span>
      </label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.email') }}
        <InputText v-model="form.email" type="email" autocomplete="email" inputmode="email" :invalid="!!fieldErrors.email" aria-describedby="login-email-error" />
        <span v-if="fieldErrors.email" id="login-email-error" class="text-xs font-normal text-rose-700" role="alert">{{ t(fieldErrors.email) }}</span>
      </label>
      <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.password') }}
        <Password v-model="form.password" :feedback="false" toggle-mask :invalid="!!fieldErrors.password"
                  :input-props="{ autocomplete: mode === 'signup' ? 'new-password' : 'current-password', 'aria-describedby': 'login-password-note' }" class="w-full" input-class="w-full" />
        <span v-if="fieldErrors.password" id="login-password-note" class="text-xs font-normal text-rose-700" role="alert">{{ t(fieldErrors.password, { n: MIN_PASSWORD }) }}</span>
        <span v-else-if="mode === 'signup'" id="login-password-note" class="text-xs font-normal text-zinc-500">{{ t('login.passwordHint', { n: MIN_PASSWORD }) }}</span>
      </label>

      <p v-if="error" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t(error) }}</p>

      <Button type="submit" :label="mode === 'signup' ? t('login.createAccount') : t('login.login')" :loading="busy" class="mt-1" />
      <p v-if="mode === 'signup'" class="text-center text-xs text-zinc-500">
        {{ t('login.termsBefore') }} <a href="/personvern" target="_blank" class="underline underline-offset-2 hover:text-zinc-900">{{ t('login.termsLink') }}</a>.
      </p>
      <template v-if="mode === 'login'">
        <button type="button" class="min-h-11 text-sm text-zinc-500 underline-offset-4 hover:underline" @click="showForgot = !showForgot">{{ t('login.forgot') }}</button>
        <div v-if="showForgot" class="flex flex-col gap-3 rounded-lg bg-zinc-50 p-4">
          <template v-if="auth?.emailEnabled">
            <p v-if="emailSent" class="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800" role="status">{{ t('login.emailSent', { email: form.email.trim() }) }}</p>
            <template v-else>
              <p class="text-sm text-zinc-600">{{ t('login.emailHint') }}</p>
              <Button type="button" :label="t('login.emailLink')" icon="pi pi-envelope" @click="emailLink" />
            </template>
            <p class="border-t border-zinc-200 pt-3 text-xs font-medium uppercase tracking-wider text-zinc-500">{{ t('login.orCode') }}</p>
          </template>
          <p class="text-sm text-zinc-600">{{ t('login.forgotHint') }}</p>
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.recoveryCode') }}
            <InputText v-model="code" autocomplete="one-time-code" autocapitalize="off" spellcheck="false" class="font-mono" :invalid="!!codeError" @keydown.enter.prevent="recover" />
            <span class="text-xs font-normal text-zinc-500">{{ t('login.recoveryHint') }}</span>
          </label>
          <p v-if="codeError" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t(codeError) }}</p>
          <Button type="button" :label="t('login.useCode')" severity="secondary" outlined @click="recover" />
        </div>
      </template>
    </form>
  </div>
</template>
