<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Drawer from 'primevue/drawer'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import { photoUrl, uploadPhoto } from '../api'
import { cabin, INFO_TILES, loadCabin, saveCabin } from '../cabin'
import { dayWeather, loadWeather, reloadWeather } from '../weather'
import { toIsoDate } from '../dates'
import WeatherIcon from '../components/WeatherIcon.vue'
import { userName } from '../user'
import { auth } from '../auth'
import { dataVersion } from '../offline'
import { t } from '../i18n'

// Each tab is its own chunk, loaded when first opened (keeps the first load small on cabin internet).
const OverviewTab = defineAsyncComponent(() => import('./OverviewTab.vue'))
const BookingPage = defineAsyncComponent(() => import('./BookingPage.vue'))
const HyttebokTab = defineAsyncComponent(() => import('./HyttebokTab.vue'))
const MaintenanceTab = defineAsyncComponent(() => import('./MaintenanceTab.vue'))
const ShoppingPage = defineAsyncComponent(() => import('./ShoppingPage.vue'))
const ForumPage = defineAsyncComponent(() => import('./ForumPage.vue'))
const AccessPage = defineAsyncComponent(() => import('./AccessPage.vue'))

// "Tilgang" is only for those who can manage access (owners and system admins).
const ALL_TABS = [
  { key: '', label: 'tabs.overview', icon: 'pi pi-home', component: OverviewTab },
  { key: 'booking', label: 'tabs.booking', icon: 'pi pi-calendar', component: BookingPage },
  { key: 'handleliste', label: 'tabs.shopping', icon: 'pi pi-shopping-cart', component: ShoppingPage },
  { key: 'vedlikehold', label: 'tabs.maintenance', icon: 'pi pi-wrench', component: MaintenanceTab },
  { key: 'hyttebok', label: 'tabs.logbook', icon: 'pi pi-book', component: HyttebokTab },
  { key: 'forum', label: 'tabs.forum', icon: 'pi pi-comments', component: ForumPage },
  { key: 'tilgang', label: 'access.title', icon: 'pi pi-users', component: AccessPage, managersOnly: true },
]
const TABS = computed(() => ALL_TABS.filter(tb => !tb.managersOnly || auth.value?.canManage))

const route = useRoute()
const toast = useToast()
const uploading = ref(false)
// A newly picked cover photo waits here until Save, so Cancel leaves the cabin untouched.
const pendingHero = ref(null)
const heroInput = ref()
const editing = ref(false)
const form = ref({})

onMounted(loadCabin)
onMounted(loadWeather)

// Today's weather sits on the cover photo, so it's the same on every tab.
const todayWeather = computed(() => dayWeather(toIsoDate(new Date()))?.symbol && dayWeather(toIsoDate(new Date())))

// After offline changes are synced (or the chatbot changed something), reload the view, but never pull an
// open form away from someone. The chat drawer holds no form, so it doesn't count.
const viewKey = ref(0)

watch(dataVersion, function refresh() {
  if (document.querySelector('.p-dialog, .p-drawer:not(.chat-drawer)')) return setTimeout(refresh, 2000)
  viewKey.value++
  loadCabin()
})

const tab = computed(() => TABS.value.find(tb => tb.key === (route.params.tab || '')) ?? TABS.value[0])
const view = computed(() => tab.value.component)
// New tab: if you'd scrolled past the cover, start the new content at its top (nothing above the tabs moves).
const tabsNav = ref()
// A tab bar wider than the phone (six tabs): keep the open tab in view.
// (Also when the cabin has loaded: the bar only appears then.)
watch([() => tab.value.key, cabin], async () => {
  await nextTick()
  tabsNav.value?.querySelector('[aria-current="page"]')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  measureTabs()
}, { immediate: true })
watch(() => tab.value.key, () => {
  const top = tabsNav.value.getBoundingClientRect().top + scrollY - (window.innerWidth < 640 ? 56 : 64)
  if (scrollY > top) scrollTo({ top })
})
// More tabs than fit: fade the edge that has more behind it, with an arrow, so it's clear the bar scrolls.
const tabsScroll = ref()
const moreTabs = ref({ left: false, right: false })
function measureTabs() {
  const el = tabsScroll.value
  if (el) moreTabs.value = { left: el.scrollLeft > 4, right: el.scrollLeft + el.clientWidth < el.scrollWidth - 4 }
}
const scrollTabs = dir => tabsScroll.value.scrollBy({ left: dir * tabsScroll.value.clientWidth * 0.6, behavior: 'smooth' })
onMounted(() => addEventListener('resize', measureTabs))
onBeforeUnmount(() => removeEventListener('resize', measureTabs))

