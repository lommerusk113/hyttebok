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
import { startSync } from './api'
import { dataVersion } from './offline'
import './install'
import { inCabin, setCabin } from './hytte'

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
    { path: '/app', component: () => import('./pages/AppPage.vue') },
    { path: '/invitasjon/:token', component: () => import('./pages/InvitePage.vue') },
    { path: '/nytt-passord/:token', component: () => import('./pages/ResetPasswordPage.vue') },
    // "Mine hytter": the cabins you belong to, a new cabin, and asking for access with a join code.
    { path: '/', component: () => import('./pages/CabinsPage.vue') },
    // One cabin: /h/<id>/<tab>/<section>. The id (not the name: names needn't be unique) picks the cabin.
    { path: '/h/:cabinId(\\d+)/:tab(booking|hyttebok|vedlikehold|handleliste|forum|tilgang)?/:sub?', component: () => import('./pages/HomePage.vue') },
    // Links without a cabin (inside the app, and in notifications sent before there were several cabins)
    // open in the cabin you're in. Older names first: "Lister" moved to Vedlikehold, the shopping list to its own tab.
    { path: '/lister/:sub?', redirect: to => ({ path: inCabin(`/vedlikehold/${to.params.sub || ''}`), query: to.query }) },
    { path: '/vedlikehold/handle', redirect: () => inCabin('/handleliste') },
    { path: '/:tab(booking|hyttebok|vedlikehold|handleliste|forum|tilgang)/:sub?', redirect: to => ({ path: inCabin(to.path), query: to.query }) },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],

})

// The address decides the cabin; remember it so links without one, and the next visit, go back there.
router.beforeEach(to => {
  if (to.params.cabinId) setCabin(to.params.cabinId)
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
  app.config.globalProperties.$toast.add({ severity: 'error', summary: t('common.error'), detail: t('common.tryAgain'), life: 5000 })
}

// Only once the address is understood: otherwise "not logged in" could show the plain login page before
// the router knows it's an invitation link (and the invitation would be forgotten).
router.isReady().then(() => app.mount('#app'))

startSync(({ sent, dropped }) => {
  const toast = app.config.globalProperties.$toast
  if (sent) toast.add({ severity: 'success', summary: t('offline.synced', sent), life: 3000 })
  if (dropped) toast.add({ severity: 'warn', summary: t('offline.dropped', dropped), life: 6000 })
  dataVersion.value++
})
