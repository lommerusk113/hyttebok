<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import { photoUrl } from '../api'
import { formatDate } from '../utils'
import { t } from '../i18n'

// Full-screen photo viewer: swipe or arrows to browse, Escape or ✕ to close. `index` is null when closed.
// Extra buttons (delete, set as cover) go in the `actions` slot.
const props = defineProps({ photos: Array })
const index = defineModel('index')

const step = d => { index.value = (index.value + d + props.photos.length) % props.photos.length }
const close = () => { index.value = null }

function onKey(e) {
  if (index.value == null) return
  if (e.key === 'ArrowRight') step(1)
  if (e.key === 'ArrowLeft') step(-1)
  if (e.key === 'Escape') close()
}
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => { window.removeEventListener('keydown', onKey); document.body.style.overflow = '' })

// No page scrolling behind the viewer.
watch(index, i => { document.body.style.overflow = i == null ? '' : 'hidden' })

const SWIPE_PX = 50
const touchX = ref(null)
const onTouchStart = e => { touchX.value = e.touches[0].clientX }
function onTouchEnd(e) {
  if (touchX.value == null || props.photos.length < 2) return
  const dx = e.changedTouches[0].clientX - touchX.value
  if (Math.abs(dx) > SWIPE_PX) step(dx < 0 ? 1 : -1)
  touchX.value = null
}
</script>

<template>
  <Teleport to="body">
    <div v-if="index != null && photos[index]" class="fixed inset-0 z-[1050] flex flex-col bg-black text-white" role="dialog" aria-modal="true" :aria-label="t('tabs.photos')">
      <div class="flex items-center justify-between px-2 pt-[env(safe-area-inset-top)]">
        <span class="px-3 text-sm text-white/70 tabular-nums">{{ photos.length > 1 ? `${index + 1} / ${photos.length}` : '' }}</span>
        <span class="flex items-center">
          <slot name="top-actions" :photo="photos[index]" />
          <Button icon="pi pi-times" rounded text class="!size-12 !text-white" :aria-label="t('photos.close')" @click="close" />
        </span>
      </div>

      <!-- Fixed-size stage: the photo scales inside it, so the controls don't move between photos -->
      <!-- Tapping the dark area around the photo closes, like tapping outside any other pop-up -->
      <div class="relative flex min-h-0 flex-1 items-center justify-center" @click.self="close" @touchstart.passive="onTouchStart" @touchend="onTouchEnd">
        <img :src="photoUrl(photos[index].id)" :alt="photos[index].caption || ''" class="max-h-full max-w-full select-none object-contain" draggable="false">
        <template v-if="photos.length > 1">
          <Button icon="pi pi-chevron-left" rounded text class="!absolute left-2 top-1/2 !size-12 -translate-y-1/2 !bg-black/40 !text-white" :aria-label="t('photos.prev')" @click="step(-1)" />
          <Button icon="pi pi-chevron-right" rounded text class="!absolute right-2 top-1/2 !size-12 -translate-y-1/2 !bg-black/40 !text-white" :aria-label="t('photos.next')" @click="step(1)" />
        </template>
      </div>

      <div class="mx-auto w-full max-w-3xl px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-3">
        <p v-if="photos[index].caption" class="font-medium">{{ photos[index].caption }}</p>
        <p class="text-xs text-white/60">
          {{ t('photos.uploadedBy', { name: photos[index].uploadedBy || t('photos.unknown'), date: formatDate(photos[index].createdAt) }) }}
        </p>
        <div v-if="$slots.actions" class="mt-2 flex items-center justify-between gap-2">
          <slot name="actions" :photo="photos[index]" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
