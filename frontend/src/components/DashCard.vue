<script setup>
import { t } from '../i18n'

// `countLabel`/`countClass` replace the plain number when it means something specific (e.g. "2 på overtid");
// `countTitle` explains a bare number (tooltip and screen readers).
defineProps({ title: String, icon: String, tint: String, to: String, count: Number, countLabel: String, countClass: String, countTitle: String })
</script>

<template>
  <section class="card flex flex-col">
    <header class="flex items-center gap-3 px-5 pb-2 pt-4">
      <span :class="tint" class="grid size-8 shrink-0 place-items-center rounded-md"><i :class="icon" class="text-sm" /></span>
      <h2 class="min-w-0 truncate font-semibold">{{ title }}</h2>
      <span v-if="countLabel || count" class="shrink-0 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium" :class="countClass || 'bg-zinc-100 text-zinc-600'"
            :title="countTitle" :aria-label="countTitle">{{ countLabel || count }}</span>
      <span class="flex-1" />
      <slot name="action" />
      <!-- Phones get just the arrow so the title keeps its line -->
      <RouterLink v-if="to" :to="to" :aria-label="`${t('overview.seeAll')}: ${title}`"
                  class="-my-2 inline-flex min-h-11 min-w-9 shrink-0 items-center justify-center gap-1 px-1 text-sm font-medium text-zinc-500 hover:text-zinc-950">
        <span class="max-sm:hidden">{{ t('overview.seeAll') }}</span> <i class="pi pi-arrow-right text-xs sm:text-[10px]" />
      </RouterLink>
    </header>
    <div class="flex-1 px-5 pb-4">
      <slot />
    </div>
  </section>
</template>
