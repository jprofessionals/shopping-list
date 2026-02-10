import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAppSelector } from '../../store/hooks';
import { LoadingSpinner, EmptyState, Badge } from '../common';

interface HouseholdListProps {
  onCreateClick: () => void;
  onSelectHousehold?: (id: string) => void;
}

export default function HouseholdList({ onCreateClick, onSelectHousehold }: HouseholdListProps) {
  const { t } = useTranslation();
  const { items, isLoading } = useAppSelector((state) => state.households);

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900">{t('households.title')}</h2>
        <button
          onClick={onCreateClick}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
        >
          {t('households.createHousehold')}
        </button>
      </div>

      {items.length === 0 ? (
        <EmptyState
          title={t('households.noHouseholdsYet')}
          description={t('households.noHouseholdsDescription')}
          action={
            <button
              onClick={onCreateClick}
              className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
            >
              {t('households.createFirstHousehold')}
            </button>
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((household) => (
            <Link
              key={household.id}
              to={`/households/${household.id}`}
              onClick={onSelectHousehold ? () => onSelectHousehold(household.id) : undefined}
              className="block rounded-lg bg-white p-6 text-left shadow transition hover:shadow-md"
            >
              <div className="flex items-start justify-between">
                <h3 className="font-semibold text-gray-900">{household.name}</h3>
                {household.isOwner && <Badge variant="primary">{t('common.owner')}</Badge>}
              </div>
              <p className="mt-2 text-sm text-gray-500">
                {t('households.memberCount', { count: household.memberCount })}
              </p>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
