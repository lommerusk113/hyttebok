<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MaintenancePage from './MaintenancePage.vue'
import ShoppingPage from './ShoppingPage.vue'
import SuggestionsPage from './SuggestionsPage.vue'
import NotesPage from './NotesPage.vue'

// Everything for keeping the cabin going on one board: tasks (recurring and one-off) with the year overview,
// the shopping list, ideas and notes. /vedlikehold/<section> (e.g. from the dashboard or a notification)
// scrolls to that section.
const route = useRoute()

onMounted(() => {
  const section = route.params.sub
  // ponytail: fixed delay so the sections have loaded their data first; fine for a handful of items
  if (section) setTimeout(() => document.getElementById(`lists-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300)
})
</script>

<template>
  <div class="space-y-6">
    <MaintenancePage>
      <template #aside>
        <div id="lists-handle" class="scroll-mt-36"><ShoppingPage /></div>
      </template>
    </MaintenancePage>
    <div id="lists-forslag" class="scroll-mt-36"><SuggestionsPage /></div>
    <div id="lists-notater" class="scroll-mt-36"><NotesPage /></div>
  </div>
</template>
