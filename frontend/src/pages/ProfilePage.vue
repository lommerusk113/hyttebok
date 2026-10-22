<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Password from 'primevue/password'
import DashCard from '../components/DashCard.vue'
import { api } from '../api'
import { auth, loadAuth } from '../auth'
import { cabinId, setCabin } from '../hytte'
import { colorFor, formatDate, initials, timeAgo } from '../utils'
import { t } from '../i18n'

// "Min profil" (/profil), and the same page for a system admin looking someone up (/admin/brukere/<id>).
// You change your own password with the current one; an admin makes a one-time link or sets a new password.
// Yourself only: leave a cabin, make recovery codes (forgot the password without asking anyone), log out everywhere.
const MIN_PASSWORD = 6
const route = useRoute()
const toast = useToast()
const confirm = useConfirm()
const codes = ref(null) // new recovery codes, shown once
const userId = computed(() => route.params.userId)
const asAdmin = computed(() => !!userId.value)
const profile = ref(null)
const form = ref({ current: '', password: '', repeat: '' })
const error = ref(null)
const busy = ref(false)

async function load() {
  profile.value = await api.get(asAdmin.value ? `/admin/users/${userId.value}` : '/profile')
}
watch(userId, load, { immediate: true })

async function savePassword() {
  error.value = null
  const { current, password, repeat } = form.value
  if (password.length < MIN_PASSWORD) return (error.value = 'login.errors.passwordShort')
  if (password !== repeat) return (error.value = 'login.passwordsDiffer')
  busy.value = true
  try {
    // Straight to the server, not through the offline queue: a password change must succeed or say why.
    const res = await fetch(asAdmin.value ? `/api/admin/users/${userId.value}/password` : '/api/profile/password', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(asAdmin.value ? { password } : { current: current || null, password }),
    })
    if (!res.ok) return (error.value = res.status === 422 ? 'profile.wrongPassword' : 'common.tryAgain')
    // A new password logs out every device, this one too: log straight back in with it.
    if (!asAdmin.value) await relogin(password)
    form.value = { current: '', password: '', repeat: '' }
    toast.add({ severity: 'success', summary: t('login.passwordSaved'), life: 3000 })
    await load()
  } catch {
    error.value = 'login.offline'
  } finally {
    busy.value = false
  }
}

async function relogin(password) {
  await fetch('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: profile.value.email, password }),
  })
  await loadAuth()
}