const tabLink = tb => `/h/${route.params.cabinId}${tb.key ? `/${tb.key}` : ''}`

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

function pickHero(e) {
  const file = e.target.files[0]
  if (file) pendingHero.value = { file, url: URL.createObjectURL(file) }
  e.target.value = ''
}

// Optional exact spot for the weather: coordinates, or a Google Maps link containing them
// (".../search/62.0955,+5.8171" or ".../@62.0955,5.8171,15z"). Empty = use the address.
const parseLocation = text => {
  const m = text.match(/(-?\d{1,2}\.\d+)\s*,\s*\+?\s*(-?\d{1,3}\.\d+)/)
  return m && { latitude: Number(m[1]), longitude: Number(m[2]) }
}
// Coordinates found from the address aren't shown: the field is only for a spot typed in by hand.
const locationText = c => (c.latitude != null && c.locationTyped ? `${c.latitude}, ${c.longitude}` : '')
const locationError = computed(() => !!form.value.location?.trim() && !parseLocation(form.value.location))

function startEdit() {
  form.value = { ...cabin.value, hiddenSections: [...cabin.value.hiddenSections], elevation: cabin.value.elevation == null ? null : Math.round(cabin.value.elevation), location: locationText(cabin.value) }
  pendingHero.value = null
  editing.value = true
}

