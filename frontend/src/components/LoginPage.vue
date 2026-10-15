<script setup>
import LoginForm from './LoginForm.vue'
import { auth } from '../auth'
import { t } from '../i18n'
import LanguageToggle from './LanguageToggle.vue'

const failed = new URLSearchParams(location.search).get('login') === 'failed'
</script>

<template>
  <div class="grid min-h-screen place-items-center px-4">
    <div class="w-full max-w-sm text-center">
      <img src="/pwa-192.png" alt="" class="mx-auto size-20 rounded-[1.5rem] shadow-lg">
      <h1 class="mt-5 font-display text-xl uppercase tracking-[0.12em]">Hyttebok</h1>
      <p class="mt-1 text-xs text-zinc-500">{{ t('app.by') }} Ravnsmark</p>
      <p class="mt-6 text-zinc-600">{{ t('login.intro') }}</p>

      <p v-if="failed" class="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t('login.failed') }}</p>

      <LoginForm class="mt-6" />
      <p v-if="auth?.googleEnabled" class="mt-4 text-xs text-zinc-500">{{ t('login.privacy') }}</p>
      <div class="mt-8 flex justify-center"><LanguageToggle /></div>
    </div>
  </div>
</template>
