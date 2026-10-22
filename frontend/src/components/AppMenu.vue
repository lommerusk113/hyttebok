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
const emit = defineEmits(['close', 'notifications', 'feedback'])

// Admin mode changes what the server shows everywhere: reload the page you're on with it (switching it off in
// a cabin you're not a member of shows "no access" there, with the way back to your cabins).
function toggleAdminMode(on) {
  setAdminMode(on)
  location.reload()
}
const toast = useToast()

async function installNow() {
  emit('close')
  if (await install()) toast.add({ severity: 'success', summary: t('install.done'), detail: t('install.openFromHome'), life: 5000 })
}
</script>

<template>
  <!-- Grouped: you · the cabin · settings · the app and your account. Every row: icon, label, value or arrow. -->
  <nav class="flex flex-col text-sm" :aria-label="t('app.menu')">
    <!-- You: display name (and the account you're logged in with) -->
    <button class="flex min-h-16 items-center gap-3 rounded-lg px-2 text-left transition hover:bg-zinc-50" @click="emit('close'); askName = true">
      <img v-if="auth?.user?.picture" :src="auth.user.picture" alt="" referrerpolicy="no-referrer" class="size-10 rounded-full">
      <Avatar v-else :label="initials(userName)" shape="circle" size="large" :class="[colorFor(userName).bg, colorFor(userName).text]" class="!size-10 !text-sm !font-semibold" />
      <span class="min-w-0 flex-1">
        <span class="block truncate font-semibold">{{ userName || t('app.whoAreYou') }}</span>
        <span class="block truncate text-xs text-zinc-500">{{ auth?.user?.email || t('app.changeName') }}</span>
      </span>
      <i class="pi pi-pencil text-xs text-zinc-500" :aria-label="t('app.changeName')" />
    </button>

    <div class="my-2 border-t border-zinc-200" role="separator" />

    <RouterLink v-if="auth?.user" to="/profil" class="row" @click="emit('close')">
      <i class="pi pi-user row-icon" /><span class="flex-1">{{ t('profile.title') }}</span><i class="pi pi-chevron-right text-xs text-zinc-400" />
    </RouterLink>
    <RouterLink v-if="auth?.admin" to="/admin" class="row" @click="emit('close')">
      <i class="pi pi-shield row-icon" /><span class="flex-1">{{ t('admin.title') }}</span><i class="pi pi-chevron-right text-xs text-zinc-400" />
    </RouterLink>

    <div class="my-2 border-t border-zinc-200" role="separator" />

    <!-- The cabin -->
    <RouterLink to="/?alle=1" class="row" @click="emit('close')">
      <i class="pi pi-home row-icon" /><span class="flex-1">{{ t('cabins.title') }}</span><i class="pi pi-chevron-right text-xs text-zinc-400" />
    </RouterLink>
    <button class="row" @click="emit('close'); emit('notifications')">
      <i class="pi pi-bell row-icon" /><span class="flex-1 text-left">{{ t('push.title') }}</span>
      <span class="text-zinc-500">{{ pushTopics.length ? t('push.on') : t('push.off') }}</span><i class="pi pi-chevron-right text-xs text-zinc-400" />
    </button>

    <div class="my-2 border-t border-zinc-200" role="separator" />

    <!-- Settings -->
    <div class="row">
      <i class="pi pi-globe row-icon" /><span class="flex-1">{{ t('app.language') }}</span><LanguageToggle />
    </div>
    <label class="row cursor-pointer">
      <i class="pi pi-moon row-icon" /><span class="flex-1">{{ t('app.darkMode') }}</span><ThemeSwitch />
    </label>
    <!-- System admins only: see every cabin, for troubleshooting -->
    <label v-if="auth?.admin" class="row cursor-pointer">
      <i class="pi pi-shield row-icon" />
      <span class="flex-1">{{ t('cabins.adminMode') }}<span class="block text-xs text-zinc-500">{{ t('cabins.adminModeHint') }}</span></span>
      <ToggleSwitch :model-value="adminMode" @update:model-value="toggleAdminMode" />
    </label>

    <div class="my-2 border-t border-zinc-200" role="separator" />

    <!-- The app and your account. iPhones always go to the instructions: only Safari's Share menu can install there. -->
    <button v-if="installPrompt && !isIos" class="row" @click="installNow">
      <i class="pi pi-download row-icon" /><span class="flex-1 text-left">{{ t('app.install') }}</span>
    </button>
    <RouterLink v-else-if="!installed" to="/app" class="row" @click="emit('close')">
      <i class="pi pi-mobile row-icon" /><span class="flex-1">{{ t('app.install') }}</span><i class="pi pi-chevron-right text-xs text-zinc-400" />
    </RouterLink>
    <button v-if="auth?.user" class="row" @click="emit('close'); emit('feedback')">
      <i class="pi pi-megaphone row-icon" /><span class="flex-1 text-left">{{ t('feedback.title') }}</span>
    </button>
    <RouterLink to="/personvern" class="row" @click="emit('close')">
      <i class="pi pi-lock row-icon" /><span class="flex-1">{{ t('privacy.title') }}</span><i class="pi pi-chevron-right text-xs text-zinc-400" />
    </RouterLink>
    <button v-if="auth?.user" class="row text-rose-700" @click="logout">
      <i class="pi pi-sign-out row-icon !text-rose-600" /><span class="flex-1 text-left">{{ t('login.logout') }}</span>
    </button>
  </nav>
</template>

<style scoped>
@reference "../style.css";

.row {
  @apply flex min-h-12 w-full items-center gap-3 rounded-md px-2 font-medium transition hover:bg-zinc-50;
}

.row-icon {
  @apply w-5 shrink-0 text-center text-zinc-500;
}
</style>
