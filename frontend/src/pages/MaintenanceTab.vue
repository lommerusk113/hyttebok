<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MaintenancePage from './MaintenancePage.vue'
import NotesPage from './NotesPage.vue'

// Tasks (recurring, one-off and suggested) with the year overview, then notes. /vedlikehold/<section>
// (e.g. from the dashboard or a notification) scrolls to that section.
const route = useRoute()

onMounted(() => {
  const section = route.params.sub
  // ponytail: fixed delay so the sections have loaded their data first; fine for a handful of items
  if (section) setTimeout(() => document.getElementById(`lists-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300)
})
</script>

<template>
  <div class="space-y-6">
    <MaintenancePage />
    <div id="lists-notater" class="scroll-mt-36"><NotesPage /></div>
  </div>
</template>
