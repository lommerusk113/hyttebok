<script setup>
import { computed, onMounted, ref } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import DashCard from '../components/DashCard.vue'
import { api } from '../api'
import { auth, loadAuth } from '../auth'
import { colorFor, formatDate, initials } from '../utils'
import { t } from '../i18n'

// "Tilgang": requests to approve, invitation links, and members with their roles. There is no list of
// everyone who has logged in: access is given through requests and invitations.
const confirm = useConfirm()
const toast = useToast()
const requests = ref([])
const invites = ref([])
const joinCode = ref('')
const members = ref([])
const loaded = ref(false)
const chat = ref(null) // { enabled, configured }; only fetched for system admins

const ROLES = computed(() => [{ value: 'OWNER', label: t('access.roles.OWNER') }, { value: 'MEMBER', label: t('access.roles.MEMBER') }])
const inviteUrl = token => `${location.origin}/invitasjon/${token}`

// The server refuses taking away your own ownership, and the last owner's: say why instead of offering it.
const ownerCount = computed(() => members.value.filter(m => m.role === 'OWNER').length)
const ownerLock = m => m.role !== 'OWNER' ? null
  : m.email === auth.value.user?.email ? 'access.ownerSelf'
    : ownerCount.value <= 1 ? 'access.ownerLast' : null

async function load() {
  let code
  ;[requests.value, invites.value, members.value, code] = await Promise.all(['/access/requests', '/access/invites', '/access/members', '/access/code'].map(api.get))
  joinCode.value = code.code
  loaded.value = true
}
onMounted(load)
onMounted(async () => { if (auth.value.admin) chat.value = await api.get('/access/chat') })

// System admins only: the AI chat for everyone in this cabin (it costs money per question).
async function setChat(enabled) {
  chat.value = await api.put('/access/chat', { enabled, configured: chat.value.configured })
  await loadAuth() // the chat button follows right away
}

// Your own access may have changed (e.g. you gave yourself access): refresh what the app lets you see.
async function changed() {
  await Promise.all([load(), loadAuth()])
}

async function decide(request, approve) {
  await api.post(`/access/requests/${request.id}/${approve ? 'approve' : 'deny'}`)
  await changed()
}

async function createInvite() {
  const invite = await api.post('/access/invites')
  await load()
  share(invite)
}

// No email service: the owner sends the link themselves (chat, SMS).
async function resetLink(person) {
  const reset = await api.post(`/access/users/${person.id}/password-reset`)
  await shareUrl(`${location.origin}/nytt-passord/${reset.token}`, t('access.resetShareTitle', { name: person.name }))
}

const share = invite => shareUrl(inviteUrl(invite.token), t('access.shareTitle'))

async function shareUrl(url, title) {
  if (navigator.share) {
    try {
      await navigator.share({ title, url })
      return
    } catch { /* cancelled: copy instead */ }
  }
  try {
    await navigator.clipboard.writeText(url)
    toast.add({ severity: 'success', summary: t('access.linkCopied'), life: 2500 })
  } catch {
    // No clipboard (plain http, or the browser said no): show the link so it can be copied by hand.
    toast.add({ severity: 'info', summary: t('access.copyLink'), detail: url, life: 15000 })
  }
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(joinCode.value)
    toast.add({ severity: 'success', summary: t('access.codeCopied'), life: 2000 })
  } catch {
    toast.add({ severity: 'info', summary: t('access.code'), detail: joinCode.value, life: 8000 })
  }
}

function newCode() {
  confirm.require({
    header: t('access.newCodeTitle'),
    message: t('access.newCodeMessage'),
    acceptProps: { label: t('access.newCode') },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => { joinCode.value = (await api.post('/access/code')).code },
  })
}

async function revoke(invite) {
  await api.del(`/access/invites/${invite.id}`)
  await load()
}

async function setRole(member, role) {
  await api.put(`/access/members/${member.id}`, { role })
  await changed()
}

