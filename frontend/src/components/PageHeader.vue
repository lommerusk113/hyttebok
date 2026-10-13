<script setup>
defineProps({ subtitle: String })
</script>

<template>
  <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
    <p class="text-sm text-zinc-500">{{ subtitle }}</p>
    <div class="flex gap-2"><slot /></div>
  </div>
</template>
