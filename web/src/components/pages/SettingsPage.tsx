import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, Link } from 'react-router-dom';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { logout } from '../../store/authSlice';
import { setLists } from '../../store/listsSlice';
import { LoadingSpinner, ErrorAlert } from '../common';
import { apiFetch } from '../../services/api';
import { languageNames } from '../../i18n/i18n';

interface Preferences {
  smartParsingEnabled: boolean;
  defaultQuantity: number;
  theme: 'system' | 'light' | 'dark';
  notifyNewList: boolean;
  notifyItemAdded: boolean;
  notifyNewComment: boolean;
}

const KEYBOARD_SHORTCUTS_KEYS = [
  { keys: ['/', 'Ctrl', 'K'], actionKey: 'settings.shortcut.focusQuickAdd' },
  { keys: ['Enter'], actionKey: 'settings.shortcut.addItem' },
  { keys: ['Escape'], actionKey: 'settings.shortcut.closeModal' },
  { keys: ['Space'], actionKey: 'settings.shortcut.toggleItem' },
  { keys: ['g', 'h'], actionKey: 'settings.shortcut.goHome' },
  { keys: ['g', 'l'], actionKey: 'settings.shortcut.goLists' },
  { keys: ['g', 's'], actionKey: 'settings.shortcut.goSettings' },
  { keys: ['?'], actionKey: 'settings.shortcut.showShortcuts' },
] as const;

