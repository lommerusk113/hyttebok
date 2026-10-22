<script setup>
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import { api } from '../api'
import { balances, settleUp } from '../expenses'
import { parseIsoDate, toIsoDate } from '../dates'
import { askName, userName } from '../user'
import { colorFor, formatDate, initials } from '../utils'
import { localeTag, t } from '../i18n'

// "Utgifter": who paid what for the cabin (power, firewood, shopping), split evenly between some people, and
// who owes whom. Paying someone back is saved as a settlement, so the balances even out.
const toast = useToast()
const expenses = ref([])
const people = ref([])
const formOpen = ref(false)
const form = ref({})
const busy = ref(false)

onMounted(async () => {
  ;[expenses.value, people.value] = await Promise.all([api.get('/expenses'), api.get('/cabin/people').catch(() => [])])
})

// Amounts are stored in øre; shown as kroner.
const money = ore => new Intl.NumberFormat(localeTag(), { style: 'currency', currency: 'NOK', maximumFractionDigits: ore % 100 ? 2 : 0 }).format(ore / 100)

// Everyone in the cabin, plus names only found in old entries.
const names = computed(() => [...new Set([...people.value, ...expenses.value.flatMap(e => [e.paidBy, ...e.splitBetween])])].sort((a, b) => a.localeCompare(b)))
const sum = computed(() => balances(expenses.value))
const standing = computed(() => Object.entries(sum.value).filter(([, v]) => v).sort((a, b) => b[1] - a[1]))
const payments = computed(() => settleUp(sum.value))
const sorted = computed(() => [...expenses.value].sort((a, b) => b.spentOn.localeCompare(a.spentOn) || b.id - a.id))
const total = computed(() => expenses.value.filter(e => e.kind === 'EXPENSE').reduce((s, e) => s + e.amount, 0))

function openForm(expense) {
  if (!userName.value) {
    askName.value = true
    return
  }
  form.value = expense
    ? { ...expense, kroner: expense.amount / 100, date: parseIsoDate(expense.spentOn) }
    : { kind: 'EXPENSE', description: '', kroner: null, paidBy: userName.value, splitBetween: [...names.value.length ? names.value : [userName.value]], date: new Date() }
  formOpen.value = true
}

const formValid = computed(() => form.value.kroner > 0 && form.value.paidBy && form.value.splitBetween?.length)

async function save() {
  if (!formValid.value) return
  busy.value = true
  try {
    const { kroner, date, ...fields } = form.value
    const body = { ...fields, amount: Math.round(kroner * 100), spentOn: toIsoDate(date), createdBy: fields.createdBy || userName.value }
    const saved = await (body.id ? api.put('/expenses', body) : api.post('/expenses', body))
    expenses.value = body.id ? expenses.value.map(e => (e.id === saved.id ? saved : e)) : [saved, ...expenses.value]
    formOpen.value = false
  } finally {
    busy.value = false
  }
}

async function remove() {
  await api.del(`/expenses/${form.value.id}`)
  expenses.value = expenses.value.filter(e => e.id !== form.value.id)
  formOpen.value = false
}

// "Ola has paid Kari back": a settlement for the suggested amount.
async function settle(p) {
  const saved = await api.post('/expenses', {
    kind: 'SETTLEMENT', amount: p.amount, paidBy: p.from, splitBetween: [p.to], spentOn: toIsoDate(new Date()), createdBy: userName.value,
  })
  expenses.value = [saved, ...expenses.value]
  toast.add({ severity: 'success', summary: t('expenses.settled', { from: p.from, to: p.to, amount: money(p.amount) }), life: 3000 })
}

const toggleAll = on => { form.value.splitBetween = on ? [...names.value] : [] }
</script>

