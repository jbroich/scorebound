export type Locale = 'en' | 'de'

const englishMessages = {
  backToManagement: 'Back to management',
  displayDescription: 'The high-contrast canvas is ready for the animated scoreboard.',
  displayEyebrow: 'Wall display',
  displayLinkDescription: 'Preview the permanent high-contrast surface',
  displayLinkTitle: 'Open wall display theme',
  displayThemeLabel: 'Dedicated display theme',
  displayTitle: 'The arena is getting ready.',
  displayWaiting: 'Waiting for scoreboard data',
  formattingDescription: 'Numbers and time follow the active language and configured timezone.',
  formattingTitle: 'Locale-aware details',
  languageDescription: 'This choice is saved in this browser for your next visit.',
  languageEnglish: 'English',
  languageGerman: 'Deutsch',
  languageLabel: 'Language',
  languageTitle: 'Choose your language',
  managementDescription: 'The shared foundation for scoring, administration, and the office wall display.',
  managementEyebrow: 'Interface foundation',
  managementThemeDescription: 'This surface automatically follows the light or dark setting of your device.',
  managementThemeTitle: 'System-aware theme',
  managementTitle: 'A clear stage for every score.',
  numberLabel: 'Example score',
  settingsLabel: 'Interface settings',
  timeLabel: 'Local time',
  timezoneLabel: 'Timezone',
} as const

export type MessageKey = keyof typeof englishMessages

const germanMessages: Record<MessageKey, string> = {
  backToManagement: 'Zur Verwaltung',
  displayDescription: 'Die kontrastreiche Fläche ist für die animierte Punkteanzeige vorbereitet.',
  displayEyebrow: 'Wandanzeige',
  displayLinkDescription: 'Die dauerhafte kontrastreiche Ansicht öffnen',
  displayLinkTitle: 'Theme der Wandanzeige ansehen',
  displayThemeLabel: 'Eigenständiges Display-Theme',
  displayTitle: 'Die Arena macht sich bereit.',
  displayWaiting: 'Warte auf Scoreboard-Daten',
  formattingDescription: 'Zahlen und Uhrzeiten richten sich nach Sprache und konfigurierter Zeitzone.',
  formattingTitle: 'Passende Formate',
  languageDescription: 'Diese Auswahl wird für den nächsten Besuch in diesem Browser gespeichert.',
  languageEnglish: 'English',
  languageGerman: 'Deutsch',
  languageLabel: 'Sprache',
  languageTitle: 'Sprache auswählen',
  managementDescription: 'Die gemeinsame Grundlage für Punktevergabe, Verwaltung und die Anzeige im Büro.',
  managementEyebrow: 'Oberflächen-Grundlage',
  managementThemeDescription: 'Diese Ansicht übernimmt automatisch den hellen oder dunklen Modus des Geräts.',
  managementThemeTitle: 'Systemabhängiges Theme',
  managementTitle: 'Eine klare Bühne für jeden Punkt.',
  numberLabel: 'Beispielpunktzahl',
  settingsLabel: 'Oberflächeneinstellungen',
  timeLabel: 'Lokale Zeit',
  timezoneLabel: 'Zeitzone',
}

export const messages: Record<Locale, Record<MessageKey, string>> = {
  en: englishMessages,
  de: germanMessages,
}

export const intlLocales: Record<Locale, string> = {
  en: 'en-GB',
  de: 'de-DE',
}
