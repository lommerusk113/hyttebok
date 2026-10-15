<script setup>
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import LanguageToggle from './LanguageToggle.vue'
import ThemeSwitch from './ThemeSwitch.vue'
import { topics as pushTopics } from '../push'
import { userName, askName } from '../user'
import { initials, colorFor } from '../utils'
import { t } from '../i18n'
import { install, installPrompt, installed, isIos } from '../install'
import { auth, logout } from '../auth'
import ToggleSwitch from 'primevue/toggleswitch'
import { adminMode, setAdminMode } from '../hytte'

// Everything personal in one place: the ☰ drawer on phones, the dropdown under your name on wide screens.
const emit = defineEmits(['close', 'notifications'])

// Admin mode changes what the server shows everywhere; start over from "Mine hytter" with it.
function toggleAdminMode(on) {
  setAdminMode(on)
  location.href = '/?alle=1'
}
const toast = useToast()

async function installNow() {
  emit('close')
  if (await install()) toast.add({ severity: 'success', summary: t('install.done'), detail: t('install.openFromHome'), life: 5000 })
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <button class="flex min-h-14 items-center gap-3 rounded-lg border border-zinc-200 p-2 text-left transition hover:bg-zinc-50"
            @click="emit('close'); askName = true">
      <Avatar :label="initials(userName)" shape="circle" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!font-semibold" />
      <span class="min-w-0 flex-1">
        <span class="block truncate font-medium">{{ userName || t('app.whoAreYou') }}</span>
        <span class="block text-xs text-zinc-500">{{ t('app.changeName') }}</span>
      </span>
      <i class="pi pi-chevron-right text-xs text-zinc-500" />
    </button>
    <button class="flex min-h-14 items-center justify-between gap-3 px-2 text-left" @click="emit('close'); emit('notifications')">
      <span class="flex items-center gap-2 text-sm font-medium"><i class="pi pi-bell" />{{ t('push.title') }}</span>
      <span class="flex items-center gap-2 text-sm text-zinc-500">{{ pushTopics.length ? t('push.on') : t('push.off') }}<i class="pi pi-chevron-right text-xs" /></span>
    </button>
    <div class="flex min-h-14 items-center justify-between gap-3 px-2">
      <span class="text-sm font-medium">{{ t('app.language') }}</span>
      <LanguageToggle />
    </div>
    <label class="flex min-h-14 cursor-pointer items-center justify-between gap-3 px-2">
      <span class="text-sm font-medium">{{ t('app.darkMode') }}</span>
      <ThemeSwitch />
    </label>

    <!-- Your cabins: switch, make a new one, or ask for access with a code -->
    <RouterLink to="/?alle=1" class="flex min-h-14 items-center justify-between gap-3 px-2" @click="emit('close')">
      <span class="flex items-center gap-2 text-sm font-medium"><i class="pi pi-home" />{{ t('cabins.title') }}</span>
      <i class="pi pi-chevron-right text-xs text-zinc-500" />
    </RouterLink>

    <!-- System admins only: see every cabin, for troubleshooting -->
    <label v-if="auth?.admin" class="flex min-h-14 cursor-pointer items-center justify-between gap-3 px-2">
      <span class="text-sm">
        <span class="flex items-center gap-2 font-medium"><i class="pi pi-shield" />{{ t('cabins.adminMode') }}</span>
        <span class="block text-xs text-zinc-500">{{ t('cabins.adminModeHint') }}</span>
      </span>
      <ToggleSwitch :model-value="adminMode" @update:model-value="toggleAdminMode" />
    </label>

    <!-- Logged in with Google: whose account, and the way out -->
    <div v-if="auth?.user" class="mt-2 flex items-center gap-3 border-t border-zinc-200 px-2 pt-4">
      <img v-if="auth.user.picture" :src="auth.user.picture" alt="" referrerpolicy="no-referrer" class="size-8 rounded-full">
      <span class="min-w-0 flex-1 truncate text-xs text-zinc-500">{{ t('login.signedInAs') }}<br><span class="text-sm text-zinc-800">{{ auth.user.email }}</span></span>
      <Button :label="t('login.logout')" icon="pi pi-sign-out" size="small" text severity="secondary" @click="logout" />
    </div>

    <!-- iPhones always go to the instructions: only Safari's Share menu can install there -->
    <Button v-if="installPrompt && !isIos" icon="pi pi-download" :label="t('app.install')" class="mt-2" @click="installNow" />
    <RouterLink v-else-if="!installed" to="/app" class="mt-2 flex min-h-11 items-center justify-center gap-2 rounded-lg bg-zinc-100 text-sm font-medium"
                @click="emit('close')">
      <i class="pi pi-mobile" />{{ t('app.install') }}
    </RouterLink>
  </div>
</template>