<template>
  <div class="grid gap-6 lg:grid-cols-3 lg:items-start">
    <!-- Who owes whom -->
    <DashCard :title="t('expenses.balance')" icon="pi pi-wallet" tint="bg-emerald-50 text-emerald-700" class="lg:order-2">
      <p v-if="!standing.length" class="py-1 text-sm text-zinc-500">{{ expenses.length ? t('expenses.even') : t('expenses.nothingYet') }}</p>
      <ul v-else class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
        <li v-for="[name, v] in standing" :key="name" class="flex items-center gap-3 px-5 py-2">
          <Avatar :label="initials(name)" shape="circle" size="small" :class="[colorFor(name).bg, colorFor(name).text]" />
          <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ name }}</span>
          <span class="text-sm font-semibold tabular-nums" :class="v > 0 ? 'text-emerald-700' : 'text-rose-700'">{{ v > 0 ? '+' : '−' }}{{ money(Math.abs(v)) }}</span>
        </li>
      </ul>

      <template v-if="payments.length">
        <p class="mt-4 text-xs font-medium uppercase tracking-wider text-zinc-500">{{ t('expenses.settleUp') }}</p>
        <ul class="mt-2 space-y-2">
          <li v-for="p in payments" :key="`${p.from}-${p.to}`" class="flex flex-wrap items-center gap-2 rounded-lg bg-zinc-50 px-3 py-2">
            <span class="min-w-0 flex-1 text-sm">{{ t('expenses.pays', { from: p.from, to: p.to }) }} <strong class="tabular-nums">{{ money(p.amount) }}</strong></span>
            <Button :label="t('expenses.markPaid')" icon="pi pi-check" size="small" text @click="settle(p)" />
          </li>
        </ul>
      </template>
    </DashCard>

    <!-- Everything paid -->
    <DashCard :title="t('expenses.title')" icon="pi pi-receipt" tint="bg-amber-50 text-amber-700" class="lg:order-1 lg:col-span-2"
              :count="expenses.length" :count-title="t('expenses.total', { amount: money(total) })">
      <template #action>
        <Button :label="t('expenses.new')" icon="pi pi-plus" size="small" @click="openForm()" />
      </template>
      <p v-if="total" class="mb-3 text-sm text-zinc-500">{{ t('expenses.total', { amount: money(total) }) }}</p>
      <ul v-if="expenses.length" class="-mx-5 divide-y divide-zinc-100 border-t border-zinc-100">
        <li v-for="e in sorted" :key="e.id">
          <button class="flex min-h-14 w-full items-center gap-3 px-5 py-2.5 text-left transition hover:bg-zinc-50" @click="openForm(e)">
            <span class="grid size-9 shrink-0 place-items-center rounded-full" :class="e.kind === 'SETTLEMENT' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">
              <i class="pi text-sm" :class="e.kind === 'SETTLEMENT' ? 'pi-arrow-right-arrow-left' : 'pi-shopping-bag'" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate font-medium">{{ e.kind === 'SETTLEMENT' ? t('expenses.paidBack', { from: e.paidBy, to: e.splitBetween[0] }) : e.description || t('expenses.untitled') }}</span>
              <span class="block truncate text-xs text-zinc-500">
                {{ formatDate(e.spentOn) }}<template v-if="e.kind === 'EXPENSE'"> · {{ t('expenses.paidByShort', { name: e.paidBy }) }} · {{ t('expenses.splitCount', { n: e.splitBetween.length }, e.splitBetween.length) }}</template>
              </span>
            </span>
            <span class="shrink-0 font-semibold tabular-nums">{{ money(e.amount) }}</span>
          </button>
        </li>
      </ul>
      <EmptyState v-else icon="pi pi-receipt" :text="t('expenses.empty')">
        <Button :label="t('expenses.new')" icon="pi pi-plus" size="small" @click="openForm()" />
      </EmptyState>
    </DashCard>

    <Dialog v-model:visible="formOpen" dismissable-mask modal :header="form.kind === 'SETTLEMENT' ? t('expenses.settlement') : form.id ? t('expenses.edit') : t('expenses.new')"
            class="w-[30rem] max-w-[calc(100vw-2rem)]">
      <form id="expense-form" class="flex flex-col gap-4" @submit.prevent="save">
        <label v-if="form.kind === 'EXPENSE'" class="flex flex-col gap-1.5 text-sm font-medium">{{ t('expenses.what') }}
          <InputText v-model="form.description" maxlength="200" :placeholder="t('expenses.whatPlaceholder')" autofocus />
        </label>
        <div class="grid grid-cols-2 gap-3">
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('expenses.amount') }}
            <InputNumber v-model="form.kroner" :min="0" :max-fraction-digits="2" suffix=" kr" :locale="localeTag()" input-class="w-full" />
          </label>
          <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('expenses.date') }}
            <DatePicker v-model="form.date" :manual-input="false" show-icon icon-display="input" date-format="d. M yy" input-class="w-full" />
          </label>
        </div>
        <label class="flex flex-col gap-1.5 text-sm font-medium">{{ form.kind === 'SETTLEMENT' ? t('expenses.from') : t('expenses.paidBy') }}
          <Select v-model="form.paidBy" :options="names" editable :placeholder="t('expenses.pickPerson')" />
        </label>

        <label v-if="form.kind === 'SETTLEMENT'" class="flex flex-col gap-1.5 text-sm font-medium">{{ t('expenses.to') }}
          <Select :model-value="form.splitBetween[0]" :options="names" editable @update:model-value="form.splitBetween = [$event]" />
        </label>
        <fieldset v-else class="flex flex-col gap-2">
          <legend class="mb-1.5 flex w-full items-center justify-between text-sm font-medium">
            {{ t('expenses.splitBetween') }}
            <Button type="button" :label="form.splitBetween.length === names.length ? t('expenses.none') : t('expenses.all')" size="small" text severity="secondary"
                    @click="toggleAll(form.splitBetween.length !== names.length)" />
          </legend>
          <label v-for="n in names" :key="n" class="flex min-h-10 cursor-pointer items-center gap-3 rounded-md px-2 hover:bg-zinc-50">
            <Checkbox v-model="form.splitBetween" :value="n" />
            <span class="flex-1 text-sm">{{ n }}</span>
            <span v-if="form.splitBetween.includes(n) && form.kroner" class="text-xs tabular-nums text-zinc-500">{{ money(Math.round(form.kroner * 100 / form.splitBetween.length)) }}</span>
          </label>
        </fieldset>
      </form>
      <template #footer>
        <Button v-if="form.id" :label="t('common.delete')" icon="pi pi-trash" text severity="danger" class="mr-auto" @click="remove" />
        <Button :label="t('common.cancel')" text severity="secondary" @click="formOpen = false" />
        <Button type="submit" form="expense-form" :label="t('common.save')" icon="pi pi-check" :loading="busy" :disabled="!formValid" />
      </template>
    </Dialog>
  </div>
</template>
