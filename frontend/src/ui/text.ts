import type { Locale } from '../i18n/messages'

const en = {
  accounts: 'Accounts', activate: 'Activate', active: 'Active', add: 'Add', admin: 'Administration',
  amount: 'Amount', appSubtitle: 'Team scoring, without the spreadsheet archaeology.', back: 'Back',
  cancel: 'Cancel', cancellationReason: 'Why is this entry cancelled?', close: 'Close', closed: 'Closed',
  color: 'Colour', create: 'Create', dashboard: 'Overview', debit: 'Debit', description: 'Description',
  display: 'Wall display', empty: 'Nothing here yet.', end: 'End', error: 'That did not work. Please check the input.',
  firstName: 'First name', forcedPassword: 'Set your permanent password',
  forcedPasswordCopy: 'This temporary password can only be used to choose a permanent one.',
  inactive: 'Inactive', language: 'Language', lastName: 'Last name', loading: 'Loading Scorebound…',
  login: 'Sign in', loginCopy: 'Use your personal account to enter and manage scores.', logout: 'Sign out',
  member: 'Member', members: 'Members', name: 'Name', navigation: 'Navigation', newPassword: 'New password',
  noActivePeriod: 'This scoreboard has no active period.', overviewCopy: 'Everything needed for today’s league at a glance.',
  password: 'Password', period: 'Period', periods: 'Periods', quickValues: 'Quick values', reason: 'Reason',
  reopen: 'Reopen', role: 'Roles', save: 'Save', scheduled: 'Scheduled', score: 'Score', scoreboards: 'Scoreboards',
  scorerAssignments: 'Scoring access', select: 'Select…', selectedTeams: 'Teams in future periods',
  shortName: 'Short name', start: 'Start', status: 'Status', standings: 'Standings', team: 'Team', teams: 'Teams',
  temporaryPassword: 'Temporary password — copy it now', transactions: 'Recent activity', username: 'Username',
  welcome: 'Good to see you', winner: 'Winner', currentPassword: 'Current temporary password',
  target: 'Target', credit: 'Credit', customAmount: 'Custom', submitScore: 'Book score',
  memberTarget: 'Individual member', teamTarget: 'Whole team', manageHint: 'Create, edit, assign and archive.',
  scoreboardHint: 'Configure teams and the current competition period.', scoreHint: 'Fast enough for a phone in one hand.',
  accountHint: 'Roles, member links and scoreboard access.', changeTeam: 'Change team', enabled: 'Enabled',
} as const

type UiKey = keyof typeof en

const de: Record<UiKey, string> = {
  accounts: 'Accounts', activate: 'Aktivieren', active: 'Aktiv', add: 'Hinzufügen', admin: 'Verwaltung',
  amount: 'Punkte', appSubtitle: 'Teampunkte ohne Tabellen-Archäologie.', back: 'Zurück', cancel: 'Stornieren',
  cancellationReason: 'Warum wird dieser Eintrag storniert?', close: 'Schließen', closed: 'Geschlossen',
  color: 'Farbe', create: 'Erstellen', dashboard: 'Übersicht', debit: 'Abzug', description: 'Beschreibung',
  display: 'Wandanzeige', empty: 'Hier ist noch nichts.', end: 'Ende', error: 'Das hat nicht geklappt. Bitte Eingaben prüfen.',
  firstName: 'Vorname', forcedPassword: 'Dauerhaftes Passwort festlegen',
  forcedPasswordCopy: 'Dieses temporäre Passwort dient nur dazu, ein dauerhaftes festzulegen.',
  inactive: 'Inaktiv', language: 'Sprache', lastName: 'Nachname', loading: 'Scorebound wird geladen…',
  login: 'Anmelden', loginCopy: 'Melde dich mit deinem persönlichen Account an.', logout: 'Abmelden',
  member: 'Azubi', members: 'Azubis', name: 'Name', navigation: 'Navigation', newPassword: 'Neues Passwort',
  noActivePeriod: 'Dieses Scoreboard hat keinen aktiven Zeitraum.', overviewCopy: 'Alles für die aktuelle Liga auf einen Blick.',
  password: 'Passwort', period: 'Zeitraum', periods: 'Zeiträume', quickValues: 'Schnellwerte', reason: 'Grund',
  reopen: 'Wieder öffnen', role: 'Rollen', save: 'Speichern', scheduled: 'Geplant', score: 'Punkte vergeben',
  scoreboards: 'Scoreboards', scorerAssignments: 'Punkte-Zuordnung', select: 'Auswählen…',
  selectedTeams: 'Teams in zukünftigen Zeiträumen', shortName: 'Kurzname', start: 'Start', status: 'Status',
  standings: 'Tabelle', team: 'Team', teams: 'Teams', temporaryPassword: 'Temporäres Passwort – jetzt kopieren',
  transactions: 'Letzte Aktivitäten', username: 'Benutzername', welcome: 'Schön, dass du da bist', winner: 'Gewinner',
  currentPassword: 'Aktuelles temporäres Passwort', target: 'Ziel', credit: 'Gutschrift', customAmount: 'Eigener Wert',
  submitScore: 'Punkte buchen', memberTarget: 'Einzelner Azubi', teamTarget: 'Ganzes Team',
  manageHint: 'Erstellen, bearbeiten, zuordnen und archivieren.', scoreboardHint: 'Teams und aktuellen Zeitraum konfigurieren.',
  scoreHint: 'Schnell genug für das Smartphone in einer Hand.', accountHint: 'Rollen, Azubi-Verknüpfung und Scoreboard-Zugriff.',
  changeTeam: 'Team wechseln', enabled: 'Aktiviert',
}

export function uiText(locale: Locale) {
  return locale === 'de' ? de : en
}

export type UiText = typeof en
