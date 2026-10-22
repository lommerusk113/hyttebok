<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import AuditList from '../components/AuditList.vue'
import DashCard from '../components/DashCard.vue'
import { api } from '../api'
import { auth, loadAuth } from '../auth'
import { cabin } from '../cabin'
import { cabinId, setCabin } from '../hytte'
import { colorFor, formatDate, initials } from '../utils'
import { t } from '../i18n'

// "Tilgang": invitation links, and members with their roles. There is no list of everyone who has logged in:
// access is given through invitations.
const confirm = useConfirm()
const toast = useToast()
const router = useRouter()
const invites = ref([])
const members = ref([])
const loaded = ref(false)
const log = ref([])
const chat = ref(null) // { enabled, configured, percentUsed }; only fetched for system admins

const ROLES = computed(() => [{ value: 'OWNER', label: t('access.roles.OWNER') }, { value: 'MEMBER', label: t('access.roles.MEMBER') }])
const inviteUrl = token => `${location.origin}/invitasjon/${token}`

// The server refuses taking away your own ownership, and the last owner's: say why instead of offering it.
const ownerCount = computed(() => members.value.filter(m => m.role === 'OWNER').length)
const ownerLock = m => m.role !== 'OWNER' ? null
  : m.email === auth.value.user?.email ? 'access.ownerSelf'
    : ownerCount.value <= 1 ? 'access.ownerLast' : null

async function load() {
  ;[invites.value, members.value, log.value] = await Promise.all(['/access/invites', '/access/members', '/access/log'].map(api.get))
  loaded.value = true
}
onMounted(load)
onMounted(async () => { if (auth.value.admin) chat.value = await api.get('/access/chat') })

// System admins only: the AI chat for everyone in this cabin (it costs money per question).
async function setChat(enabled) {
  chat.value = await api.put('/access/chat', { enabled })
  await Promise.all([loadAuth(), load()]) // the chat button follows right away
}

// Your own access may have changed (e.g. you gave yourself access): refresh what the app lets you see.
async function changed() {
  await Promise.all([load(), loadAuth()])
}

async function createInvite() {
  const invite = await api.post('/access/invites')
  await load()
  share(invite)
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

// Only marked deleted on the server: everyone loses access, and a system admin can bring it back.
function deleteCabin() {
  const name = cabin.value?.name
  confirm.require({
    header: t('access.deleteTitle'),
    message: t('access.deleteMessage', { name }),
    acceptProps: { label: t('access.deleteCabin'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/cabins/${cabinId.value}`)
      setCabin(null)
      router.push('/?alle=1')
      toast.add({ severity: 'success', summary: t('access.deleted', { name }), life: 4000 })
    },
  })
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">

    <p v-if="!auth.canManage" class="rounded-lg bg-amber-50 p-4 text-sm text-amber-800">{{ t('access.notAllowed') }}</p>

    <template v-else-if="loaded">
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
        <!-- Only the share of the monthly budget, never the amount -->
        <div v-if="chat.configured" class="mt-4">
          <div class="mb-1.5 flex justify-between text-xs text-zinc-500">
            <span>{{ t('access.chatBudget') }}</span><span class="tabular-nums">{{ chat.percentUsed }} %</span>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-zinc-100" role="progressbar" :aria-valuenow="chat.percentUsed" aria-valuemin="0" aria-valuemax="100" :aria-label="t('access.chatBudget')">
            <div class="h-full rounded-full" :class="chat.percentUsed >= 100 ? 'bg-rose-500' : chat.percentUsed >= 80 ? 'bg-amber-500' : 'bg-emerald-500'" :style="{ width: `${chat.percentUsed}%` }" />
          </div>
        </div>
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
            <span class="flex items-center gap-3" v-tooltip.left="ownerLock(m) && t(ownerLock(m))">
              <Select :model-value="m.role" :options="ROLES" option-label="label" option-value="value" size="small" :aria-label="t('access.role')"
                      :disabled="!!ownerLock(m)" @update:model-value="setRole(m, $event)" />
              <Button icon="pi pi-user-minus" text rounded severity="danger" :aria-label="t('access.remove')" :disabled="!!ownerLock(m)" @click="remove(m)" />
            </span>
          </li>
        </ul>
      </DashCard>

      <DashCard v-if="log.length" :title="t('audit.title')" icon="pi pi-history" tint="bg-zinc-100 text-zinc-700" scroll class="max-h-96">
        <AuditList :events="log" />
      </DashCard>

      <DashCard :title="t('access.deleteCabin')" icon="pi pi-trash" tint="bg-red-50 text-red-700">
        <div class="flex flex-wrap items-center gap-3">
          <p class="min-w-0 flex-1 text-sm text-zinc-500">{{ t('access.deleteHint') }}</p>
          <Button :label="t('access.deleteCabin')" icon="pi pi-trash" size="small" severity="danger" outlined @click="deleteCabin" />
        </div>
      </DashCard>

    </template>
  </div>
</template>
