import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'
import ConfirmationService from 'primevue/confirmationservice'
import Tooltip from 'primevue/tooltip'
import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'
import 'primeicons/primeicons.css'
import './style.css'
import App from './App.vue'
import { i18n, primeLocale, t } from './i18n'

// Ravnsmark: monochrome, black is the accent.
const preset = definePreset(Aura, {
  semantic: {
    primary: Object.fromEntries(
      [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map(s => [s, `{zinc.${s}}`]),
    ),
    colorScheme: {
      light: {
        surface: Object.fromEntries(
          [0, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map(s => [s, s ? `{zinc.${s}}` : '#ffffff']),
        ),
        primary: { color: '{zinc.950}', contrastColor: '#ffffff', hoverColor: '{zinc.800}', activeColor: '{zinc.700}' },
        highlight: { background: '{zinc.950}', focusBackground: '{zinc.800}', color: '#ffffff', focusColor: '#ffffff' },
      },
      dark: {
        surface: Object.fromEntries(
          [0, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map(s => [s, s ? `{zinc.${s}}` : '#ffffff']),
        ),
        primary: { color: '{zinc.50}', contrastColor: '{zinc.950}', hoverColor: '{zinc.200}', activeColor: '{zinc.300}' },
        highlight: { background: '{zinc.50}', focusBackground: '{zinc.200}', color: '{zinc.950}', focusColor: '{zinc.950}' },
      },
    },
  },
})

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/:tab(booking|lister|hyttebok)?/:sub?', component: () => import('./pages/HomePage.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],

})

const app = createApp(App)

app
  .use(router)
  .use(i18n)
  .use(PrimeVue, {
    theme: { preset, options: { darkModeSelector: '.app-dark', cssLayer: { name: 'primevue', order: 'theme, base, primevue' } } },
    locale: primeLocale(),
  })
  .use(ToastService)
  .use(ConfirmationService)
  .directive('tooltip', Tooltip)

// Any failed API call or handler ends up here instead of failing silently.
app.config.errorHandler = err => {
  console.error(err)
  app.config.globalProperties.$toast.add({ severity: 'error', summary: t('common.error'), detail: err?.message?.slice(0, 200), life: 5000 })
}

app.mount('#app')