function remove(member) {
  confirm.require({
    header: t('access.removeTitle'),
    message: t('access.removeMessage', { name: member.name }),
    acceptProps: { label: t('access.remove'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/access/members/${member.id}`)
      await changed()
    },
  })
}

</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">

    <p v-if="!auth.canManage" class="rounded-lg bg-amber-50 p-4 text-sm text-amber-800">{{ t('access.notAllowed') }}</p>

    <template v-else-if="loaded">
      <!-- Waiting for a yes or no -->
      <DashCard :title="t('access.requests')" icon="pi pi-inbox" tint="bg-amber-50 text-amber-700" :count="requests.length" count-class="bg-amber-100 text-amber-800">
        <ul v-if="requests.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
          <li v-for="r in requests" :key="r.id" class="flex flex-wrap items-center gap-3 px-5 py-3">
            <Avatar :image="r.user.picture || undefined" :label="r.user.picture ? undefined : initials(r.user.name)" shape="circle" :class="[colorFor(r.user.name).bg, colorFor(r.user.name).text]" />
            <span class="min-w-0 flex-1">
              <span class="block font-medium">{{ r.user.name }}</span>
              <span class="block truncate text-xs text-zinc-500">{{ r.user.email }} · {{ formatDate(r.createdAt) }}</span>
              <span v-if="r.message" class="mt-1 block text-sm text-zinc-700">«{{ r.message }}»</span>
            </span>
            <span class="flex gap-2">
              <Button :label="t('access.decline')" size="small" text severity="secondary" @click="decide(r, false)" />
              <Button :label="t('access.approve')" icon="pi pi-check" size="small" @click="decide(r, true)" />
            </span>
          </li>
        </ul>
        <p v-else class="py-1 text-sm text-zinc-500">{{ t('access.noRequests') }}</p>
      </DashCard>

      <!-- The code people type under "Mine hytter" to ask for access (owners approve above) -->
      <DashCard :title="t('access.code')" icon="pi pi-key" tint="bg-violet-50 text-violet-700">
        <p class="mb-3 text-sm text-zinc-500">{{ t('access.codeHint') }}</p>
        <div class="flex flex-wrap items-center gap-2">
          <span class="rounded-lg bg-zinc-100 px-4 py-2 font-mono text-lg font-semibold tracking-widest">{{ joinCode }}</span>
          <Button icon="pi pi-copy" :label="t('common.copy')" size="small" outlined severity="secondary" @click="copyCode" />
          <Button icon="pi pi-refresh" :label="t('access.newCode')" size="small" text severity="secondary" @click="newCode" />
        </div>
      </DashCard>

      <!-- Links to share in the family chat -->
      <DashCard :title="t('access.invites')" icon="pi pi-link" tint="bg-sky-50 text-sky-700">
        <template #action>
          <Button :label="t('access.newInvite')" icon="pi pi-plus" size="small" @click="createInvite" />
        </template>
        <p class="mb-3 text-sm text-zinc-500">{{ t('access.invitesHint') }}</p>
        <ul v-if="invites.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
          <li v-for="i in invites" :key="i.id" class="flex items-center gap-3 px-5 py-2.5">
            <span class="min-w-0 flex-1">
              <span class="block truncate font-mono text-xs text-zinc-700">{{ inviteUrl(i.token) }}</span>
              <span class="block text-xs text-zinc-500">{{ t('access.inviteMeta', { date: formatDate(i.expiresAt), n: i.uses }) }}<template v-if="i.createdBy"> · {{ i.createdBy }}</template></span>
            </span>
            <Button icon="pi pi-share-alt" text rounded severity="secondary" :aria-label="t('access.share')" @click="share(i)" />
            <Button icon="pi pi-times" text rounded severity="secondary" :aria-label="t('access.revoke')" v-tooltip.left="t('access.revoke')" @click="revoke(i)" />
          </li>
        </ul>
      </DashCard>

      <!-- System admins only (ADMIN_EMAILS): owners and members never see this -->
      <DashCard v-if="chat" :title="t('access.adminTools')" icon="pi pi-shield" tint="bg-zinc-100 text-zinc-700">
        <label class="flex items-center gap-4">
          <span class="min-w-0 flex-1">
            <span class="block text-sm font-medium">{{ t('access.chatToggle') }}</span>
            <span class="block text-xs text-zinc-500">{{ t('access.chatHint') }}</span>
          </span>
          <ToggleSwitch :model-value="chat.enabled" @update:model-value="setChat" />
        </label>
        <p v-if="!chat.configured" class="mt-3 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">{{ t('access.chatNoKey') }}</p>
      </DashCard>

      <!-- Who has access -->
      <DashCard :title="t('access.members')" icon="pi pi-users" tint="bg-emerald-50 text-emerald-700" :count="members.length">
        <ul class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
          <li v-for="m in members" :key="m.id" class="flex items-center gap-3 px-5 py-2.5">
            <Avatar :image="m.picture || undefined" :label="m.picture ? undefined : initials(m.name)" shape="circle" :class="[colorFor(m.name).bg, colorFor(m.name).text]" />
            <span class="min-w-0 flex-1">
              <span class="block truncate font-medium">{{ m.name }}</span>
              <span class="block truncate text-xs text-zinc-500"><span v-if="m.admin" class="mr-1.5 rounded-full bg-zinc-100 px-1.5 py-0.5 text-zinc-600">{{ t('access.admin') }}</span>{{ m.email }}</span>
            </span>
            <Button icon="pi pi-key" text rounded severity="secondary" :aria-label="t('access.resetLink')" v-tooltip.left="t('access.resetLink')" @click="resetLink(m)" />
            <span class="flex items-center gap-3" v-tooltip.left="ownerLock(m) && t(ownerLock(m))">
              <Select :model-value="m.role" :options="ROLES" option-label="label" option-value="value" size="small" :aria-label="t('access.role')"
                      :disabled="!!ownerLock(m)" @update:model-value="setRole(m, $event)" />
              <Button icon="pi pi-user-minus" text rounded severity="danger" :aria-label="t('access.remove')" :disabled="!!ownerLock(m)" @click="remove(m)" />
            </span>
          </li>
        </ul>
      </DashCard>

    </template>
  </div>
</template>
