import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import en from './locales/en/translation.json';
import nb from './locales/nb/translation.json';
import nn from './locales/nn/translation.json';
import se from './locales/se/translation.json';
import tl from './locales/tl/translation.json';

export const languageNames: Record<string, string> = {
  en: 'English',
  nb: 'Norsk bokmal',
  nn: 'Norsk nynorsk',
  se: 'Davvisamigiella',
  tl: 'Tagalog',
};

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      nb: { translation: nb },
      nn: { translation: nn },
      se: { translation: se },
      tl: { translation: tl },
    },
    fallbackLng: 'en',
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
    },
    interpolation: {
      escapeValue: false,
    },
    react: {
      useSuspense: false,
    },
  });

export default i18n;
