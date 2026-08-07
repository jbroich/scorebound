import type { Locale } from '../i18n/messages'
import { useLocale } from '../i18n/useLocale'

export function LanguageSwitcher({ compact = false }: { compact?: boolean }) {
  const { locale, setLocale, t } = useLocale()
  const options: Array<{ value: Locale; label: string }> = [
    { value: 'en', label: compact ? 'EN' : t('languageEnglish') },
    { value: 'de', label: compact ? 'DE' : t('languageGerman') },
  ]
  return (
    <div className="segmented-control" aria-label={t('languageLabel')}>
      {options.map((option) => (
        <button key={option.value} className="segmented-control__button" type="button"
          aria-pressed={locale === option.value} onClick={() => setLocale(option.value)}>
          {option.label}
        </button>
      ))}
    </div>
  )
}
