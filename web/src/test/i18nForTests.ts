import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from '../i18n/locales/en/translation.json';

const i18nForTests = i18n.createInstance();

i18nForTests.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: { translation: en },
  },
  interpolation: {
    escapeValue: false,
  },
  react: {
    useSuspense: false,
  },
});

export default i18nForTests;
