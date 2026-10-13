<script setup>
import ToggleSwitch from 'primevue/toggleswitch'
import { dark, setDark } from '../theme'
import { t } from '../i18n'
</script>

<template>
  <ToggleSwitch :model-value="dark" :aria-label="t('app.darkMode')" @update:model-value="setDark">
    <template #handle="{ checked }">
      <i class="pi !text-[10px]" :class="checked ? 'pi-moon' : 'pi-sun'" />
    </template>
  </ToggleSwitch>
</template>