export default function SettingsPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user, token } = useAppSelector((state) => state.auth);
  const { items: lists } = useAppSelector((state) => state.lists);

  const [preferences, setPreferences] = useState<Preferences | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Get pinned lists from the lists slice
  const pinnedLists = lists.filter((list) => list.isPinned);

  // Fetch preferences on mount
  useEffect(() => {
    const fetchPreferences = async () => {
      if (!token) return;

      setIsLoading(true);
      setError(null);

      try {
        const response = await apiFetch('/preferences');

        if (response.ok) {
          const data = await response.json();
          setPreferences(data);
        } else {
          setError('Failed to load preferences');
        }
      } catch (err) {
        console.error('Failed to fetch preferences:', err);
        setError('Failed to load preferences');
      } finally {
        setIsLoading(false);
      }
    };

    fetchPreferences();
  }, [token]);

  // Fetch lists to get pinned lists (if not already loaded)
  useEffect(() => {
    const fetchLists = async () => {
      if (!token || lists.length > 0) return;

      try {
        const response = await apiFetch('/lists');

        if (response.ok) {
          const data = await response.json();
          dispatch(setLists(data));
        }
      } catch (err) {
        console.error('Failed to fetch lists:', err);
      }
    };

    fetchLists();
  }, [token, lists.length, dispatch]);

  // Update a single preference
  const updatePreference = useCallback(
    async <K extends keyof Preferences>(key: K, value: Preferences[K]) => {
      if (!token || !preferences) return;

      // Apply theme changes immediately (optimistic)
      if (key === 'theme') {
        localStorage.setItem('theme', String(value));
        const prefersDark =
          value === 'dark' ||
          (value === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
        document.documentElement.classList.toggle('dark', prefersDark);
      }

      setPreferences((prev) => (prev ? { ...prev, [key]: value } : prev));
      setIsSaving(true);
      setSaveSuccess(false);
      setError(null);

      try {
        const response = await apiFetch('/preferences', {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ [key]: value }),
        });

        if (response.ok) {
          const data = await response.json();
          setPreferences(data);
          setSaveSuccess(true);
          setTimeout(() => setSaveSuccess(false), 2000);
        } else {
          setError('Failed to save preference');
        }
      } catch (err) {
        console.error('Failed to update preference:', err);
        setError('Failed to save preference');
      } finally {
        setIsSaving(false);
      }
    },
    [token, preferences]
  );

  // Handle unpin
  const handleUnpin = async (listId: string) => {
    if (!token) return;

    try {
      const response = await apiFetch(`/lists/${listId}/pin`, {
        method: 'DELETE',
      });

      if (response.ok) {
        // Update local state
        dispatch(
          setLists(lists.map((list) => (list.id === listId ? { ...list, isPinned: false } : list)))
        );
      }
    } catch (err) {
      console.error('Failed to unpin list:', err);
    }
  };

  // Handle sign out
  const handleSignOut = () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (token) {
      apiFetch('/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: refreshToken ? JSON.stringify({ refreshToken }) : undefined,
      }).catch(() => {
        // Ignore logout API errors
      });
    }
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    dispatch(logout());
    navigate('/login');
  };

  // Handle language change
  const handleLanguageChange = (lng: string) => {
    i18n.changeLanguage(lng);
  };

  return (
    <div data-testid="settings-page">
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
          {t('settings.title')}
        </h2>
        <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{t('settings.subtitle')}</p>
      </div>

      {error && (
        <div className="mb-6">
          <ErrorAlert message={error} />
        </div>
      )}

      {saveSuccess && (
        <div
          className="mb-6 rounded-md bg-green-50 p-4 text-sm text-green-700 dark:bg-green-900/30 dark:text-green-300"
          data-testid="save-success"
        >
          {t('settings.preferencesSaved')}
        </div>
      )}

      <div className="space-y-6">
        {/* Account Section */}
        <section
          className="rounded-lg bg-white p-6 shadow dark:bg-gray-800 dark:shadow-gray-900/20"
          aria-labelledby="account-heading"
          data-testid="account-section"
        >
          <h3 id="account-heading" className="text-lg font-medium text-gray-900 dark:text-white">
            {t('settings.account')}
          </h3>
          <dl className="mt-4 space-y-3">
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500 dark:text-gray-400">
                {t('settings.displayName')}
              </dt>
              <dd
                className="text-sm font-medium text-gray-900 dark:text-white"
                data-testid="user-display-name"
              >
                {user?.displayName || '-'}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-sm text-gray-500 dark:text-gray-400">{t('settings.email')}</dt>
              <dd
                className="text-sm font-medium text-gray-900 dark:text-white"
                data-testid="user-email"
              >
                {user?.email || '-'}
              </dd>
            </div>
          </dl>
          <div className="mt-6">
            <button
              type="button"
              onClick={handleSignOut}
              className="w-full rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-red-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600"
              data-testid="sign-out-button"
            >
              {t('common.signOut')}
            </button>
          </div>
        </section>

        {/* Language Section */}
        <section
          className="rounded-lg bg-white p-6 shadow dark:bg-gray-800 dark:shadow-gray-900/20"
          aria-labelledby="language-heading"
          data-testid="language-section"
        >
          <h3 id="language-heading" className="text-lg font-medium text-gray-900 dark:text-white">
            {t('settings.language')}
          </h3>
          <div className="mt-4 flex items-center justify-between">
            <div>
              <label
                htmlFor="language"
                className="text-sm font-medium text-gray-900 dark:text-white"
                id="language-label"
              >
                {t('settings.languageLabel')}
              </label>
              <p className="text-sm text-gray-500 dark:text-gray-400" id="language-description">
                {t('settings.languageDescription')}
              </p>
            </div>
            <select
              id="language"
              value={i18n.language}
              onChange={(e) => handleLanguageChange(e.target.value)}
              className="rounded-md border-gray-300 py-1.5 pl-3 pr-8 text-sm focus:border-indigo-500 focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              aria-labelledby="language-label"
              aria-describedby="language-description"
              data-testid="language-select"
            >
              {Object.entries(languageNames).map(([code, name]) => (
                <option key={code} value={code}>
                  {name}
                </option>
              ))}
            </select>
          </div>
        </section>

        {/* Preferences Section */}
        <section
          className="rounded-lg bg-white p-6 shadow dark:bg-gray-800 dark:shadow-gray-900/20"
          aria-labelledby="preferences-heading"
          data-testid="preferences-section"
        >
          <h3
            id="preferences-heading"
            className="text-lg font-medium text-gray-900 dark:text-white"
          >
            {t('settings.preferences')}
          </h3>

          {isLoading ? (
            <div className="mt-4 flex justify-center">
              <LoadingSpinner size="sm" />
            </div>
          ) : preferences ? (
            <div className="mt-4 space-y-5">
              {/* Smart Parsing Toggle */}
              <div className="flex items-center justify-between">
                <div>
                  <label
                    htmlFor="smart-parsing"
                    className="text-sm font-medium text-gray-900 dark:text-white"
                    id="smart-parsing-label"
                  >
                    {t('settings.smartParsing')}
                  </label>
                  <p
                    className="text-sm text-gray-500 dark:text-gray-400"
                    id="smart-parsing-description"
                  >
                    {t('settings.smartParsingDescription')}
                  </p>
                </div>
                <button
                  id="smart-parsing"
                  type="button"
                  role="switch"
                  aria-checked={preferences.smartParsingEnabled}
                  aria-labelledby="smart-parsing-label"
                  aria-describedby="smart-parsing-description"
                  onClick={() =>
                    updatePreference('smartParsingEnabled', !preferences.smartParsingEnabled)
                  }
                  disabled={isSaving}
                  className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-2 disabled:opacity-50 ${
                    preferences.smartParsingEnabled
                      ? 'bg-indigo-600'
                      : 'bg-gray-200 dark:bg-gray-600'
                  }`}
                  data-testid="smart-parsing-toggle"
                >
                  <span
                    className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                      preferences.smartParsingEnabled ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              {/* Default Quantity Selector */}
              <div className="flex items-center justify-between">
                <div>
                  <label
                    htmlFor="default-quantity"
                    className="text-sm font-medium text-gray-900 dark:text-white"
                    id="default-quantity-label"
                  >
                    {t('settings.defaultQuantity')}
                  </label>
                  <p
                    className="text-sm text-gray-500 dark:text-gray-400"
                    id="default-quantity-description"
                  >
                    {t('settings.defaultQuantityDescription')}
                  </p>
                </div>
                <select
                  id="default-quantity"
                  value={preferences.defaultQuantity}
                  onChange={(e) => updatePreference('defaultQuantity', Number(e.target.value))}
                  disabled={isSaving}
                  className="rounded-md border-gray-300 py-1.5 pl-3 pr-8 text-sm focus:border-indigo-500 focus:ring-indigo-500 disabled:opacity-50 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                  aria-labelledby="default-quantity-label"
                  aria-describedby="default-quantity-description"
                  data-testid="default-quantity-select"
                >
                  <option value={1}>1</option>
                  <option value={2}>2</option>
                  <option value={3}>3</option>
                  <option value={4}>4</option>
                  <option value={5}>5</option>
                  <option value={6}>6</option>
                  <option value={10}>10</option>
                  <option value={12}>12</option>
                </select>
              </div>

              {/* Theme Selector */}
              <div className="flex items-center justify-between">
                <div>
                  <label
                    htmlFor="theme"
                    className="text-sm font-medium text-gray-900 dark:text-white"
                    id="theme-label"
                  >
                    {t('settings.theme')}
                  </label>
                  <p className="text-sm text-gray-500 dark:text-gray-400" id="theme-description">
                    {t('settings.themeDescription')}
                  </p>
                </div>
                <select
                  id="theme"
                  value={preferences.theme}
                  onChange={(e) =>
                    updatePreference('theme', e.target.value as 'system' | 'light' | 'dark')
                  }
                  disabled={isSaving}
                  className="rounded-md border-gray-300 py-1.5 pl-3 pr-8 text-sm focus:border-indigo-500 focus:ring-indigo-500 disabled:opacity-50 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                  aria-labelledby="theme-label"
                  aria-describedby="theme-description"
                  data-testid="theme-select"
                >
                  <option value="system">{t('settings.themeSystem')}</option>
                  <option value="light">{t('settings.themeLight')}</option>
                  <option value="dark">{t('settings.themeDark')}</option>
                </select>
              </div>
            </div>
          ) : (
            <p className="mt-4 text-sm text-gray-500 dark:text-gray-400">
              {t('settings.unableToLoadPreferences')}
            </p>
          )}
        </section>

        {/* Notifications Section */}
        <section
          className="rounded-lg bg-white p-6 shadow dark:bg-gray-800 dark:shadow-gray-900/20"
          aria-labelledby="notifications-heading"
          data-testid="notifications-section"
        >
          <h3
            id="notifications-heading"
            className="text-lg font-medium text-gray-900 dark:text-white"
          >
            {t('settings.notifications')}
          </h3>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('settings.notificationsSubtitle')}
          </p>

          {isLoading ? (
            <div className="mt-4 flex justify-center">
              <LoadingSpinner size="sm" />
            </div>
          ) : preferences ? (
            <div className="mt-4 space-y-5">
              {/* Notify New List Toggle */}
              <div className="flex items-center justify-between">
                <div>
                  <label
                    htmlFor="notify-new-list"
                    className="text-sm font-medium text-gray-900 dark:text-white"
                    id="notify-new-list-label"
                  >
                    {t('settings.notifyNewList')}
                  </label>
                  <p
                    className="text-sm text-gray-500 dark:text-gray-400"
                    id="notify-new-list-description"
                  >
                    {t('settings.notifyNewListDescription')}
                  </p>
                </div>
                <button
                  id="notify-new-list"
                  type="button"
                  role="switch"
                  aria-checked={preferences.notifyNewList}
                  aria-labelledby="notify-new-list-label"
                  aria-describedby="notify-new-list-description"
                  onClick={() => updatePreference('notifyNewList', !preferences.notifyNewList)}
                  disabled={isSaving}
                  className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-2 disabled:opacity-50 ${
                    preferences.notifyNewList ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600'
                  }`}
                  data-testid="notify-new-list-toggle"
                >
                  <span
                    className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                      preferences.notifyNewList ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              {/* Notify Item Added Toggle */}
              <div className="flex items-center justify-between">
                <div>
                  <label
                    htmlFor="notify-item-added"
                    className="text-sm font-medium text-gray-900 dark:text-white"
                    id="notify-item-added-label"
                  >
                    {t('settings.notifyItemAdded')}
                  </label>
                  <p
                    className="text-sm text-gray-500 dark:text-gray-400"
                    id="notify-item-added-description"
                  >
                    {t('settings.notifyItemAddedDescription')}
                  </p>
                </div>
                <button
                  id="notify-item-added"
                  type="button"
                  role="switch"
                  aria-checked={preferences.notifyItemAdded}
                  aria-labelledby="notify-item-added-label"
                  aria-describedby="notify-item-added-description"
                  onClick={() => updatePreference('notifyItemAdded', !preferences.notifyItemAdded)}
                  disabled={isSaving}
                  className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-2 disabled:opacity-50 ${
                    preferences.notifyItemAdded ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600'
                  }`}
                  data-testid="notify-item-added-toggle"
                >
                  <span
                    className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                      preferences.notifyItemAdded ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              {/* Notify New Comment Toggle */}
              <div className="flex items-center justify-between">
                <div>
                  <label
                    htmlFor="notify-new-comment"
                    className="text-sm font-medium text-gray-900 dark:text-white"
                    id="notify-new-comment-label"
                  >
                    {t('settings.notifyNewComment')}
                  </label>
                  <p
                    className="text-sm text-gray-500 dark:text-gray-400"
                    id="notify-new-comment-description"
                  >
                    {t('settings.notifyNewCommentDescription')}
                  </p>
                </div>
                <button
                  id="notify-new-comment"
                  type="button"
                  role="switch"
                  aria-checked={preferences.notifyNewComment}
                  aria-labelledby="notify-new-comment-label"
                  aria-describedby="notify-new-comment-description"
                  onClick={() =>
                    updatePreference('notifyNewComment', !preferences.notifyNewComment)
                  }
                  disabled={isSaving}
                  className={`relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-600 focus:ring-offset-2 disabled:opacity-50 ${
                    preferences.notifyNewComment ? 'bg-indigo-600' : 'bg-gray-200 dark:bg-gray-600'
                  }`}
                  data-testid="notify-new-comment-toggle"
                >
                  <span
                    className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                      preferences.notifyNewComment ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            </div>
          ) : (
            <p className="mt-4 text-sm text-gray-500 dark:text-gray-400">
              {t('settings.unableToLoadPreferences')}
            </p>
          )}
        </section>

        {/* Pinned Lists Section */}
        <section
          className="rounded-lg bg-white p-6 shadow dark:bg-gray-800 dark:shadow-gray-900/20"
          aria-labelledby="pinned-lists-heading"
          data-testid="pinned-lists-section"
        >
          <h3
            id="pinned-lists-heading"
            className="text-lg font-medium text-gray-900 dark:text-white"
          >
            {t('settings.pinnedLists')}
          </h3>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('settings.pinnedListsSubtitle')}
          </p>

          {pinnedLists.length > 0 ? (
            <ul
              className="mt-4 divide-y divide-gray-200 dark:divide-gray-700"
              data-testid="pinned-lists"
            >
              {pinnedLists.map((list) => (
                <li
                  key={list.id}
                  className="flex items-center justify-between py-3"
                  data-testid="pinned-list-item"
                >
                  <Link
                    to={`/lists/${list.id}`}
                    className="text-sm font-medium text-gray-900 hover:text-indigo-600 dark:text-white"
                  >
                    {list.name}
                  </Link>
                  <button
                    type="button"
                    onClick={() => handleUnpin(list.id)}
                    className="rounded-md px-2 py-1 text-sm text-gray-500 hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
                    aria-label={`Unpin ${list.name}`}
                    data-testid="unpin-button"
                  >
                    {t('common.unpin')}
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p
              className="mt-4 text-sm text-gray-500 dark:text-gray-400"
              data-testid="no-pinned-lists"
            >
              {t('settings.noPinnedLists')}
            </p>
          )}
        </section>

        {/* Keyboard Shortcuts Section */}
        <section
          className="rounded-lg bg-white p-6 shadow dark:bg-gray-800 dark:shadow-gray-900/20"
          aria-labelledby="shortcuts-heading"
          data-testid="keyboard-shortcuts-section"
        >
          <h3 id="shortcuts-heading" className="text-lg font-medium text-gray-900 dark:text-white">
            {t('settings.keyboardShortcuts')}
          </h3>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {t('settings.keyboardShortcutsSubtitle')}
          </p>

          <div className="mt-4 overflow-hidden rounded-md border border-gray-200 dark:border-gray-700">
            <table
              className="min-w-full divide-y divide-gray-200 dark:divide-gray-700"
              data-testid="shortcuts-table"
            >
              <thead className="bg-gray-50 dark:bg-gray-900">
                <tr>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
                  >
                    {t('settings.keysColumn')}
                  </th>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400"
                  >
                    {t('settings.actionColumn')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-700 dark:bg-gray-800">
                {KEYBOARD_SHORTCUTS_KEYS.map(({ keys, actionKey }) => (
                  <tr key={actionKey}>
                    <td className="whitespace-nowrap px-4 py-3">
                      <div className="flex gap-1">
                        {keys.map((key, index) => (
                          <span key={index}>
                            <kbd className="inline-flex items-center rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300">
                              {key}
                            </kbd>
                            {index < keys.length - 1 && (
                              <span className="mx-1 text-gray-400 dark:text-gray-500">+</span>
                            )}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700 dark:text-gray-200">
                      {t(actionKey)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}
