import { useState, useEffect, useCallback } from 'react';

interface WidgetProps {
  token: string;
  apiUrl: string;
  theme: 'light' | 'dark';
}

interface ListItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  isChecked: boolean;
}

interface ListData {
  id: string;
  name: string;
  permission: string;
}

type ViewState = 'loading' | 'error' | 'expired' | 'not_found' | 'success';

export function ShoppingListWidgetApp({ token, apiUrl, theme }: WidgetProps) {
  const [viewState, setViewState] = useState<ViewState>('loading');
  const [listData, setListData] = useState<ListData | null>(null);
  const [items, setItems] = useState<ListItem[]>([]);
  const [newItemName, setNewItemName] = useState('');

  const fetchList = useCallback(async () => {
    try {
      const response = await fetch(`${apiUrl}/api/shared/${token}`);
      if (!response.ok) {
        if (response.status === 410) setViewState('expired');
        else if (response.status === 404) setViewState('not_found');
        else setViewState('error');
        return;
      }
      const data = await response.json();
      setListData(data);
      setViewState('success');
    } catch {
      setViewState('error');
    }
  }, [apiUrl, token]);

  const fetchItems = useCallback(async () => {
    try {
      const response = await fetch(`${apiUrl}/api/shared/${token}/items`);
      if (response.ok) {
        setItems(await response.json());
      }
    } catch {
      // silently fail for items refresh
    }
  }, [apiUrl, token]);

  useEffect(() => {
    fetchList();
    fetchItems();
  }, [fetchList, fetchItems]);

  // WebSocket for real-time updates
  useEffect(() => {
    const protocol = apiUrl.startsWith('https') ? 'wss' : 'ws';
    const wsUrl = `${protocol}://${new URL(apiUrl).host}/ws/shared?token=${token}`;

    let ws: WebSocket | null = null;
    try {
      ws = new WebSocket(wsUrl);
      ws.onmessage = () => {
        fetchItems();
      };
    } catch {
      // WebSocket optional — still works without it
    }

    return () => ws?.close();
  }, [apiUrl, token, fetchItems]);

  const addItem = async () => {
    if (!newItemName.trim()) return;
    try {
      await fetch(`${apiUrl}/api/shared/${token}/items`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: newItemName.trim(), quantity: 1 }),
      });
      setNewItemName('');
      fetchItems();
    } catch {
      // show inline error
    }
  };

  const toggleItem = async (itemId: string, checked: boolean) => {
    try {
      await fetch(`${apiUrl}/api/shared/${token}/items/${itemId}/check`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ checked: !checked }),
      });
      fetchItems();
    } catch {
      // silently fail
    }
  };

  const isDark = theme === 'dark';
  const bg = isDark ? '#1f2937' : '#ffffff';
  const text = isDark ? '#f3f4f6' : '#111827';
  const border = isDark ? '#374151' : '#e5e7eb';

  if (viewState === 'loading') {
    return <div style={{ padding: '16px', color: text, backgroundColor: bg }}>Loading...</div>;
  }
  if (viewState === 'expired') {
    return (
      <div style={{ padding: '16px', color: '#dc2626', backgroundColor: bg }}>
        This shopping list link has expired.
      </div>
    );
  }
  if (viewState === 'not_found') {
    return (
      <div style={{ padding: '16px', color: '#dc2626', backgroundColor: bg }}>
        Shopping list not found.
      </div>
    );
  }
  if (viewState === 'error') {
    return (
      <div style={{ padding: '16px', color: '#dc2626', backgroundColor: bg }}>
        Unable to load shopping list.
      </div>
    );
  }

  return (
    <div
      style={{
        fontFamily: 'system-ui, sans-serif',
        backgroundColor: bg,
        color: text,
        border: `1px solid ${border}`,
        borderRadius: '8px',
        padding: '16px',
        maxWidth: '400px',
      }}
    >
      <h3 style={{ margin: '0 0 12px 0', fontSize: '18px', fontWeight: 600 }}>
        {listData?.name}
      </h3>

      <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 12px 0' }}>
        {items.map((item) => (
          <li
            key={item.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 0',
              borderBottom: `1px solid ${border}`,
              cursor: 'pointer',
            }}
            onClick={() => toggleItem(item.id, item.isChecked)}
          >
            <input type="checkbox" checked={item.isChecked} readOnly style={{ cursor: 'pointer' }} />
            <span
              style={{
                textDecoration: item.isChecked ? 'line-through' : 'none',
                opacity: item.isChecked ? 0.5 : 1,
                flex: 1,
              }}
            >
              {item.name}
              {item.quantity > 1 && ` (${item.quantity}${item.unit ? ` ${item.unit}` : ''})`}
            </span>
          </li>
        ))}
      </ul>

      <div style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text"
          value={newItemName}
          onChange={(e) => setNewItemName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && addItem()}
          placeholder="Add item..."
          style={{
            flex: 1,
            padding: '8px',
            border: `1px solid ${border}`,
            borderRadius: '4px',
            backgroundColor: bg,
            color: text,
          }}
        />
        <button
          onClick={addItem}
          style={{
            padding: '8px 16px',
            backgroundColor: '#3b82f6',
            color: '#ffffff',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          Add
        </button>
      </div>
    </div>
  );
}
