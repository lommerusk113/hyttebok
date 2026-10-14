<script setup>
import Button from 'primevue/button'
import { install, installPrompt, installed, installedBefore, isIos, markInstallPageSeen } from '../install'
import { onMounted } from 'vue'
import { t } from '../i18n'

// Shareable install page (/app). Android/desktop Chrome get a real install button;
// iOS doesn't let websites install themselves, so iPhone users get step-by-step instructions.
onMounted(markInstallPageSeen)

async function installNow() {
  if (await install()) installedBefore.value = true
}

const IOS_STEPS = [
  { icon: 'pi pi-upload', key: 'install.iosStep1' },
  { icon: 'pi pi-plus-circle', key: 'install.iosStep2' },
  { icon: 'pi pi-check', key: 'install.iosStep3' },
]
</script>

<template>
  <div class="mx-auto max-w-md">
    <RouterLink to="/" class="inline-flex min-h-11 items-center gap-2 text-sm font-medium"><i class="pi pi-arrow-left text-xs" />{{ t('install.back') }}</RouterLink>
  </div>
  <div class="mx-auto flex max-w-md flex-col items-center gap-6 py-6 text-center">
    <img src="/pwa-192.png" alt="" class="size-24 rounded-[1.75rem] shadow-lg">
    <div>
      <h1 class="font-display text-xl uppercase tracking-[0.12em]">Hyttebok</h1>
      <p class="mt-1 text-xs text-zinc-500">{{ t('app.by') }} Ravnsmark</p>
      <p class="mt-1 text-zinc-500">{{ t('install.subtitle') }}</p>
    </div>

    <ul class="grid w-full gap-2 text-left text-sm">
      <li v-for="key in ['install.benefitHome', 'install.benefitOffline', 'install.benefitSync']" :key="key"
          class="flex items-center gap-3 rounded-lg bg-surface px-4 py-3 shadow-sm">
        <i class="pi pi-check-circle text-emerald-600" />{{ t(key) }}
      </li>
    </ul>

    <!-- Running as the installed app -->
    <div v-if="installed" class="card w-full p-5">
      <p class="font-semibold"><i class="pi pi-check-circle mr-2 text-emerald-600" />{{ t('install.done') }}</p>
      <RouterLink to="/" class="mt-3 inline-flex min-h-11 items-center font-medium underline underline-offset-4">{{ t('install.open') }}</RouterLink>
    </div>

    <!-- Android / desktop Chrome & Edge: one tap -->
    <Button v-else-if="installPrompt && !isIos" :label="t('app.install')" icon="pi pi-download" size="large" class="w-full !py-4 !text-base" @click="installNow" />

    <!-- Installed, but this visit is in the browser -->
    <div v-else-if="installedBefore" class="card w-full p-5">
      <p class="font-semibold"><i class="pi pi-check-circle mr-2 text-emerald-600" />{{ t('install.done') }}</p>
      <p class="mt-1 text-sm text-zinc-600">{{ t('install.openFromHome') }}</p>
      <RouterLink to="/" class="mt-3 inline-flex min-h-11 items-center text-sm font-medium underline underline-offset-4">{{ t('install.continueInBrowser') }}</RouterLink>
    </div>

    <!-- iPhone / iPad: Apple only allows installing from the Share menu -->
    <div v-else-if="isIos" class="card w-full p-5 text-left">
      <p class="mb-4 font-semibold">{{ t('install.iosTitle') }}</p>
      <ol class="space-y-4">
        <li v-for="(step, i) in IOS_STEPS" :key="step.key" class="flex items-center gap-4">
          <span class="grid size-11 shrink-0 place-items-center rounded-xl bg-zinc-100 text-lg text-sky-600"><i :class="step.icon" /></span>
          <span><span class="mr-1 font-semibold">{{ i + 1 }}.</span>{{ t(step.key) }}</span>
        </li>
      </ol>
      <p class="mt-4 text-xs text-zinc-500">{{ t('install.iosNote') }}</p>
    </div>

    <!-- Browsers without an install prompt (Firefox, prompt already dismissed, …) -->
    <div v-else class="card w-full p-5 text-left text-sm text-zinc-600">
      <p class="mb-1 font-semibold text-zinc-900">{{ t('install.otherTitle') }}</p>
      <p>{{ t('install.otherText') }}</p>
    </div>
  </div>
</template>
