import { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { type TFunction } from 'i18next';
import { Link } from 'react-router-dom';
import { type ShoppingList } from '../../store/listsSlice';
import Badge from '../common/Badge';

interface ListCardProps {
  list: ShoppingList;
  householdName?: string;
  onPin?: (listId: string) => void;
  onUnpin?: (listId: string) => void;
}

function formatRelativeTime(timestamp: string, t: TFunction): string {
  const now = new Date();
  const date = new Date(timestamp);
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return t('listCard.time.justNow');
  if (diffMins < 60) return t('listCard.time.minutesAgo', { count: diffMins });
  if (diffHours < 24) return t('listCard.time.hoursAgo', { count: diffHours });
  if (diffDays === 1) return t('listCard.time.yesterday');
  if (diffDays < 7) return t('listCard.time.daysAgo', { count: diffDays });
  return date.toLocaleDateString();
}

function formatActivityMessage(
  type: string,
  actorName: string,
  itemName: string | undefined,
  t: TFunction
): string {
  switch (type) {
    case 'item:added':
      return itemName
        ? t('listCard.activity.itemAdded', { actor: actorName, item: itemName })
        : t('listCard.activity.itemAddedDefault', { actor: actorName });
    case 'item:checked':
      return itemName
        ? t('listCard.activity.itemChecked', { actor: actorName, item: itemName })
        : t('listCard.activity.itemCheckedDefault', { actor: actorName });
    case 'item:unchecked':
      return itemName
        ? t('listCard.activity.itemUnchecked', { actor: actorName, item: itemName })
        : t('listCard.activity.itemUncheckedDefault', { actor: actorName });
    case 'item:removed':
      return itemName
        ? t('listCard.activity.itemRemoved', { actor: actorName, item: itemName })
        : t('listCard.activity.itemRemovedDefault', { actor: actorName });
    case 'list:created':
      return t('listCard.activity.listCreated', { actor: actorName });
    case 'list:updated':
      return t('listCard.activity.listUpdated', { actor: actorName });
    default:
      return t('listCard.activity.madeChanges', { actor: actorName });
  }
}

export default function ListCard({ list, householdName, onPin, onUnpin }: ListCardProps) {
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(false);
  const [longPressTimer, setLongPressTimer] = useState<ReturnType<typeof setTimeout> | null>(null);

  const {
    id,
    name,
    isPersonal,
    itemCount = 0,
    uncheckedCount = 0,
    previewItems = [],
    lastActivity,
    isPinned = false,
  } = list;

  const statusText =
    itemCount === 0
      ? t('listCard.emptyList')
      : uncheckedCount === 0
        ? t('listCard.allDone')
        : t('listCard.remaining', { count: uncheckedCount });

  const statusColor =
    itemCount === 0
      ? 'text-gray-400 dark:text-gray-500'
      : uncheckedCount === 0
        ? 'text-green-600'
        : 'text-gray-600 dark:text-gray-300';

  // Show 5 items when expanded, 3 when collapsed
  const displayItems = previewItems.slice(0, isExpanded ? 5 : 3);

  const handleMouseEnter = useCallback(() => setIsExpanded(true), []);
  const handleMouseLeave = useCallback(() => setIsExpanded(false), []);

  const handleTouchStart = useCallback(() => {
    const timer = setTimeout(() => setIsExpanded(true), 500);
    setLongPressTimer(timer);
  }, []);

  const handleTouchEnd = useCallback(() => {
    if (longPressTimer) {
      clearTimeout(longPressTimer);
      setLongPressTimer(null);
    }
  }, [longPressTimer]);

  const handlePinClick = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (isPinned && onUnpin) {
        onUnpin(id);
      } else if (!isPinned && onPin) {
        onPin(id);
      }
    },
    [id, isPinned, onPin, onUnpin]
  );

  return (
    <Link
      to={`/lists/${id}`}
      className={`block rounded-lg bg-white p-4 shadow transition-all duration-200 dark:bg-gray-800 dark:shadow-gray-900/20 ${
        isExpanded ? 'shadow-lg scale-[1.02]' : 'hover:shadow-md'
      }`}
      data-testid="list-card"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {/* Header: Name + Badge + Pin indicator */}
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2 min-w-0 flex-1">
          <h3 className="font-medium text-gray-900 truncate dark:text-white">{name}</h3>
          <Badge variant={isPersonal ? 'default' : 'primary'}>
            {isPersonal ? t('common.private') : householdName || t('common.household')}
          </Badge>
        </div>
        {isPinned && (
          <span
            className="ml-2 flex-shrink-0 text-indigo-600"
            title={t('common.pinned')}
            data-testid="pin-indicator"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="h-5 w-5"
              aria-label={t('common.pinned')}
            >
              <path d="M14 4v5c0 1.12.37 2.16 1 3H9c.65-.86 1-1.9 1-3V4h4m3-2H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3V4h1c.55 0 1-.45 1-1s-.45-1-1-1z" />
            </svg>
          </span>
        )}
      </div>

      {/* Status line */}
      <p className={`mt-2 text-sm font-medium ${statusColor}`} data-testid="status-text">
        {statusText}
      </p>

      {/* Preview items */}
      {previewItems.length > 0 && (
        <ul className="mt-2 space-y-1" data-testid="preview-items">
          {displayItems.map((item, index) => (
            <li key={index} className="truncate text-sm text-gray-500 dark:text-gray-400">
              {item}
            </li>
          ))}
        </ul>
      )}

      {/* Last activity */}
      {lastActivity && (
        <p className="mt-3 text-xs text-gray-400 dark:text-gray-500" data-testid="last-activity">
          {formatActivityMessage(
            lastActivity.type,
            lastActivity.actorName,
            lastActivity.itemName,
            t
          )}{' '}
          - {formatRelativeTime(lastActivity.timestamp, t)}
        </p>
      )}

      {/* Quick actions (visible when expanded) */}
      {isExpanded && (onPin || onUnpin) && (
        <div
          className="mt-3 flex gap-2 border-t pt-3 dark:border-gray-700"
          data-testid="quick-actions"
          onClick={(e) => e.preventDefault()}
        >
          <button
            onClick={handlePinClick}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
            data-testid="pin-action"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="currentColor"
              className="h-4 w-4"
            >
              <path d="M14 4v5c0 1.12.37 2.16 1 3H9c.65-.86 1-1.9 1-3V4h4m3-2H7c-.55 0-1 .45-1 1s.45 1 1 1h1v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3V4h1c.55 0 1-.45 1-1s-.45-1-1-1z" />
            </svg>
            {isPinned ? t('common.unpin') : t('common.pin')}
          </button>
        </div>
      )}
    </Link>
  );
}
