<script setup>
defineProps({ icon: String, text: String })
</script>

<template>
  <div class="flex flex-col items-center gap-3 px-6 py-8 text-center">
    <span class="grid size-12 place-items-center rounded-full bg-zinc-100 text-zinc-500"><i :class="icon" class="text-xl" /></span>
    <p class="text-sm text-zinc-500">{{ text }}</p>
    <slot />
  </div>
</template>
