<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import TodoPage from './TodoPage.vue'
import ShoppingPage from './ShoppingPage.vue'
import SuggestionsPage from './SuggestionsPage.vue'
import NotesPage from './NotesPage.vue'

// All lists on one board; /lister/<section> (e.g. from the dashboard) scrolls to that section.
const route = useRoute()

onMounted(() => {
  const section = route.params.sub
  // ponytail: fixed delay so the sections have loaded their data first; fine for a handful of items
  if (section) setTimeout(() => document.getElementById(`lists-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 300)
})
</script>

<template>
  <!-- Gjøremål and Handleliste side by side on wide screens, stretched to the same height -->
  <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
    <div id="lists-gjoremal" class="flex scroll-mt-36 *:flex-1"><TodoPage /></div>
    <div id="lists-handle" class="flex scroll-mt-36 *:flex-1"><ShoppingPage /></div>
    <div id="lists-forslag" class="scroll-mt-36 lg:col-span-2"><SuggestionsPage /></div>
    <div id="lists-notater" class="scroll-mt-36 lg:col-span-2"><NotesPage /></div>
  </div>
</template>
