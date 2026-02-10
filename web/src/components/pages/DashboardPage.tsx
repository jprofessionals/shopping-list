import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { setLists, setLoading as setListsLoading, setError } from '../../store/listsSlice';
import PinnedListsRow from '../dashboard/PinnedListsRow';
import NeedsAttentionList from '../dashboard/NeedsAttentionList';

export default function DashboardPage() {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const { user, token } = useAppSelector((state) => state.auth);
  const { items: households } = useAppSelector((state) => state.households);
  const { items: lists, isLoading, error } = useAppSelector((state) => state.lists);

  // Fetch lists on mount
  useEffect(() => {
    const fetchLists = async () => {
      if (!token) return;

      dispatch(setListsLoading(true));
      dispatch(setError(null));

      try {
        const response = await fetch('http://localhost:8080/lists', {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (response.ok) {
          const data = await response.json();
          dispatch(setLists(data));
        } else {
          dispatch(setError('Failed to load lists'));
        }
      } catch (err) {
        console.error('Failed to fetch lists:', err);
        dispatch(setError('Failed to load lists'));
      } finally {
        dispatch(setListsLoading(false));
      }
    };

    fetchLists();
  }, [token, dispatch]);

  // Build household name lookup for display
  const householdNames = useMemo(() => {
    return households.reduce(
      (acc, h) => {
        acc[h.id] = h.name;
        return acc;
      },
      {} as Record<string, string>
    );
  }, [households]);

  return (
    <div data-testid="dashboard-page">
      {/* Welcome header */}
      <div className="mb-6">
        <h2
          className="text-xl font-semibold text-gray-900 dark:text-white"
          data-testid="welcome-message"
        >
          {t('dashboard.welcomeBack', { name: user?.displayName || 'User' })}
        </h2>
        <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{t('dashboard.overview')}</p>
      </div>

      {/* Error state */}
      {error && (
        <div
          className="mb-6 rounded-lg bg-red-50 p-4 text-red-700 dark:bg-red-900/30 dark:text-red-300"
          role="alert"
          data-testid="error-message"
        >
          {error}
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div
          className="mb-6 text-center text-gray-500 dark:text-gray-400"
          data-testid="loading-indicator"
        >
          {t('dashboard.loadingLists')}
        </div>
      )}

      {/* Pinned lists section */}
      <section className="mb-8" aria-labelledby="pinned-lists-heading">
        <h3
          id="pinned-lists-heading"
          className="text-lg font-medium text-gray-900 dark:text-white mb-3"
        >
          {t('dashboard.pinnedLists')}
        </h3>
        <PinnedListsRow lists={lists} householdNames={householdNames} />
      </section>

      {/* Needs attention section */}
      <section className="mb-8" aria-labelledby="needs-attention-heading">
        <h3
          id="needs-attention-heading"
          className="text-lg font-medium text-gray-900 dark:text-white mb-3"
        >
          {t('dashboard.needsAttention')}
        </h3>
        <NeedsAttentionList lists={lists} householdNames={householdNames} limit={5} />
      </section>

      {/* Quick actions footer */}
      <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
        <Link
          to="/lists"
          className="flex items-center justify-center rounded-lg bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 transition-colors"
        >
          {t('dashboard.viewAllLists')}
        </Link>
        <Link
          to="/households"
          className="flex items-center justify-center rounded-lg bg-gray-100 px-4 py-3 text-sm font-semibold text-gray-700 hover:bg-gray-200 transition-colors dark:bg-gray-900 dark:text-gray-200 dark:hover:bg-gray-700"
        >
          {t('dashboard.manageHouseholds')}
        </Link>
      </div>
    </div>
  );
}
