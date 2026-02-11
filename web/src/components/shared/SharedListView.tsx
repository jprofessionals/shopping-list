import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { LoadingSpinner, ErrorAlert, EmptyState, Badge } from '../common';
import { API_BASE } from '../../services/api';

interface SharedListViewProps {
  token: string;
}

interface SharedItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  isChecked: boolean;
}

interface SharedList {
  id: string;
  name: string;
  permission: string;
  items: SharedItem[];
}

type ViewState = 'loading' | 'expired' | 'not_found' | 'success' | 'error';

export default function SharedListView({ token }: SharedListViewProps) {
  const { t } = useTranslation();
  const [viewState, setViewState] = useState<ViewState>('loading');
  const [list, setList] = useState<SharedList | null>(null);

  useEffect(() => {
    const fetchList = async () => {
      try {
        const response = await fetch(`${API_BASE}/shared/${token}`);

        if (!response.ok) {
          if (response.status === 410) {
            setViewState('expired');
            return;
          }
          if (response.status === 404) {
            setViewState('not_found');
            return;
          }
          setViewState('error');
          return;
        }

        const data: SharedList = await response.json();
        setList(data);
        setViewState('success');
      } catch {
        setViewState('error');
      }
    };

    fetchList();
  }, [token]);

  const handleToggleCheck = async (item: SharedItem) => {
    if (!list || (list.permission !== 'CHECK' && list.permission !== 'WRITE')) {
      return;
    }

    try {
      const url = `${API_BASE}/shared/${token}/items/${item.id}/check`;
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ isChecked: !item.isChecked }),
      });

      if (response.ok) {
        const data = await response.json();
        setList((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            items: prev.items.map((i) =>
              i.id === item.id ? { ...i, isChecked: data.isChecked } : i
            ),
          };
        });
      }
    } catch {
      console.error('Failed to toggle item check');
    }
  };

  const canCheck = list?.permission === 'CHECK' || list?.permission === 'WRITE';

  if (viewState === 'loading') {
    return (
      <div className="flex items-center justify-center p-8">
        <LoadingSpinner />
      </div>
    );
  }

  if (viewState === 'expired') {
    return (
      <ErrorAlert
        variant="warning"
        title={t('shared.linkExpiredTitle')}
        message={t('shared.linkExpiredMessage')}
      />
    );
  }

  if (viewState === 'not_found') {
    return <ErrorAlert title={t('shared.notFoundTitle')} message={t('shared.notFoundMessage')} />;
  }

  if (viewState === 'error' || !list) {
    return <ErrorAlert title={t('shared.errorTitle')} message={t('shared.errorMessage')} />;
  }

  const uncheckedItems = list.items.filter((item) => !item.isChecked);
  const checkedItems = list.items.filter((item) => item.isChecked);

  const getPermissionLabel = (permission: string) => {
    switch (permission) {
      case 'VIEW':
        return t('shared.permissionView');
      case 'CHECK':
        return t('shared.permissionCheck');
      case 'WRITE':
        return t('shared.permissionWrite');
      default:
        return permission;
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{list.name}</h2>
        <Badge>{getPermissionLabel(list.permission)}</Badge>
      </div>

      {/* Unchecked Items */}
      {uncheckedItems.length > 0 && (
        <div className="mb-6 rounded-lg bg-white p-4 shadow">
          <ul className="divide-y divide-gray-200">
            {uncheckedItems.map((item) => (
              <li key={item.id} className="flex items-center justify-between py-3">
                <div className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={item.isChecked}
                    onChange={() => handleToggleCheck(item)}
                    disabled={!canCheck}
                    className="h-5 w-5 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
                  />
                  <span className="text-gray-900">{item.name}</span>
                  <span className="text-sm text-gray-500">
                    {item.quantity} {item.unit}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Checked Items Section */}
      {checkedItems.length > 0 && (
        <div className="rounded-lg bg-gray-50 p-4">
          <h3 className="mb-3 text-sm font-medium text-gray-700">{t('shared.checkedItems')}</h3>
          <ul className="divide-y divide-gray-200">
            {checkedItems.map((item) => (
              <li key={item.id} className="flex items-center justify-between py-3">
                <div className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    checked={item.isChecked}
                    onChange={() => handleToggleCheck(item)}
                    disabled={!canCheck}
                    className="h-5 w-5 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
                  />
                  <span className="line-through text-gray-500">{item.name}</span>
                  <span className="text-sm text-gray-500">
                    {item.quantity} {item.unit}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {list.items.length === 0 && <EmptyState title={t('shared.noItems')} />}
    </div>
  );
}
