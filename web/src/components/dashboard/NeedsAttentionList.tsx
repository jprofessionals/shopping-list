import { useTranslation } from 'react-i18next';
import { type ShoppingList } from '../../store/listsSlice';
import ListCard from './ListCard';

interface NeedsAttentionListProps {
  lists: ShoppingList[];
  householdNames?: Record<string, string>;
  limit?: number;
}

/**
 * Displays lists that have unchecked items, sorted by most recently updated.
 * Shows "All caught up!" when no lists need attention.
 */
export default function NeedsAttentionList({
  lists,
  householdNames = {},
  limit = 5,
}: NeedsAttentionListProps) {
  const { t } = useTranslation();
  // Filter lists with unchecked items and sort by updatedAt descending
  const listsNeedingAttention = lists
    .filter((list) => (list.uncheckedCount ?? 0) > 0)
    .sort((a, b) => {
      const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
      const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
      return dateB - dateA; // Descending (most recent first)
    })
    .slice(0, limit);

  if (listsNeedingAttention.length === 0) {
    return (
      <div className="py-8 text-center text-gray-500" data-testid="needs-attention-empty">
        <div className="text-2xl mb-2">{t('dashboard.allCaughtUp')}</div>
        <p className="text-sm">{t('dashboard.noListsNeedAttention')}</p>
      </div>
    );
  }

  return (
    <div className="space-y-3" data-testid="needs-attention-list">
      {listsNeedingAttention.map((list) => (
        <ListCard
          key={list.id}
          list={list}
          householdName={list.householdId ? householdNames[list.householdId] : undefined}
        />
      ))}
    </div>
  );
}
