<script setup>
import { t } from '../i18n'

// A picked or attached photo with a small ✕ in the corner (an undo arrow once marked `removed`).
defineProps({ src: String, removed: Boolean })
const emit = defineEmits(['toggle'])
</script>

<template>
  <div class="relative">
    <img :src="src" alt="" class="size-20 rounded-md object-cover" :class="removed && 'opacity-40 grayscale'">
    <button type="button" class="absolute -right-2 -top-2 grid size-7 place-items-center rounded-full bg-zinc-900 text-white"
            :aria-label="removed ? t('suggestions.undo') : t('forum.removePhoto')" @click="emit('toggle')">
      <i :class="removed ? 'pi pi-undo' : 'pi pi-times'" class="text-[10px]" />
    </button>
  </div>
</template>
