import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { type ShoppingList } from '../../store/listsSlice';

interface PinnedListsRowProps {
  lists: ShoppingList[];
  householdNames?: Record<string, string>;
}

interface CompactListCardProps {
  list: ShoppingList;
  householdName?: string;
}

function CompactListCard({ list, householdName }: CompactListCardProps) {
  const { t } = useTranslation();
  const { id, name, isPersonal, uncheckedCount = 0, itemCount = 0 } = list;

  const statusText =
    itemCount === 0
      ? t('listCard.empty')
      : uncheckedCount === 0
        ? t('listCard.allDone')
        : t('listCard.remaining', { count: uncheckedCount });

  const statusColor =
    itemCount === 0 ? 'text-gray-400' : uncheckedCount === 0 ? 'text-green-600' : 'text-indigo-600';

  return (
    <Link
      to={`/lists/${id}`}
      className="flex-shrink-0 w-40 rounded-lg bg-white p-3 shadow transition-shadow hover:shadow-md dark:bg-gray-800 dark:shadow-gray-900/20"
      data-testid="pinned-list-card"
    >
      {/* Pin icon */}
      <div className="flex items-start justify-between mb-1">
        <span className="text-indigo-600" data-testid="compact-pin-indicator">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="currentColor"
            className="h-4 w-4"
            aria-label={t('common.pinned')}
          >
            <path d="M14 4v5c0 1.12.37 2.16 1 3H9c.65-.86 1-1.9 1-3V4h4m3-2H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3V4h1c.55 0 1-.45 1-1s-.45-1-1-1z" />
          </svg>
        </span>
        <span className="text-xs text-gray-500 dark:text-gray-400">
          {isPersonal ? t('common.private') : householdName || t('common.household')}
        </span>
      </div>

      {/* List name */}
      <h4
        className="font-medium text-gray-900 truncate text-sm dark:text-white"
        data-testid="pinned-list-name"
      >
        {name}
      </h4>

      {/* Status */}
      <p className={`mt-1 text-xs font-medium ${statusColor}`} data-testid="pinned-list-status">
        {statusText}
      </p>
    </Link>
  );
}

export default function PinnedListsRow({ lists, householdNames = {} }: PinnedListsRowProps) {
  const { t } = useTranslation();
  const pinnedLists = lists.filter((list) => list.isPinned);

  if (pinnedLists.length === 0) {
    return (
      <div
        className="py-4 text-center text-sm text-gray-500 dark:text-gray-400"
        data-testid="pinned-lists-empty"
      >
        {t('dashboard.noPinnedLists')}
      </div>
    );
  }

  return (
    <div className="py-2" data-testid="pinned-lists-row">
      <div className="flex gap-3 overflow-x-auto pb-2 scrollbar-thin scrollbar-thumb-gray-300">
        {pinnedLists.map((list) => (
          <CompactListCard
            key={list.id}
            list={list}
            householdName={list.householdId ? householdNames[list.householdId] : undefined}
          />
        ))}
      </div>
    </div>
  );
}
