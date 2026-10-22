<script setup>
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import LanguageToggle from './LanguageToggle.vue'
import LoginForm from './LoginForm.vue'
import { t } from '../i18n'

// The front page for visitors who aren't logged in: what Hyttebok is, with screenshots from a test cabin
// (public/landing). "Kom i gang" opens sign-up, "Logg inn" the login form; both in a dialog over the page.
const formMode = ref(null) // 'signup' | 'login' | null
const params = new URLSearchParams(location.search)
const failed = params.get('login') === 'failed'
if (failed) formMode.value = 'login'
if (params.has('signup')) formMode.value = 'signup' // "Lag din egen hytte" from the example cabin
if (params.has('logginn')) formMode.value = 'login'

// "Se en eksempelhytte" only when a system admin has picked one.
const example = ref(null)
onMounted(async () => {
  const res = await fetch('/api/cabins/example').catch(() => null)
  if (res?.status === 200) example.value = await res.json()
})

const FEATURES = [
  { key: 'booking', icon: 'pi pi-calendar', tint: 'bg-sky-50 text-sky-700' },
  { key: 'stay', icon: 'pi pi-sign-in', tint: 'bg-emerald-50 text-emerald-700' },
  { key: 'logbook', icon: 'pi pi-book', tint: 'bg-violet-50 text-violet-700' },
  { key: 'shopping', icon: 'pi pi-shopping-cart', tint: 'bg-amber-50 text-amber-700' },
  { key: 'maintenance', icon: 'pi pi-wrench', tint: 'bg-zinc-100 text-zinc-700' },
  { key: 'expenses', icon: 'pi pi-wallet', tint: 'bg-rose-50 text-rose-700' },
  { key: 'forum', icon: 'pi pi-comments', tint: 'bg-indigo-50 text-indigo-700' },
  { key: 'offline', icon: 'pi pi-wifi', tint: 'bg-teal-50 text-teal-700' },
]

// Screenshots with the text beside them; every other one swaps sides on wide screens.
const SPOTLIGHTS = [
  { key: 'stay', image: '/landing/phone-checkout.jpg', phone: true },
  { key: 'calendar', image: '/landing/desktop-booking.jpg' },
  { key: 'expenses', image: '/landing/phone-expenses.jpg', phone: true },
  { key: 'offline', image: '/landing/phone-shopping.jpg', phone: true },
]
</script>

