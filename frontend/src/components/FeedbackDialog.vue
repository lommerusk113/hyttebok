<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Textarea from 'primevue/textarea'
import { t } from '../i18n'

// "Gi tilbakemelding": a message to whoever runs Hyttebok (the admin console lists them), with the page it came from.
// Straight to the server, not through the offline queue: it says if it couldn't be sent.
const visible = defineModel('visible', { type: Boolean })
const route = useRoute()
const toast = useToast()
const message = ref('')
const busy = ref(false)
const failed = ref(false)

watch(visible, open => { if (open) failed.value = false })

async function send() {
  if (!message.value.trim()) return
  busy.value = true
  failed.value = false
  try {
    const res = await fetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: message.value.trim(), page: route.fullPath.slice(0, 200) }),
    })
    if (!res.ok) throw new Error(res.status)
    message.value = ''
    visible.value = false
    toast.add({ severity: 'success', summary: t('feedback.thanks'), life: 3000 })
  } catch {
    failed.value = true
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:visible="visible" modal dismissable-mask :header="t('feedback.title')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <form id="feedback-form" class="flex flex-col gap-3" @submit.prevent="send">
      <p class="text-sm text-zinc-600">{{ t('feedback.hint') }}</p>
      <Textarea v-model="message" rows="5" auto-resize maxlength="4000" autofocus :placeholder="t('feedback.placeholder')" :aria-label="t('feedback.title')" />
      <p v-if="failed" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-800" role="alert">{{ t('feedback.failed') }}</p>
    </form>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="visible = false" />
      <Button type="submit" form="feedback-form" :label="t('feedback.send')" icon="pi pi-send" :loading="busy" :disabled="!message.trim()" />
    </template>
  </Dialog>
</template>
