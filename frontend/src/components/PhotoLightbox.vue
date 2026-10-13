<script setup>
import { onMounted, onUnmounted } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import { photoUrl } from '../api'
import { formatDate } from '../utils'
import { t } from '../i18n'

// Full-screen photo viewer; `index` is null when closed. Extra buttons go in the `actions` slot.
const props = defineProps({ photos: Array })
const index = defineModel('index')

const step = d => { index.value = (index.value + d + props.photos.length) % props.photos.length }

function onKey(e) {
  if (index.value == null) return
  if (e.key === 'ArrowRight') step(1)
  if (e.key === 'ArrowLeft') step(-1)
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Dialog :visible="index != null" modal :show-header="false" dismissable-mask
          class="!border-0 !bg-transparent !shadow-none w-[min(64rem,calc(100vw-1rem))]" content-class="!p-0 !bg-transparent"
          @update:visible="index = null">
    <div v-if="index != null && photos[index]" class="overflow-hidden rounded-lg bg-black">
      <div class="relative">
        <img :src="photoUrl(photos[index].id)" alt="" class="mx-auto max-h-[75vh] w-auto object-contain">
        <Button icon="pi pi-times" rounded text class="!absolute right-2 top-2 !bg-black/30 !text-white" :aria-label="t('photos.close')" @click="index = null" />
        <template v-if="photos.length > 1">
          <Button icon="pi pi-chevron-left" rounded text class="!absolute left-2 top-1/2 -translate-y-1/2 !bg-black/30 !text-white" :aria-label="t('photos.prev')" @click="step(-1)" />
          <Button icon="pi pi-chevron-right" rounded text class="!absolute right-2 top-1/2 -translate-y-1/2 !bg-black/30 !text-white" :aria-label="t('photos.next')" @click="step(1)" />
        </template>
      </div>
      <div class="flex flex-wrap items-center gap-3 px-4 py-3 text-white">
        <div class="min-w-0 flex-1">
          <p class="font-medium">{{ photos[index].caption || t('photos.noCaption') }}</p>
          <p class="text-xs text-white/60">
            {{ photos[index].uploadedBy || t('photos.unknown') }} · {{ formatDate(photos[index].createdAt) }}
            <span v-if="photos.length > 1"> · {{ index + 1 }}/{{ photos.length }}</span>
          </p>
        </div>
        <slot name="actions" :photo="photos[index]" />
      </div>
    </div>
  </Dialog>
</template>