function leave(cabin) {
  confirm.require({
    header: t('profile.leaveTitle'),
    message: t('profile.leaveMessage', { name: cabin.name }),
    acceptProps: { label: t('profile.leave'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      const res = await fetch(`/api/profile/cabins/${cabin.id}`, { method: 'DELETE' })
      if (res.status === 409) return toast.add({ severity: 'warn', summary: t('profile.lastOwner'), life: 6000 })
      if (!res.ok) return toast.add({ severity: 'error', summary: t('common.tryAgain'), life: 4000 })
      if (String(cabinId.value) === String(cabin.id)) setCabin(null)
      toast.add({ severity: 'success', summary: t('profile.left', { name: cabin.name }), life: 3000 })
      await load()
    },
  })
}

async function newCodes() {
  codes.value = await api.post('/profile/recovery-codes')
  await load()
}

async function copyCodes() {
  try {
    await navigator.clipboard.writeText(codes.value.join('\n'))
    toast.add({ severity: 'success', summary: t('profile.codesCopied'), life: 2500 })
  } catch { /* no clipboard: they're on screen to write down */ }
}

function logOutEverywhere() {
  confirm.require({
    header: t('profile.everywhereTitle'),
    message: t('profile.everywhereMessage'),
    acceptProps: { label: t('profile.everywhere'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.post('/profile/logout-everywhere')
      location.href = '/'
    },
  })
}

// No email service: the admin sends the link themselves (chat, SMS).
async function resetLink() {
  const reset = await api.post(`/admin/users/${userId.value}/password-reset`)
  const url = `${location.origin}/nytt-passord/${reset.token}`
  if (navigator.share) {
    try {
      await navigator.share({ title: t('access.resetShareTitle', { name: profile.value.name }), url })
      return
    } catch { /* cancelled: copy instead */ }
  }
  try {
    await navigator.clipboard.writeText(url)
    toast.add({ severity: 'success', summary: t('access.linkCopied'), life: 2500 })
  } catch {
    toast.add({ severity: 'info', summary: t('access.copyLink'), detail: url, life: 15000 })
  }
}

const ROLE = { OWNER: 'access.roles.OWNER', MEMBER: 'access.roles.MEMBER' }
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <p v-if="asAdmin && !auth?.admin" class="rounded-lg bg-amber-50 p-4 text-sm text-amber-800">{{ t('admin.notAllowed') }}</p>

    <template v-else-if="profile">
      <RouterLink v-if="asAdmin" to="/admin/brukere" class="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-zinc-600 underline-offset-4 hover:underline">
        <i class="pi pi-arrow-left text-xs" />{{ t('admin.users') }}
      </RouterLink>

      <!-- Who -->
      <div class="card flex flex-wrap items-center gap-4 p-5">
        <img v-if="profile.picture" :src="profile.picture" alt="" referrerpolicy="no-referrer" class="size-16 rounded-full">
        <Avatar v-else :label="initials(profile.name)" shape="circle" :class="[colorFor(profile.name).bg, colorFor(profile.name).text]" class="!size-16 !text-xl !font-semibold" />
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-xl font-semibold">{{ profile.name }}</h1>
          <p class="truncate text-sm text-zinc-500">{{ profile.email }}</p>
          <p class="mt-2 flex flex-wrap gap-1.5 text-xs">
            <span v-if="profile.admin" class="rounded-full bg-zinc-100 px-2 py-0.5 text-zinc-600">{{ t('access.admin') }}</span>
            <span v-if="profile.google" class="rounded-full bg-sky-50 px-2 py-0.5 text-sky-700">Google</span>
            <span v-if="profile.password" class="rounded-full bg-violet-50 px-2 py-0.5 text-violet-700">{{ t('admin.passwordBadge') }}</span>
          </p>
        </div>
        <dl class="grid grid-cols-2 gap-x-6 gap-y-1 text-xs text-zinc-500 sm:block sm:space-y-1 sm:text-right">
          <div><dt class="inline">{{ t('profile.since') }}</dt> <dd class="inline font-medium text-zinc-700">{{ formatDate(profile.createdAt) }}</dd></div>
          <div><dt class="inline">{{ t('admin.lastLogin') }}</dt> <dd class="inline font-medium text-zinc-700">{{ timeAgo(profile.lastLoginAt) }}</dd></div>
        </dl>
      </div>

      <!-- Cabins -->
      <DashCard :title="asAdmin ? t('admin.cabins') : t('cabins.title')" icon="pi pi-home" tint="bg-emerald-50 text-emerald-700" :count="profile.cabins.length">
        <ul v-if="profile.cabins.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
          <li v-for="c in profile.cabins" :key="c.id" class="flex items-center gap-3 px-5 py-2.5">
            <span class="min-w-0 flex-1 truncate font-medium">{{ c.name }}</span>
            <span v-if="c.deleted" class="rounded-full bg-rose-50 px-2 py-0.5 text-xs text-rose-700">{{ t('cabins.deleted') }}</span>
            <span class="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-600">{{ t(ROLE[c.role]) }}</span>
            <Button v-if="!asAdmin" icon="pi pi-sign-out" text rounded severity="secondary" size="small" :aria-label="t('profile.leaveIn', { name: c.name })"
                    v-tooltip.left="t('profile.leave')" @click="leave(c)" />
            <RouterLink v-if="!asAdmin" :to="`/h/${c.id}`" class="grid size-9 place-items-center rounded-full hover:bg-zinc-100" :aria-label="c.name">
              <i class="pi pi-chevron-right text-xs text-zinc-400" />
            </RouterLink>
          </li>
        </ul>
        <p v-else class="py-1 text-sm text-zinc-500">{{ t('profile.noCabins') }}</p>
      </DashCard>

      <!-- Password -->
      <DashCard :title="t('login.password')" icon="pi pi-key" tint="bg-violet-50 text-violet-700">
        <div v-if="asAdmin" class="mb-4 flex flex-wrap items-center gap-3 border-b border-zinc-100 pb-4">
          <p class="min-w-0 flex-1 text-sm text-zinc-500">{{ t('profile.resetLinkHint') }}</p>
          <Button :label="t('access.resetLink')" icon="pi pi-link" size="small" outlined @click="resetLink" />
        </div>
        <p class="mb-3 text-sm text-zinc-500">
          {{ asAdmin ? t('profile.setHint') : profile.password ? t('profile.changeHint') : t('profile.addHint') }}
        </p>
        <form class="flex max-w-sm flex-col gap-3" novalidate @submit.prevent="savePassword">
          <label v-if="!asAdmin && profile.password" class="flex flex-col gap-1.5 text-sm font-medium">{{ t('profile.current') }}
            <Password v-model="form.current" :feedback="false" toggle-mask :input-props="{ autocomplete: 'current-password' }" class="w-full" input-class="w-full" />
          </label>
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('profile.newPassword') }}
            <Password v-model="form.password" :feedback="false" toggle-mask :input-props="{ autocomplete: 'new-password' }" class="w-full" input-class="w-full" />
            <span class="text-xs font-normal text-zinc-500">{{ t('login.passwordHint', { n: MIN_PASSWORD }) }}</span>
          </label>
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('login.repeatPassword') }}
            <Password v-model="form.repeat" :feedback="false" toggle-mask :input-props="{ autocomplete: 'new-password' }" class="w-full" input-class="w-full" />
          </label>
          <p v-if="error" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t(error, { n: MIN_PASSWORD }) }}</p>
          <Button type="submit" :label="t('login.savePassword')" :loading="busy" class="self-start" />
        </form>
      </DashCard>

      <!-- Yourself only: getting back in without anyone's help, and ending sessions on other devices -->
      <DashCard v-if="!asAdmin" :title="t('profile.security')" icon="pi pi-shield" tint="bg-emerald-50 text-emerald-700">
        <div class="flex flex-wrap items-center gap-3">
          <p class="min-w-0 flex-1 basis-60 text-sm text-zinc-500">
            {{ t('profile.codesHint') }}
            <span class="mt-1 block font-medium text-zinc-700">{{ t('profile.codesLeft', { n: profile.recoveryCodes }, profile.recoveryCodes) }}</span>
          </p>
          <Button :label="profile.recoveryCodes ? t('profile.newCodes') : t('profile.makeCodes')" icon="pi pi-key" size="small" outlined @click="newCodes" />
        </div>
        <div v-if="codes" class="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4">
          <p class="text-sm font-medium text-amber-900">{{ t('profile.codesOnce') }}</p>
          <ul class="mt-3 grid grid-cols-2 gap-x-6 gap-y-1 font-mono text-sm tabular-nums text-zinc-900">
            <li v-for="c in codes" :key="c">{{ c }}</li>
          </ul>
          <div class="mt-3 flex gap-2">
            <Button :label="t('common.copy')" icon="pi pi-copy" size="small" severity="secondary" outlined @click="copyCodes" />
            <Button :label="t('profile.codesSaved')" size="small" text severity="secondary" @click="codes = null" />
          </div>
        </div>
        <div class="mt-4 flex flex-wrap items-center gap-3 border-t border-zinc-100 pt-4">
          <p class="min-w-0 flex-1 basis-60 text-sm text-zinc-500">{{ t('profile.everywhereHint') }}</p>
          <Button :label="t('profile.everywhere')" icon="pi pi-sign-out" size="small" severity="danger" outlined @click="logOutEverywhere" />
        </div>
      </DashCard>
    </template>
  </div>
</template>
