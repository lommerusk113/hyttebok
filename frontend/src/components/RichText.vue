<script setup>
import { computed } from 'vue'

// Plain text where lines starting with "-", "*" or "•" become bullet lists, and web addresses become links.
const props = defineProps({ text: String })

const URL = /(https?:\/\/[^\s]+)/
const pieces = line => line.split(URL).filter(Boolean).map(part => ({ part, link: URL.test(part) }))

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
          <span class="mt-2.5 size-1.5 shrink-0 rounded-full bg-zinc-400" />
          <span class="min-w-0"><template v-for="(p, k) in pieces(item)" :key="k">
            <a v-if="p.link" :href="p.part" target="_blank" rel="noopener noreferrer" class="break-all underline underline-offset-2" @click.stop>{{ p.part }}</a><template v-else>{{ p.part }}</template>
          </template></span>
        </li>
      </ul>
      <p v-else><template v-for="(p, k) in pieces(b.text)" :key="k">
        <a v-if="p.link" :href="p.part" target="_blank" rel="noopener noreferrer" class="break-all underline underline-offset-2" @click.stop>{{ p.part }}</a><template v-else>{{ p.part }}</template>
      </template></p>
    </template>
  </div>
</template>
