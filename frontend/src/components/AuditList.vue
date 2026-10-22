<script setup>
import { formatDate } from '../utils'
import { localeTag, t } from '../i18n'

// The change log: who changed what about access. `withCabin`: say which cabin (the admin console lists them all).
defineProps({ events: { type: Array, required: true }, withCabin: Boolean })

const ICON = {
  ROLE_CHANGED: 'pi-user-edit', MEMBER_REMOVED: 'pi-user-minus', MEMBER_LEFT: 'pi-sign-out', MEMBER_JOINED: 'pi-user-plus', ADMIN_JOINED: 'pi-shield',
  INVITE_CREATED: 'pi-link', INVITE_REVOKED: 'pi-times', CABIN_CREATED: 'pi-home', CABIN_DELETED: 'pi-trash', CABIN_RESTORED: 'pi-replay',
  CHAT_CHANGED: 'pi-comments', RESET_LINK: 'pi-key', PASSWORD_SET: 'pi-key', PASSWORD_RESET: 'pi-key', RECOVERY_USED: 'pi-key', SIGNED_OUT_EVERYWHERE: 'pi-sign-out',
}

function text(e) {
  const params = {
    actor: e.actor || t('audit.someone'),
    target: e.target || t('audit.someone'),
    role: e.action === 'ROLE_CHANGED' && t(`access.roles.${e.detail}`).toLowerCase(),
  }
  const key = e.action === 'CHAT_CHANGED' ? `audit.CHAT_${e.detail}` : e.action === 'CABIN_CREATED' && e.detail === 'demo' ? 'audit.DEMO_CREATED' : `audit.${e.action}`

  return t(key, params)
}
</script>

<template>
  <ul class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
    <li v-for="e in events" :key="e.id" class="flex items-start gap-3 px-5 py-2.5">
      <span class="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-zinc-100 text-zinc-500"><i class="pi text-xs" :class="ICON[e.action] || 'pi-circle'" /></span>
      <span class="min-w-0 flex-1">
        <span class="block text-sm">{{ text(e) }}</span>
        <span class="block text-xs text-zinc-500">{{ formatDate(e.at) }} {{ new Date(e.at).toLocaleTimeString(localeTag(), { hour: '2-digit', minute: '2-digit' }) }}<template v-if="withCabin && e.cabinName"> · {{ e.cabinName }}</template></span>
      </span>
    </li>
  </ul>
</template>
