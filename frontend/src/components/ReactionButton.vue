<script>
// Emoji → its name in forum.reactions.* (the server accepts exactly these).
export const REACTIONS = { '👍': 'like', '❤️': 'love', '😂': 'haha', '😮': 'wow', '😢': 'sad', '🎉': 'party' }
</script>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { t } from '../i18n'

// Facebook-style "Liker": a tap likes (or takes your reaction away); holding it (or hovering with a mouse,
// or arrow up on the keyboard) shows all the reactions to pick from.

const props = defineProps({ mine: String, small: Boolean })
const emit = defineEmits(['pick'])

const open = ref(false)
let timer
let held = false

const label = computed(() => (props.mine ? t(`forum.reactions.${REACTIONS[props.mine]}`) : t('forum.reactions.like')))

function pick(emoji) {
  open.value = false
  emit('pick', emoji)
}

function press(e) {
  held = false
  if (e.pointerType === 'mouse') return
  timer = setTimeout(() => { held = true; open.value = true }, 450)
}

function release() {
  clearTimeout(timer)
}

function tap() {
  if (held) return // the long press opened the picker; letting go isn't a tap
  pick(props.mine || '👍')
}

function hover(on) {
  clearTimeout(timer)
  timer = setTimeout(() => { open.value = on }, on ? 500 : 300)
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <span :class="!small && 'flex-1'" class="relative inline-flex" @mouseenter="hover(true)" @mouseleave="hover(false)">
    <button type="button" aria-haspopup="true" :aria-expanded="open" :aria-pressed="!!mine"
            :class="[small ? 'min-h-8 px-1 text-xs' : 'min-h-10 flex-1 justify-center rounded-md px-3 text-sm hover:bg-zinc-100', mine ? 'font-semibold text-sky-700' : 'font-medium text-zinc-600']"
            class="flex select-none items-center gap-1.5 transition [-webkit-touch-callout:none]"
            @pointerdown="press" @pointerup="release" @pointercancel="release" @contextmenu.prevent
            @click="tap" @keydown.up.prevent="open = true" @keydown.esc="open = false">
      <span v-if="mine" aria-hidden="true">{{ mine }}</span>
      <i v-else-if="!small" class="pi pi-thumbs-up" />
      {{ label }}
    </button>
    <span v-if="open" role="menu" class="absolute bottom-full left-0 z-10 mb-1 flex gap-0.5 rounded-full border border-zinc-200 bg-surface p-1 shadow-lg">
      <button v-for="(name, emoji) in REACTIONS" :key="emoji" type="button" role="menuitem" :aria-label="t(`forum.reactions.${name}`)" :title="t(`forum.reactions.${name}`)"
              :class="mine === emoji && 'bg-zinc-100'" class="grid size-10 place-items-center rounded-full text-2xl transition hover:scale-125"
              @click="pick(emoji)">{{ emoji }}</button>
    </span>
  </span>
</template>
