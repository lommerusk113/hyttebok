<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { enterExample } from '../hytte'

// /eksempel: opens the example cabin (if a system admin picked one), read-only, without logging in.
const router = useRouter()

onMounted(async () => {
  const res = await fetch('/api/cabins/example').catch(() => null)
  if (res?.status !== 200) return router.replace('/')
  const { id } = await res.json()
  enterExample(id)
  router.replace(`/h/${id}`)
})
</script>

<template>
  <div class="grid min-h-[50vh] place-items-center"><i class="pi pi-spin pi-spinner text-2xl text-zinc-400" /></div>
</template>
