<script setup>
import { onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Drawer from 'primevue/drawer'
import DashCard from '../components/DashCard.vue'
import EmptyState from '../components/EmptyState.vue'
import PhotoLightbox from '../components/PhotoLightbox.vue'
import { api, photoUrl, uploadPhoto } from '../api'
import { userName } from '../user'
import { saveCabin } from '../cabin'
import { t } from '../i18n'

const toast = useToast()
const confirm = useConfirm()
const photos = ref([])
const uploadOpen = ref(false)
const files = ref([])
const caption = ref('')
const uploading = ref(false)
const index = ref(null)
const allOpen = ref(false)

const PREVIEW = 12

onMounted(async () => { photos.value = await api.get('/photos') })


async function upload() {
  uploading.value = true
  try {
    const saved = []
    for (const f of files.value) saved.push(await uploadPhoto(f, caption.value, userName.value))
    photos.value = [...saved.reverse(), ...photos.value]
    toast.add({ severity: 'success', summary: t('photos.uploaded', saved.length), life: 2500 })
    uploadOpen.value = false
    files.value = []
    caption.value = ''
  } catch (err) {
    toast.add({ severity: 'error', summary: t('common.uploadFailed'), detail: err.message, life: 4000 })
  } finally {
    uploading.value = false
  }
}

async function useAsHero(photo) {
  await saveCabin({ heroPhotoId: photo.id })
  toast.add({ severity: 'success', summary: t('photos.coverSet'), life: 2500 })
}

function remove(photo) {
  confirm.require({
    header: t('photos.deleteTitle'),
    message: t('photos.deleteMessage'),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', text: true },
    accept: async () => {
      await api.del(`/photos/${photo.id}`)
      photos.value = photos.value.filter(p => p.id !== photo.id)
      index.value = null
    },
  })
}
</script>

<template>
  <DashCard :title="t('tabs.photos')" icon="pi pi-images" tint="bg-violet-50 text-violet-700" :count="photos.length">
    <template #action>
      <Button icon="pi pi-upload" :label="t('photos.upload')" size="small" outlined severity="secondary" @click="uploadOpen = true" />
    </template>

    <!-- Phone: a swipeable strip; desktop: a 3-column grid. Everything else is one tap away in a slide-in. -->
    <div v-if="photos.length" class="-mx-5 flex gap-2 overflow-x-auto px-5 pb-1 lg:mx-0 lg:grid lg:grid-cols-3 lg:overflow-visible lg:px-0 lg:pb-0">
      <button v-for="(p, i) in photos.slice(0, PREVIEW)" :key="p.id" class="group relative size-24 shrink-0 overflow-hidden rounded-md bg-zinc-100 lg:aspect-square lg:size-auto"
              @click="index = i">
        <img :src="photoUrl(p.id)" :alt="p.caption || ''" loading="lazy" class="h-full w-full object-cover transition duration-300 group-hover:scale-105">
      </button>
    </div>
    <EmptyState v-else icon="pi pi-images" :text="t('photos.empty')" />
    <Button v-if="photos.length > PREVIEW" :label="t('photos.seeAll', { n: photos.length })" icon="pi pi-th-large" text size="small" class="mt-2 !px-0" @click="allOpen = true" />
  </DashCard>

  <Drawer v-model:visible="allOpen" position="right" :header="t('tabs.photos')" class="!w-full sm:!w-[44rem]">
    <div class="grid grid-cols-3 gap-2 sm:grid-cols-4">
      <button v-for="(p, i) in photos" :key="p.id" class="group relative aspect-square overflow-hidden rounded-md bg-zinc-100" @click="index = i">
        <img :src="photoUrl(p.id)" :alt="p.caption || ''" loading="lazy" class="h-full w-full object-cover transition duration-300 group-hover:scale-105">
        <span v-if="p.caption" class="absolute inset-x-0 bottom-0 truncate bg-gradient-to-t from-black/60 to-transparent p-2 pt-6 text-left text-xs text-white opacity-0 transition group-hover:opacity-100">
          {{ p.caption }}
        </span>
      </button>
    </div>
  </Drawer>

  <!-- Upload -->
  <Dialog dismissable-mask v-model:visible="uploadOpen" modal :header="t('photos.uploadTitle')" class="w-[30rem] max-w-[calc(100vw-2rem)]">
    <div class="flex flex-col gap-4">
      <label class="flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed border-zinc-300 p-8 text-center transition hover:border-zinc-500 hover:bg-zinc-100">
        <i class="pi pi-cloud-upload text-3xl text-zinc-900" />
        <span class="font-medium">{{ files.length ? t('photos.selected', files.length) : t('photos.choose') }}</span>
        <span class="text-xs text-zinc-500">{{ t('photos.maxSize') }}</span>
        <input type="file" accept="image/*" multiple class="hidden" @change="files = [...$event.target.files]">
      </label>
      <InputText v-model="caption" :placeholder="t('photos.captionPlaceholder')" />
    </div>
    <template #footer>
      <Button :label="t('common.cancel')" text severity="secondary" @click="uploadOpen = false" />
      <Button :label="t('photos.upload')" icon="pi pi-upload" :loading="uploading" :disabled="!files.length" @click="upload" />
    </template>
  </Dialog>

  <PhotoLightbox v-model:index="index" :photos="photos">
    <template #actions="{ photo }">
      <Button :label="t('photos.setCover')" icon="pi pi-star" size="small" text class="!text-white" @click="useAsHero(photo)" />
      <Button icon="pi pi-trash" size="small" text severity="danger" :aria-label="t('common.delete')" @click="remove(photo)" />
    </template>
  </PhotoLightbox>
</template>