async function save() {

  if (locationError.value) return
  const { location, ...changes } = form.value
  // Unchanged (just rounded for the field) = keep the stored value, so it isn't mistaken for a typed height.
  const heightTyped = changes.elevation !== (cabin.value.elevation == null ? null : Math.round(cabin.value.elevation))
  if (!heightTyped) changes.elevation = cabin.value.elevation
  const spotChanged = location.trim() !== locationText(cabin.value)
  if (spotChanged) {
    // A new spot (or none: back to the address) gets its height looked up, unless one was typed too.
    Object.assign(changes, parseLocation(location) || { latitude: null, longitude: null }, { locationTyped: !!location.trim() })
    if (!heightTyped) changes.elevation = null
  }
  const moved = spotChanged || changes.address !== cabin.value.address || heightTyped

  uploading.value = true
  try {
    if (pendingHero.value) {
      const photo = await uploadPhoto(pendingHero.value.file, t('home.coverCaption'), userName.value)
      changes.heroPhotoId = photo.id
    }
    await saveCabin(changes)
  } catch (err) {
    toast.add({ severity: 'error', summary: t('common.uploadFailed'), detail: err.message, life: 4000 })
    return
  } finally {
    uploading.value = false
  }

  pendingHero.value = null
  editing.value = false
  if (moved) reloadWeather()
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

      <RouterLink v-if="todayWeather" to="/booking" :aria-label="t('weather.today', { max: Math.round(todayWeather.maxTemp), min: Math.round(todayWeather.minTemp) })"
                  class="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-black/30 py-1 pl-1.5 pr-3 text-sm font-medium text-white backdrop-blur-sm transition hover:bg-black/50">
        <WeatherIcon :symbol="todayWeather.symbol" size="size-7" />
        <span class="tabular-nums">{{ Math.round(todayWeather.maxTemp) }}° <span class="text-white/70">/ {{ Math.round(todayWeather.minTemp) }}°</span></span>
      </RouterLink>
      <Button icon="pi pi-pencil" rounded text :aria-label="t('home.editInfo')" v-tooltip.left="t('home.editInfo')"
              class="!absolute right-3 top-3 !bg-black/30 !text-white hover:!bg-black/50" @click="startEdit" />
      <div class="flex min-h-[11rem] flex-col justify-end p-6 sm:min-h-[15rem] sm:p-8">
        <h1 class="font-display break-words text-3xl uppercase tracking-[0.12em] text-white drop-shadow sm:text-4xl">{{ cabin.name }}</h1>
        <p v-if="cabin.tagline" class="mt-2 max-w-xl text-white/85 sm:text-lg">{{ cabin.tagline }}</p>
      </div>
    </section>

    <!-- Tabs -->
    <nav ref="tabsNav" class="sticky top-14 z-20 -mx-4 bg-page/90 px-4 py-2 backdrop-blur mt-4 sm:top-16">
      <!-- Each tab keeps a readable width; on a narrow phone the bar scrolls sideways -->
      <div class="relative">
        <div ref="tabsScroll" class="grid gap-1 overflow-x-auto rounded-lg border border-zinc-200 bg-surface p-1 shadow-sm [scrollbar-width:none]"
             :style="{ gridTemplateColumns: `repeat(${TABS.length}, minmax(4.25rem, 1fr))` }" @scroll.passive="measureTabs">
          <RouterLink v-for="tb in TABS" :key="tb.key" :to="tabLink(tb)"
                      class="relative flex min-w-0 flex-col items-center gap-0.5 rounded-md px-1 py-1.5 text-[11px] sm:px-2 sm:text-xs font-medium text-zinc-500 transition hover:bg-zinc-100 hover:text-zinc-900 sm:flex-row sm:justify-center sm:gap-2 sm:text-sm"
                      :class="tab === tb && '!bg-zinc-950 !text-surface'">
            <i :class="tb.icon" />{{ t(tb.label) }}
            <!-- People waiting for an answer -->
            <span v-if="tb.key === 'tilgang' && auth?.waitingRequests" class="absolute right-1 top-1 grid min-w-4 place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-semibold leading-4 text-white sm:static sm:min-w-5 sm:text-xs sm:leading-5"
                  :aria-label="t('access.waiting', auth.waitingRequests)">{{ auth.waitingRequests }}</span>
          </RouterLink>
        </div>
        <button v-for="side in ['left', 'right']" v-show="moreTabs[side]" :key="side" type="button" tabindex="-1" aria-hidden="true"
                :class="side === 'left' ? 'left-px rounded-l-lg bg-gradient-to-r justify-start pl-1' : 'right-px rounded-r-lg bg-gradient-to-l justify-end pr-1'"
                class="absolute inset-y-px flex w-10 items-center from-surface from-40% to-transparent text-zinc-500"
                @click="scrollTabs(side === 'left' ? -1 : 1)">
          <i :class="side === 'left' ? 'pi pi-chevron-left' : 'pi pi-chevron-right'" class="text-xs" />
        </button>
      </div>
    </nav>

    <!-- min-h keeps the page from collapsing (and the scroll position from jumping) while a tab loads -->
    <div class="mt-4 min-h-screen">
      <component :is="view" :key="`${tab.key}-${viewKey}`" />
    </div>

    <!-- Edit drawer -->
    <Drawer v-model:visible="editing" position="right" :header="t('home.editTitle')" class="!w-full sm:!w-[30rem]">
      <form class="flex flex-col gap-4" @submit.prevent="save">
        <div class="flex items-center gap-3 rounded-lg border border-zinc-200 p-3">
          <img v-if="pendingHero || form.heroPhotoId" :src="pendingHero?.url || photoUrl(form.heroPhotoId)" alt="" class="h-14 w-20 shrink-0 rounded-md object-cover">
          <span v-else class="grid h-14 w-20 shrink-0 place-items-center rounded-md bg-zinc-100 text-zinc-500"><i class="pi pi-image" /></span>
          <span class="min-w-0 flex-1">
            <span class="block text-sm font-medium">{{ t('home.coverCaption') }}</span>
            <span v-if="pendingHero" class="block text-xs text-zinc-500">{{ t('home.coverPending') }}</span>
          </span>
          <input ref="heroInput" type="file" accept="image/*" class="hidden" @change="pickHero">
          <Button type="button" :label="pendingHero || form.heroPhotoId ? t('home.changePhoto') : t('home.addPhoto')" icon="pi pi-camera" size="small"
                  outlined severity="secondary" @click="heroInput.click()" />
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

        <!-- Weather comes from the exact spot if given, else the address; the height corrects the temperature -->
        <label class="flex flex-col gap-1.5 pt-2 text-sm font-medium">
          {{ t('home.weatherLocation') }}
          <InputText v-model="form.location" :invalid="locationError" :placeholder="t('home.weatherLocationPlaceholder')" />
          <span class="text-xs font-normal" :class="locationError ? 'text-rose-700' : 'text-zinc-500'">
            {{ locationError ? t('home.weatherLocationInvalid') : t('home.weatherLocationHint') }}
          </span>
        </label>
        <label class="flex flex-col gap-1.5 text-sm font-medium">
          {{ t('home.elevation') }}
          <InputNumber v-model="form.elevation" :min="0" :max="2500" :use-grouping="false" :suffix="t('home.elevationSuffix')" :placeholder="t('home.elevationPlaceholder')" />
          <span class="text-xs font-normal text-zinc-500">{{ t('home.elevationHint') }}</span>
        </label>

        <div class="flex justify-end gap-2 pt-2">
          <Button :label="t('common.cancel')" text severity="secondary" @click="editing = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="uploading" />
        </div>
      </form>
    </Drawer>
  </div>
</template>
