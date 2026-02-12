import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { LoadingSpinner, ErrorAlert, Badge } from '../common';
import { API_BASE } from '../../services/api';
import ShoppingListView from '../shopping-list/ShoppingListView';
import type { SharePermission } from '../shopping-list/ShoppingListView';
import { type ListItem } from '../../store/listsSlice';

interface SharedListViewProps {
  token: string;
}

interface SharedListData {
  id: string;
  name: string;
  permission: SharePermission;
}

type ViewState = 'loading' | 'expired' | 'not_found' | 'success' | 'error';

export default function SharedListView({ token }: SharedListViewProps) {
  const { t } = useTranslation();
  const [viewState, setViewState] = useState<ViewState>('loading');
  const [listData, setListData] = useState<SharedListData | null>(null);
  const [items, setItems] = useState<ListItem[]>([]);

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
        const data = await response.json();
        setListData({ id: data.id, name: data.name, permission: data.permission });
        setItems(data.items);
        setViewState('success');
      } catch {
        setViewState('error');
      }
    };
    fetchList();
  }, [token]);

  const handleItemsChange = useCallback((newItems: ListItem[]) => {
    setItems(newItems);
  }, []);

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
  if (viewState === 'error' || !listData) {
    return <ErrorAlert title={t('shared.errorTitle')} message={t('shared.errorMessage')} />;
  }

  const getPermissionLabel = (permission: string) => {
    switch (permission) {
      case 'READ':
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
        <h2 className="text-2xl font-bold text-gray-900">{listData.name}</h2>
        <Badge>{getPermissionLabel(listData.permission)}</Badge>
      </div>
      <ShoppingListView
        listId={listData.id}
        shareToken={token}
        permission={listData.permission}
        sharedListName={listData.name}
        sharedItems={items}
        onSharedItemsChange={handleItemsChange}
        onBack={() => {}}
      />
    </div>
  );
}
