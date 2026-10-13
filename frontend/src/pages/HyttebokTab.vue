<script setup>
import { ref } from 'vue'
import LogPage from './LogPage.vue'
import PhotosPage from './PhotosPage.vue'

// Bumping the key remounts the photo column so it picks up photos added through a post.
const photosKey = ref(0)
</script>

<template>
  <!-- Phone: photo strip on top, posts below. Desktop: posts left, photos in a sticky column right. -->
  <div class="flex flex-col gap-6 lg:grid lg:grid-cols-3 lg:items-start [&>*]:min-w-0">
    <div class="lg:sticky lg:top-36 lg:order-2"><PhotosPage :key="photosKey" /></div>
    <div class="lg:order-1 lg:col-span-2"><LogPage @photos-changed="photosKey++" /></div>
  </div>
</template>
