<script setup>
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Drawer from 'primevue/drawer'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import { photoUrl, uploadPhoto } from '../api'
import { cabin, INFO_TILES, loadCabin, saveCabin } from '../cabin'
import { userName } from '../user'
import { t } from '../i18n'

// Each tab is its own chunk, loaded when first opened (keeps the first load small on cabin internet).
const OverviewTab = defineAsyncComponent(() => import('./OverviewTab.vue'))
const BookingPage = defineAsyncComponent(() => import('./BookingPage.vue'))
const ListsTab = defineAsyncComponent(() => import('./ListsTab.vue'))
const HyttebokTab = defineAsyncComponent(() => import('./HyttebokTab.vue'))

const TABS = [
  { key: '', label: 'tabs.overview', icon: 'pi pi-home', component: OverviewTab },
  { key: 'booking', label: 'tabs.booking', icon: 'pi pi-calendar', component: BookingPage },
  { key: 'lister', label: 'tabs.lists', icon: 'pi pi-list-check', component: ListsTab },
  { key: 'hyttebok', label: 'tabs.logbook', icon: 'pi pi-book', component: HyttebokTab },
]

const route = useRoute()
const toast = useToast()
const uploading = ref(false)
const heroInput = ref()
const editing = ref(false)
const form = ref({})

onMounted(loadCabin)

const tab = computed(() => TABS.find(tb => tb.key === (route.params.tab || '')))
const view = computed(() => tab.value.component)
const tabLink = tb => (tb.key ? `/${tb.key}` : '/')

const textareas = {}

// Appends a "- " line and puts the cursor there, so bullets don't need typing knowledge.
function addBullet(key) {
  const value = form.value[key] || ''
  form.value[key] = value + (value && !value.endsWith('\n') ? '\n' : '') + '- '
  const el = textareas[key]?.$el
  requestAnimationFrame(() => { el?.focus(); el?.setSelectionRange(el.value.length, el.value.length) })
}

const sectionOn = key => !form.value.hiddenSections.includes(key)
function toggleSection(key, on) {
  form.value.hiddenSections = on ? form.value.hiddenSections.filter(k => k !== key) : [...form.value.hiddenSections, key]
}

