import { useTranslation } from 'react-i18next';
import { useAppSelector } from '../../store/hooks';
import { LoadingSpinner, EmptyState } from '../common';
import ListCard from '../dashboard/ListCard';

interface ShoppingListsPageProps {
  onSelectList?: (id: string) => void;
  onCreateClick: () => void;
  onPin?: (listId: string) => void;
  onUnpin?: (listId: string) => void;
  onDelete?: (listId: string) => void;
}

export default function ShoppingListsPage({
  onCreateClick,
  onPin,
  onUnpin,
  onDelete,
}: ShoppingListsPageProps) {
  const { t } = useTranslation();
  const { items: lists, isLoading } = useAppSelector((state) => state.lists);
  const { items: households } = useAppSelector((state) => state.households);

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  // Group lists: personal lists first, then by household
  const personalLists = lists.filter((list) => list.isPersonal);
  const householdListsMap = new Map<string, typeof lists>();

  lists
    .filter((list) => !list.isPersonal && list.householdId)
    .forEach((list) => {
      const householdId = list.householdId!;
      if (!householdListsMap.has(householdId)) {
        householdListsMap.set(householdId, []);
      }
      householdListsMap.get(householdId)!.push(list);
    });

  const getHouseholdName = (householdId: string): string => {
    const household = households.find((h) => h.id === householdId);
    return household?.name || 'Unknown Household';
  };

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
          {t('shoppingLists.title')}
        </h2>
        <button
          onClick={onCreateClick}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          {t('shoppingLists.createList')}
        </button>
      </div>

      {lists.length === 0 ? (
        <EmptyState
          title={t('shoppingLists.noListsYet')}
          description={t('shoppingLists.noListsDescription')}
          action={
            <button
              onClick={onCreateClick}
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
            >
              {t('shoppingLists.createFirstList')}
            </button>
          }
        />
      ) : (
        <div className="space-y-8">
          {/* Personal Lists Section */}
          {personalLists.length > 0 && (
            <div>
              <h3 className="mb-4 text-lg font-medium text-gray-900 dark:text-white">
                {t('shoppingLists.personalLists')}
              </h3>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {personalLists.map((list) => (
                  <ListCard
                    key={list.id}
                    list={list}
                    onPin={onPin}
                    onUnpin={onUnpin}
                    onDelete={onDelete}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Household Lists Sections */}
          {Array.from(householdListsMap.entries()).map(([householdId, householdLists]) => (
            <div key={householdId}>
              <h3 className="mb-4 text-lg font-medium text-gray-900 dark:text-white">
                {getHouseholdName(householdId)}
              </h3>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {householdLists.map((list) => (
                  <ListCard
                    key={list.id}
                    list={list}
                    householdName={getHouseholdName(householdId)}
                    onPin={onPin}
                    onUnpin={onUnpin}
                    onDelete={onDelete}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
