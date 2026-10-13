<script setup>
import { LOCALES, locale, t } from '../i18n'
</script>

<template>
  <div class="flex rounded-full bg-zinc-100 p-0.5 text-xs font-semibold" role="group" :aria-label="t('app.language')">
    <button v-for="(l, code) in LOCALES" :key="code" class="min-h-7 rounded-full px-2.5 transition"
            :class="locale === code ? 'bg-zinc-950 text-surface' : 'text-zinc-500 hover:text-zinc-950'" @click="locale = code">
      {{ l.label }}
    </button>
  </div>
</template>
