<script setup>
import { computed } from 'vue'
import { locale, t } from '../i18n'

// /personvern: privacy and terms, readable without logging in (linked from the landing page and sign-up).
// The text lives here (not in the locale files): it's long, and both languages are kept side by side to stay in step.
const sections = computed(() => (locale.value === 'en' ? EN : NB))

const NB = [
  { title: 'Hvem står bak', body: ['Hyttebok lages og driftes av Ravnsmark. Har du spørsmål om personvern, eller vil du at vi skal slette noe, bruk «Gi tilbakemelding» i menyen i appen.'] },
  {
    title: 'Hva vi lagrer',
    body: [
      'Kontoen din: navn og e-postadresse, og passordet ditt som en sikker hash (aldri selve passordet). Logger du inn med Google, får vi også navn, e-post og profilbilde fra Google.',
      'Det dere legger inn i en hytte: bookinger, hytteboka, bilder, lister, oppgaver, utgifter, forum og wiki. Det er knyttet til navnene dere skriver inn.',
      'Varsler: skrur du på varsler, lagrer vi adressen telefonen eller nettleseren gir oss for å sende dem, og hvilke varsler du vil ha.',
      'Tilbakemeldinger du sender oss, og en logg over endringer i tilgang (hvem som inviterte, fjernet eller endret roller).',
    ],
  },
  {
    title: 'Hvem som ser det',
    body: [
      'Innholdet i en hytte ser bare de som er med i hytta. Eierne styrer hvem som er med, og kan laste ned alt i hytta.',
      'Vi selger aldri data og bruker dem ikke til reklame. Vi ser bare på data for å drifte og hjelpe til, for eksempel når du ber om hjelp.',
      'AI-chatten er bare på i hytter der den er slått på. Når du spør chatten, sendes spørsmålet og det chatten trenger fra hytta (f.eks. wikien og bookingene) til OpenAI for å lage svaret. OpenAI bruker ikke dette til å trene modellene sine, og sletter det etter inntil 30 dager.',
    ],
  },
  { title: 'Informasjonskapsler', body: ['Vi bruker bare én informasjonskapsel: den som holder deg innlogget. Appen lagrer også innstillinger og innhold på enheten din, så den virker uten nett. Ingen sporing eller analyseverktøy.'] },
  {
    title: 'Hvor lenge',
    body: [
      'Data lagres så lenge kontoen og hytta finnes. Sikkerhetskopier tas daglig og slettes etter 14 dager.',
      'Under «Min profil» kan du forlate hytter og slette kontoen din. Hytter der bare du er med, slettes da med alt innhold. I hytter du deler med andre blir det du har skrevet stående under navnet ditt; eierne kan slette det.',
    ],
  },
  {
    title: 'Vilkår',
    body: [
      'Hyttebok er i utvikling og leveres som den er. Vi gjør vårt beste for at den skal være oppe og at data ikke går tapt, men kan ikke garantere det. Last gjerne ned en kopi av hytta av og til (under Tilgang).',
      'Du er ansvarlig for det du legger inn, og at du har lov til å dele bilder av andre. Vi kan stenge kontoer som misbrukes. Vilkårene kan endres; vesentlige endringer sier vi fra om i appen.',
    ],
  },
]

const EN = [
  { title: 'Who runs it', body: ['Hyttebok is made and run by Ravnsmark. For questions about privacy, or if you want something deleted, use “Give feedback” in the app\'s menu.'] },
  {
    title: 'What we store',
    body: [
      'Your account: name and email address, and your password as a secure hash (never the password itself). If you log in with Google, we also get your name, email and profile picture from Google.',
      'What you add to a cabin: bookings, the cabin book, photos, lists, tasks, expenses, forum and wiki. It\'s linked to the names you type in.',
      'Notifications: if you turn them on, we store the address your phone or browser gives us for sending them, and which notifications you want.',
      'Feedback you send us, and a log of access changes (who invited, removed or changed roles).',
    ],
  },
  {
    title: 'Who sees it',
    body: [
      'A cabin\'s content is only visible to its members. The owners decide who is in, and can download everything in the cabin.',
      'We never sell data or use it for advertising. We only look at data to run the service and to help, for example when you ask for help.',
      'The AI chat is only on in cabins where it has been switched on. When you ask it something, the question and what the chat needs from the cabin (e.g. the wiki and bookings) are sent to OpenAI to write the answer. OpenAI doesn\'t use this to train its models, and deletes it after at most 30 days.',
    ],
  },
  { title: 'Cookies', body: ['We use one cookie only: the one that keeps you logged in. The app also stores settings and content on your device so it works offline. No tracking or analytics.'] },
  {
    title: 'How long',
    body: [
      'Data is kept as long as the account and the cabin exist. Backups are made daily and deleted after 14 days.',
      'Under “My profile” you can leave cabins and delete your account. Cabins where you\'re the only member are then deleted with everything in them. In cabins you share, what you wrote stays under your name; the owners can delete it.',
    ],
  },
  {
    title: 'Terms',
    body: [
      'Hyttebok is in development and provided as is. We do our best to keep it up and not lose data, but can\'t guarantee it. Do download a copy of your cabin now and then (under Access).',
      'You\'re responsible for what you add, and for being allowed to share photos of others. We may close accounts that are misused. These terms may change; we\'ll say so in the app if anything important changes.',
    ],
  },
]
</script>

<template>
  <article class="mx-auto max-w-2xl py-10">
    <a href="/" class="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-zinc-600 underline-offset-4 hover:underline"><i class="pi pi-arrow-left text-xs" />Hyttebok</a>
    <h1 class="mt-4 text-3xl font-semibold tracking-tight">{{ t('privacy.title') }}</h1>
    <p class="mt-2 text-sm text-zinc-500">{{ t('privacy.updated') }}</p>
    <section v-for="s in sections" :key="s.title" class="mt-8">
      <h2 class="text-lg font-semibold">{{ s.title }}</h2>
      <p v-for="(p, i) in s.body" :key="i" class="mt-2 leading-relaxed text-zinc-700">{{ p }}</p>
    </section>
  </article>
</template>
