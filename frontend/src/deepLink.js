import { useRoute, useRouter } from 'vue-router'

// One-shot query params (?new=…, ?id=…, ?stay=…) used by dashboard links. The query is captured at
// setup, so several sections on one page each see it; reading it clears the URL so a reload doesn't
// reopen the dialog. `new` names the target section where it matters (e.g. ?new=gjoremal).
export function useDeepLink() {
  const route = useRoute()
  const router = useRouter()
  const query = { ...route.query }

  return () => {
    if (Object.keys(route.query).length) router.replace({ query: {} })
    return query
  }
}
