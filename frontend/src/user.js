import { ref, watch } from 'vue'

// ponytail: no login, the name is just remembered per browser. Add auth if the app goes public.
const KEY = 'hyttebok.name'
const read = () => { try { return localStorage.getItem(KEY) || '' } catch { return '' } }

export const userName = ref(read())
export const askName = ref(!userName.value)

watch(userName, v => { try { localStorage.setItem(KEY, v) } catch { /* private mode */ } })
