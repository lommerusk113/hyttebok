<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import Button from 'primevue/button'
import ToggleSwitch from 'primevue/toggleswitch'
import { needsInstall, permission, refreshPermission, sendTest, setTopics, supported, TOPICS, topics } from '../push'
import { askName, userName } from '../user'
import { locale, t } from '../i18n'

const busy = ref(false)
const failed = ref(null) // 'pushService' when the browser can't reach its push server (e.g. Brave), else 'generic'
const testSent = ref(false)

// The panel mounts each time it's opened; also re-check when coming back from the phone's settings.
onMounted(() => { refreshPermission(); document.addEventListener('visibilitychange', refreshPermission) })
onUnmounted(() => document.removeEventListener('visibilitychange', refreshPermission))
// Bumped after every attempt so a switch whose change failed snaps back to the real state.
const attempt = ref(0)

async function toggle(topic, on) {

  if (!userName.value) {
    askName.value = true // the name is how we leave out your own changes
    return
  }

  busy.value = true
  failed.value = null
  try {
    const next = on ? [...topics.value, topic] : topics.value.filter(x => x !== topic)
    await setTopics(TOPICS.filter(x => next.includes(x)), { name: userName.value, lang: locale.value })
  } catch (err) {
    failed.value = err?.name === 'AbortError' ? 'pushService' : 'generic'
  } finally {
    busy.value = false
    attempt.value++
  }
}

async function test() {
  await sendTest()
  testSent.value = true
}
</script>

<template>
  <!-- iPhone in Safari: push only exists in the installed app -->
  <div v-if="needsInstall" class="rounded-lg bg-zinc-100 p-4 text-sm">
    <p class="mb-1 font-semibold">{{ t('push.installFirstTitle') }}</p>
    <p class="mb-3 text-zinc-600">{{ t('push.installFirst') }}</p>
    <RouterLink to="/app" class="inline-flex min-h-11 items-center gap-2 font-medium underline underline-offset-4"><i class="pi pi-mobile" />{{ t('app.install') }}</RouterLink>
  </div>

  <p v-else-if="!supported" class="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-600">{{ t('push.unsupported') }}</p>

  <div v-else>
    <p class="mb-3 text-sm text-zinc-600">{{ t('push.intro') }}</p>

    <ul class="divide-y divide-zinc-100">
      <li v-for="topic in TOPICS" :key="topic">
        <label class="flex min-h-14 cursor-pointer items-center gap-3 py-2">
          <span class="min-w-0 flex-1">
            <span class="block text-sm font-medium">{{ t(`push.topics.${topic}.title`) }}</span>
            <span class="block text-xs text-zinc-500">{{ t(`push.topics.${topic}.hint`) }}</span>
          </span>
          <ToggleSwitch :key="`${topic}-${attempt}`" :model-value="topics.includes(topic)" :disabled="busy || permission === 'denied'" @update:model-value="toggle(topic, $event)" />
        </label>
      </li>
    </ul>

    <p v-if="failed" class="mt-4 flex gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">
      <i class="pi pi-exclamation-triangle mt-0.5" />
      <span class="flex-1">{{ t(`push.failed.${failed}`) }}</span>
    </p>

    <p v-if="permission === 'denied'" class="mt-4 flex gap-2 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
      <i class="pi pi-exclamation-triangle mt-0.5" />
      <span class="flex-1">
        {{ t('push.blocked') }}
        <button class="mt-1 block min-h-11 font-semibold underline underline-offset-4" @click="refreshPermission(); attempt++">{{ t('push.recheck') }}</button>
      </span>
    </p>

    <div v-if="topics.length" class="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-zinc-100 pt-4">
      <p class="text-xs text-zinc-500">{{ t('push.notOwn') }}</p>
      <Button :label="testSent ? t('push.testSent') : t('push.test')" :icon="testSent ? 'pi pi-check' : 'pi pi-send'" size="small" outlined severity="secondary" @click="test" />
    </div>
  </div>
</template>