<template>
  <div class="min-h-screen bg-page text-zinc-950">
    <header class="sticky top-0 z-30 border-b border-zinc-200 bg-surface/85 backdrop-blur">
      <div class="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:h-16">
        <a href="/" class="flex min-h-11 items-center gap-2.5">
          <img src="/mark.png" alt="" class="h-8 w-auto invert dark:invert-0">
          <span class="flex flex-col gap-1">
            <span class="font-display text-base uppercase leading-none tracking-[0.18em]">Hyttebok</span>
            <span class="flex items-center gap-1 text-[10px] text-zinc-500">{{ t('app.by') }} <img src="/wordmark.png" alt="Ravnsmark" class="h-2 w-auto opacity-70 invert dark:invert-0"></span>
          </span>
        </a>
        <div class="flex items-center gap-2 sm:gap-3">
          <LanguageToggle class="hidden sm:flex" />
          <Button :label="t('landing.login')" text severity="secondary" @click="formMode = 'login'" />
          <Button :label="t('landing.start')" class="hidden sm:inline-flex" @click="formMode = 'signup'" />
        </div>
      </div>
    </header>

    <main>
      <!-- Hero: the pitch, and the app itself on a laptop and a phone -->
      <section class="mx-auto max-w-6xl px-4 pb-16 pt-12 sm:pt-20">
        <div class="mx-auto max-w-3xl text-center">
          <p class="text-sm font-semibold uppercase tracking-[0.2em] text-emerald-700">{{ t('landing.eyebrow') }}</p>
          <h1 class="mt-4 text-balance text-4xl font-semibold tracking-tight sm:text-6xl">{{ t('landing.title') }}</h1>
          <p class="mx-auto mt-5 max-w-2xl text-pretty text-lg text-zinc-600 sm:text-xl">{{ t('landing.subtitle') }}</p>
          <div class="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Button :label="t('landing.startLong')" icon="pi pi-arrow-right" icon-pos="right" size="large" class="w-full sm:w-auto" @click="formMode = 'signup'" />
            <RouterLink v-if="example" to="/eksempel" class="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-md border border-zinc-300 px-5 font-medium transition hover:border-zinc-400 hover:bg-surface sm:w-auto">
              <i class="pi pi-eye text-sm" />{{ t('landing.example') }}
            </RouterLink>
            <Button v-else :label="t('landing.login')" size="large" severity="secondary" outlined class="w-full sm:w-auto" @click="formMode = 'login'" />
          </div>
        </div>

        <div class="relative mx-auto mt-14 max-w-5xl sm:mt-20">
          <!-- Laptop-ish browser frame (hidden on phones, where the phone shot alone is clearer) -->
          <div class="hidden overflow-hidden rounded-xl border border-zinc-200 bg-surface shadow-2xl sm:block">
            <div class="flex items-center gap-1.5 border-b border-zinc-200 px-4 py-2.5">
              <span class="size-2.5 rounded-full bg-zinc-200" /><span class="size-2.5 rounded-full bg-zinc-200" /><span class="size-2.5 rounded-full bg-zinc-200" />
            </div>
            <img src="/landing/desktop-overview.jpg" width="1600" height="1000" :alt="t('landing.alt.desktop')" class="block w-full">
          </div>
          <div class="mx-auto w-64 sm:absolute sm:-bottom-10 sm:-right-4 sm:w-56 lg:-right-10 lg:w-64">
            <div class="overflow-hidden rounded-[2.25rem] border-[6px] border-zinc-900 bg-zinc-900 shadow-2xl">
              <img src="/landing/phone-overview.jpg" width="640" height="1385" :alt="t('landing.alt.phone')" class="block w-full rounded-[1.75rem]">
            </div>
          </div>
        </div>
      </section>

      <!-- Everything in one place -->
      <section class="border-y border-zinc-200 bg-surface py-20">
        <div class="mx-auto max-w-6xl px-4">
          <h2 class="text-balance text-center text-3xl font-semibold tracking-tight sm:text-4xl">{{ t('landing.featuresTitle') }}</h2>
          <p class="mx-auto mt-3 max-w-2xl text-center text-zinc-600">{{ t('landing.featuresText') }}</p>
          <ul class="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <li v-for="f in FEATURES" :key="f.key" class="rounded-xl border border-zinc-200 bg-page/40 p-5">
              <span class="grid size-10 place-items-center rounded-lg" :class="f.tint"><i :class="f.icon" /></span>
              <h3 class="mt-4 font-semibold">{{ t(`landing.features.${f.key}.title`) }}</h3>
              <p class="mt-1.5 text-sm leading-relaxed text-zinc-600">{{ t(`landing.features.${f.key}.text`) }}</p>
            </li>
          </ul>
        </div>
      </section>

      <!-- A closer look -->
      <section class="mx-auto max-w-6xl space-y-24 px-4 py-24">
        <div v-for="(s, i) in SPOTLIGHTS" :key="s.key" class="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
          <div :class="i % 2 && 'lg:order-2'">
            <p class="text-sm font-semibold uppercase tracking-[0.2em] text-zinc-500">{{ t(`landing.spotlights.${s.key}.eyebrow`) }}</p>
            <h2 class="mt-3 text-balance text-3xl font-semibold tracking-tight">{{ t(`landing.spotlights.${s.key}.title`) }}</h2>
            <p class="mt-4 text-pretty text-lg text-zinc-600">{{ t(`landing.spotlights.${s.key}.text`) }}</p>
            <ul class="mt-6 space-y-3">
              <li v-for="n in 3" :key="n" class="flex gap-3">
                <i class="pi pi-check-circle mt-1 text-emerald-600" />
                <span>{{ t(`landing.spotlights.${s.key}.points.${n - 1}`) }}</span>
              </li>
            </ul>
          </div>
          <div class="flex justify-center" :class="i % 2 && 'lg:order-1'">
            <div v-if="s.phone" class="w-64 overflow-hidden rounded-[2.25rem] border-[6px] border-zinc-900 bg-zinc-900 shadow-xl sm:w-72">
              <img :src="s.image" width="640" height="1385" loading="lazy" :alt="t(`landing.spotlights.${s.key}.title`)" class="block w-full rounded-[1.75rem]">
            </div>
            <div v-else class="overflow-hidden rounded-xl border border-zinc-200 bg-surface shadow-xl">
              <img :src="s.image" width="1600" height="1000" loading="lazy" :alt="t(`landing.spotlights.${s.key}.title`)" class="block w-full">
            </div>
          </div>
        </div>
      </section>

      <!-- Getting started -->
      <section class="border-t border-zinc-200 bg-surface py-20">
        <div class="mx-auto max-w-5xl px-4">
          <h2 class="text-center text-3xl font-semibold tracking-tight sm:text-4xl">{{ t('landing.stepsTitle') }}</h2>
          <ol class="mt-12 grid gap-8 sm:grid-cols-3">
            <li v-for="n in 3" :key="n" class="text-center">
              <span class="mx-auto grid size-12 place-items-center rounded-full bg-zinc-950 font-display text-lg text-surface">{{ n }}</span>
              <h3 class="mt-4 font-semibold">{{ t(`landing.steps.${n - 1}.title`) }}</h3>
              <p class="mt-1.5 text-sm text-zinc-600">{{ t(`landing.steps.${n - 1}.text`) }}</p>
            </li>
          </ol>
        </div>
      </section>

      <!-- Last call, over the cover photo of the test cabin -->
      <section class="relative isolate overflow-hidden">
        <img src="/landing/phone-overview.jpg" alt="" aria-hidden="true" class="absolute inset-0 -z-10 h-full w-full scale-110 object-cover object-top blur-2xl brightness-50">
        <div class="mx-auto max-w-3xl px-4 py-24 text-center text-white">
          <h2 class="text-balance text-3xl font-semibold tracking-tight sm:text-5xl">{{ t('landing.ctaTitle') }}</h2>
          <p class="mx-auto mt-4 max-w-xl text-lg text-white/80">{{ t('landing.ctaText') }}</p>
          <div class="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Button :label="t('landing.startLong')" icon="pi pi-arrow-right" icon-pos="right" size="large" severity="contrast"
                    class="!bg-white !text-zinc-950 hover:!bg-zinc-100" @click="formMode = 'signup'" />
            <RouterLink v-if="example" to="/eksempel" class="inline-flex min-h-12 items-center gap-2 rounded-md px-5 font-medium text-white/90 underline-offset-4 hover:underline">
              <i class="pi pi-eye text-sm" />{{ t('landing.example') }}
            </RouterLink>
          </div>
        </div>
      </section>
    </main>

    <footer class="border-t border-zinc-200 bg-surface">
      <div class="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 text-sm text-zinc-500 sm:flex-row">
        <span class="flex items-center gap-2"><img src="/mark.png" alt="" class="h-5 w-auto invert dark:invert-0">Hyttebok {{ t('app.by') }} Ravnsmark</span>
        <span class="flex items-center gap-4">
          <RouterLink to="/personvern" class="underline-offset-4 hover:text-zinc-950 hover:underline">{{ t('privacy.title') }}</RouterLink>
          <RouterLink to="/app" class="underline-offset-4 hover:text-zinc-950 hover:underline">{{ t('app.install') }}</RouterLink>
          <button type="button" class="underline-offset-4 hover:text-zinc-950 hover:underline" @click="formMode = 'login'">{{ t('landing.login') }}</button>
          <LanguageToggle />
        </span>
      </div>
    </footer>

    <Dialog :visible="!!formMode" modal dismissable-mask :header="formMode === 'signup' ? t('landing.signupTitle') : t('landing.loginTitle')"
            class="w-[26rem] max-w-[calc(100vw-2rem)]" @update:visible="formMode = $event ? formMode : null">
      <p v-if="failed && formMode === 'login'" class="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t('login.failed') }}</p>
      <LoginForm :key="formMode" :initial-mode="formMode" />
    </Dialog>
  </div>
</template>