async function changeHero(e) {
  const file = e.target.files[0]
  if (!file) return
  uploading.value = true
  try {
    const photo = await uploadPhoto(file, t('home.coverCaption'), userName.value)
    await saveCabin({ heroPhotoId: photo.id })
    toast.add({ severity: 'success', summary: t('home.coverUpdated'), life: 2500 })
  } catch (err) {
    toast.add({ severity: 'error', summary: t('common.uploadFailed'), detail: err.message, life: 4000 })
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

function startEdit() {
  form.value = { ...cabin.value, hiddenSections: [...cabin.value.hiddenSections] }
  editing.value = true
}

async function save() {
  await saveCabin(form.value)
  editing.value = false
  toast.add({ severity: 'success', summary: t('common.saved'), life: 2000 })
}
</script>

<template>
  <div v-if="cabin">
    <!-- Hero: identical on every tab so nothing above the tabs moves when switching -->
    <section class="relative isolate overflow-hidden rounded-xl bg-gradient-to-br from-[#27272a] via-[#18181b] to-black shadow-lg">
      <img v-if="cabin.heroPhotoId" :src="photoUrl(cabin.heroPhotoId)" alt="" class="absolute inset-0 -z-10 h-full w-full object-cover">
      <div class="absolute inset-0 -z-10 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />
      <img v-if="!cabin.heroPhotoId" src="/mark.png" alt="" class="absolute -right-8 -top-6 -z-10 w-72 opacity-[0.07] sm:right-10 sm:w-96">

      <Button icon="pi pi-pencil" rounded text :aria-label="t('home.editInfo')" v-tooltip.left="t('home.editInfo')"
              class="!absolute right-3 top-3 !bg-black/30 !text-white hover:!bg-black/50" @click="startEdit" />
      <div class="flex min-h-[11rem] flex-col justify-end p-6 sm:min-h-[15rem] sm:p-8">
        <h1 class="font-display break-words text-3xl uppercase tracking-[0.12em] text-white drop-shadow sm:text-4xl">{{ cabin.name }}</h1>
        <p v-if="cabin.tagline" class="mt-2 max-w-xl text-white/85 sm:text-lg">{{ cabin.tagline }}</p>
      </div>
    </section>

    <!-- Tabs -->
    <nav class="sticky top-14 z-20 -mx-4 bg-page/90 px-4 py-2 backdrop-blur mt-4 sm:top-16">
      <div class="grid grid-cols-4 gap-1 rounded-lg border border-zinc-200 bg-surface p-1 shadow-sm">
        <RouterLink v-for="tb in TABS" :key="tb.key" :to="tabLink(tb)"
                    class="flex flex-col items-center gap-0.5 rounded-md px-2 py-1.5 text-xs font-medium text-zinc-500 transition hover:bg-zinc-100 hover:text-zinc-900 sm:flex-row sm:justify-center sm:gap-2 sm:text-sm"
                    :class="tab === tb && '!bg-zinc-950 !text-surface'">
          <i :class="tb.icon" />{{ t(tb.label) }}
        </RouterLink>
      </div>
    </nav>

    <!-- min-h keeps the page from collapsing (and the scroll position from jumping) while a tab loads -->
    <div class="mt-4 min-h-screen">
      <component :is="view" :key="tab.key" />
    </div>

    <!-- Edit drawer -->
    <Drawer v-model:visible="editing" position="right" :header="t('home.editTitle')" class="!w-full sm:!w-[30rem]">
      <form class="flex flex-col gap-4" @submit.prevent="save">
        <div class="flex items-center gap-3 rounded-lg border border-zinc-200 p-3">
          <img v-if="cabin.heroPhotoId" :src="photoUrl(cabin.heroPhotoId)" alt="" class="h-14 w-20 shrink-0 rounded-md object-cover">
          <span v-else class="grid h-14 w-20 shrink-0 place-items-center rounded-md bg-zinc-100 text-zinc-500"><i class="pi pi-image" /></span>
          <span class="flex-1 text-sm font-medium">{{ t('home.coverCaption') }}</span>
          <input ref="heroInput" type="file" accept="image/*" class="hidden" @change="changeHero">
          <Button type="button" :label="cabin.heroPhotoId ? t('home.changePhoto') : t('home.addPhoto')" icon="pi pi-camera" size="small"
                  outlined severity="secondary" :loading="uploading" @click="heroInput.click()" />
        </div>
        <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('home.name') }}<InputText v-model="form.name" required /></label>
        <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('home.tagline') }}<InputText v-model="form.tagline" /></label>
        <label class="flex flex-col gap-1.5 text-sm font-medium">{{ t('home.about') }}<Textarea v-model="form.description" rows="5" auto-resize /></label>

        <p class="pt-2 text-xs font-medium uppercase tracking-wider text-zinc-500">{{ t('home.infoCards') }}</p>
        <div v-for="tile in INFO_TILES" :key="tile.key" class="rounded-lg border border-zinc-200 p-3">
          <label class="flex cursor-pointer items-center gap-3">
            <span :class="tile.tint" class="grid size-8 place-items-center rounded-md"><i :class="tile.icon" class="text-sm" /></span>
            <span class="flex-1 text-sm font-medium" :class="!sectionOn(tile.key) && 'text-zinc-500'">{{ t(`info.${tile.key}`) }}</span>
            <ToggleSwitch :model-value="sectionOn(tile.key)" @update:model-value="toggleSection(tile.key, $event)" />
          </label>
          <div v-if="sectionOn(tile.key)" class="mt-3">
            <div v-if="tile.key === 'wifi'" class="grid grid-cols-2 gap-3">
              <InputText v-model="form.wifiName" :placeholder="t('home.wifiNetwork')" />
              <InputText v-model="form.wifiPassword" :placeholder="t('home.wifiPassword')" />
            </div>
            <template v-else>
              <Textarea :ref="el => textareas[tile.key] = el" v-model="form[tile.key]" :rows="tile.key === 'address' ? 2 : 4" auto-resize class="w-full" />
              <div class="mt-1.5 flex items-center justify-between gap-2">
                <span class="text-xs text-zinc-500">{{ t('home.bulletHint') }}</span>
                <Button type="button" :label="t('home.addBullet')" icon="pi pi-list" size="small" text severity="secondary" @click="addBullet(tile.key)" />
              </div>
            </template>
          </div>
        </div>

        <div class="flex justify-end gap-2 pt-2">
          <Button :label="t('common.cancel')" text severity="secondary" @click="editing = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" />
        </div>
      </form>
    </Drawer>
  </div>
</template>
