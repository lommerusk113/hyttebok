<script setup>
import { computed } from 'vue'

// Plain text where lines starting with "-", "*" or "•" become bullet lists.
const props = defineProps({ text: String })

const BULLET = /^\s*[-*•]\s+/

const blocks = computed(() => {
  const result = []
  for (const line of (props.text || '').split('\n')) {
    if (BULLET.test(line)) {
      const last = result.at(-1)
      const item = line.replace(BULLET, '')
      last?.list ? last.list.push(item) : result.push({ list: [item] })
    } else if (line.trim()) {
      result.push({ text: line })
    }
  }
  return result
})
</script>

<template>
  <div class="space-y-2 leading-relaxed">
    <template v-for="(b, i) in blocks" :key="i">
      <ul v-if="b.list" class="space-y-1.5">
        <li v-for="(item, j) in b.list" :key="j" class="flex gap-2.5">
          <span class="mt-2.5 size-1.5 shrink-0 rounded-full bg-zinc-400" />{{ item }}
        </li>
      </ul>
      <p v-else>{{ b.text }}</p>
    </template>
  </div>
</template>
