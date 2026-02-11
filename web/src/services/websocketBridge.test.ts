import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../store/authSlice';
import householdsReducer from '../store/householdsSlice';
import listsReducer from '../store/listsSlice';
import websocketReducer from '../store/websocketSlice';
import commentsReducer from '../store/commentsSlice';
import recurringItemsReducer from '../store/recurringItemsSlice';
import {
  initWebSocketBridge,
  cleanupWebSocketBridge,
  setCurrentUserId,
  getCurrentUserId,
  setToastCallback,
  connectWebSocket,
  disconnectWebSocket,
  subscribeToLists,
  unsubscribeFromLists,
} from './websocketBridge';
import { getWebSocketService, resetWebSocketService } from './websocket';
import type { RootState } from '../store/store';

// Create a mock store for testing
function createTestStore(preloadedState?: Partial<RootState>) {
  return configureStore({
    reducer: {
      auth: authReducer,
      households: householdsReducer,
      lists: listsReducer,
      websocket: websocketReducer,
      comments: commentsReducer,
      recurringItems: recurringItemsReducer,
    },
    preloadedState: preloadedState as RootState,
  });
}

// Mock WebSocket
class MockWebSocket {
  static instances: MockWebSocket[] = [];
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  url: string;
  readyState: number = MockWebSocket.CONNECTING;
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;

  sentMessages: string[] = [];

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  send(data: string): void {
    this.sentMessages.push(data);
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED;
  }

  simulateOpen(): void {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.(new Event('open'));
  }

  simulateClose(code = 1000, reason = ''): void {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.(new CloseEvent('close', { code, reason }));
  }

  simulateMessage(data: object): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }));
  }
}

const originalWebSocket = globalThis.WebSocket;
const originalAddEventListener = window.addEventListener;
const originalRemoveEventListener = window.removeEventListener;

describe('websocketBridge', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    MockWebSocket.instances = [];

    globalThis.WebSocket = MockWebSocket as unknown as typeof WebSocket;
    window.addEventListener = vi.fn() as typeof window.addEventListener;
    window.removeEventListener = vi.fn() as typeof window.removeEventListener;

    // Reset singleton
    resetWebSocketService();
    setCurrentUserId(null);
  });

  afterEach(() => {
    cleanupWebSocketBridge();
    resetWebSocketService();
    vi.useRealTimers();
    globalThis.WebSocket = originalWebSocket;
    window.addEventListener = originalAddEventListener;
    window.removeEventListener = originalRemoveEventListener;
  });

  describe('initWebSocketBridge', () => {
    it('should sync connection state to Redux', () => {
      const store = createTestStore();
      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');

      expect(store.getState().websocket.connectionState).toBe('connecting');

      MockWebSocket.instances[0].simulateOpen();
      expect(store.getState().websocket.connectionState).toBe('connected');
    });

    it('should clean up previous subscriptions when called again', () => {
      const store = createTestStore();
      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      // Re-initialize
      initWebSocketBridge(store.dispatch, store.getState);

      // Should still work (no duplicate handlers)
      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-1',
        item: {
          id: 'item-1',
          name: 'Test',
          quantity: 1,
          unit: null,
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'other-user', displayName: 'Other' },
        timestamp: '2024-01-01T00:00:00Z',
      });
    });
  });

  describe('setCurrentUserId / getCurrentUserId', () => {
    it('should set and get current user ID', () => {
      expect(getCurrentUserId()).toBeNull();
      setCurrentUserId('user-123');
      expect(getCurrentUserId()).toBe('user-123');
    });

    it('should not duplicate items from current user already added via REST', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [
            {
              id: 'item-1',
              name: 'Test',
              quantity: 1,
              unit: null,
              isChecked: false,
              checkedByName: null,
              createdAt: '2024-01-01T00:00:00Z',
            },
          ],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);
      setCurrentUserId('user-123');

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      // WebSocket echo of own item:added - should not duplicate
      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-1',
        item: {
          id: 'item-1',
          name: 'Test',
          quantity: 1,
          unit: null,
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'user-123', displayName: 'Current User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(store.getState().lists.currentListItems).toHaveLength(1);
    });
  });

  describe('item events', () => {
    it('should handle item:added event for current list', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-1',
        item: {
          id: 'item-1',
          name: 'Milk',
          quantity: 2,
          unit: 'liters',
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      const items = store.getState().lists.currentListItems;
      expect(items).toHaveLength(1);
      expect(items[0].name).toBe('Milk');
      expect(items[0].quantity).toBe(2);
    });

    it('should not add duplicate items', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [
            {
              id: 'item-1',
              name: 'Existing',
              quantity: 1,
              unit: null,
              isChecked: false,
              checkedByName: null,
              createdAt: '2024-01-01T00:00:00Z',
            },
          ],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-1',
        item: {
          id: 'item-1', // Same ID
          name: 'Milk',
          quantity: 2,
          unit: null,
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(store.getState().lists.currentListItems).toHaveLength(1);
      expect(store.getState().lists.currentListItems[0].name).toBe('Existing');
    });

    it('should ignore item:added event for different list', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-2', // Different list
        item: {
          id: 'item-1',
          name: 'Milk',
          quantity: 2,
          unit: 'liters',
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(store.getState().lists.currentListItems).toHaveLength(0);
    });

    it('should handle item:updated event', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [
            {
              id: 'item-1',
              name: 'Milk',
              quantity: 1,
              unit: null,
              isChecked: false,
              checkedByName: null,
              createdAt: '2024-01-01T00:00:00Z',
            },
          ],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:updated',
        listId: 'list-1',
        item: {
          id: 'item-1',
          name: 'Whole Milk',
          quantity: 3,
          unit: 'gallons',
          isChecked: false,
          checkedByName: null,
        },
        changes: ['name', 'quantity', 'unit'],
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      const item = store.getState().lists.currentListItems[0];
      expect(item.name).toBe('Whole Milk');
      expect(item.quantity).toBe(3);
      expect(item.unit).toBe('gallons');
    });

    it('should handle item:checked event', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [
            {
              id: 'item-1',
              name: 'Milk',
              quantity: 1,
              unit: null,
              isChecked: false,
              checkedByName: null,
              createdAt: '2024-01-01T00:00:00Z',
            },
          ],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:checked',
        listId: 'list-1',
        itemId: 'item-1',
        isChecked: true,
        actor: { id: 'other-user', displayName: 'John' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      const item = store.getState().lists.currentListItems[0];
      expect(item.isChecked).toBe(true);
      expect(item.checkedByName).toBe('John');
    });

    it('should handle item:removed event', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [
            {
              id: 'item-1',
              name: 'Milk',
              quantity: 1,
              unit: null,
              isChecked: false,
              checkedByName: null,
              createdAt: '2024-01-01T00:00:00Z',
            },
          ],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:removed',
        listId: 'list-1',
        itemId: 'item-1',
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(store.getState().lists.currentListItems).toHaveLength(0);
    });
  });

  describe('list events', () => {
    it('should handle list:created event', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: null,
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'list:created',
        list: {
          id: 'list-1',
          name: 'Groceries',
          householdId: 'household-1',
          isPersonal: false,
        },
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      const lists = store.getState().lists.items;
      expect(lists).toHaveLength(1);
      expect(lists[0].name).toBe('Groceries');
      expect(lists[0].isOwner).toBe(false);
    });

    it('should show toast for system-created list', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: null,
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      const toastFn = vi.fn();
      setToastCallback(toastFn);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'list:created',
        list: {
          id: 'list-auto',
          name: 'Recurring - Feb 11',
          householdId: 'household-1',
          isPersonal: false,
        },
        actor: { id: 'system', displayName: 'System' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(toastFn).toHaveBeenCalledTimes(1);
      expect(toastFn).toHaveBeenCalledWith(expect.stringContaining('Recurring - Feb 11'));

      setToastCallback(null);
    });

    it('should not show toast for user-created list', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: null,
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      const toastFn = vi.fn();
      setToastCallback(toastFn);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'list:created',
        list: {
          id: 'list-1',
          name: 'My List',
          householdId: 'household-1',
          isPersonal: false,
        },
        actor: { id: 'user-1', displayName: 'A User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(toastFn).not.toHaveBeenCalled();

      setToastCallback(null);
    });

    it('should handle list:updated event', () => {
      const store = createTestStore({
        lists: {
          items: [
            {
              id: 'list-1',
              name: 'Groceries',
              householdId: 'household-1',
              isPersonal: false,
              createdAt: '2024-01-01T00:00:00Z',
              isOwner: true,
            },
          ],
          currentListId: null,
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'list:updated',
        list: {
          id: 'list-1',
          name: 'Weekly Groceries',
          householdId: 'household-1',
          isPersonal: false,
        },
        changes: ['name'],
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      const list = store.getState().lists.items[0];
      expect(list.name).toBe('Weekly Groceries');
    });

    it('should handle list:deleted event', () => {
      const store = createTestStore({
        lists: {
          items: [
            {
              id: 'list-1',
              name: 'Groceries',
              householdId: 'household-1',
              isPersonal: false,
              createdAt: '2024-01-01T00:00:00Z',
              isOwner: true,
            },
          ],
          currentListId: null,
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'list:deleted',
        listId: 'list-1',
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(store.getState().lists.items).toHaveLength(0);
    });
  });

  describe('timestamp tracking', () => {
    it('should update lastEventTimestamp on events', () => {
      const store = createTestStore();
      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-1',
        item: {
          id: 'item-1',
          name: 'Milk',
          quantity: 1,
          unit: null,
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-15T12:30:00Z',
      });

      expect(store.getState().websocket.lastEventTimestamp).toBe('2024-01-15T12:30:00Z');
    });
  });

  describe('helper functions', () => {
    it('should connect WebSocket', () => {
      connectWebSocket('test-token');
      expect(MockWebSocket.instances).toHaveLength(1);
    });

    it('should disconnect WebSocket', () => {
      connectWebSocket('test-token');
      MockWebSocket.instances[0].simulateOpen();
      disconnectWebSocket();
      expect(MockWebSocket.instances[0].readyState).toBe(MockWebSocket.CLOSED);
    });

    it('should subscribe to lists', () => {
      connectWebSocket('test-token');
      MockWebSocket.instances[0].simulateOpen();

      subscribeToLists(['list-1', 'list-2']);

      const sentMessages = MockWebSocket.instances[0].sentMessages;
      expect(sentMessages).toContainEqual(
        JSON.stringify({ type: 'subscribe', listIds: ['list-1', 'list-2'] })
      );
    });

    it('should unsubscribe from lists', () => {
      connectWebSocket('test-token');
      MockWebSocket.instances[0].simulateOpen();

      unsubscribeFromLists(['list-1']);

      const sentMessages = MockWebSocket.instances[0].sentMessages;
      expect(sentMessages).toContainEqual(
        JSON.stringify({ type: 'unsubscribe', listIds: ['list-1'] })
      );
    });
  });

  describe('cleanupWebSocketBridge', () => {
    it('should clean up event listeners', () => {
      const store = createTestStore({
        lists: {
          items: [],
          currentListId: 'list-1',
          currentListItems: [],
          isLoading: false,
          error: null,
        },
      } as Partial<RootState>);

      initWebSocketBridge(store.dispatch, store.getState);

      const wsService = getWebSocketService();
      wsService.connect('test-token');
      MockWebSocket.instances[0].simulateOpen();

      cleanupWebSocketBridge();

      // After cleanup, events should not update the store
      MockWebSocket.instances[0].simulateMessage({
        type: 'item:added',
        listId: 'list-1',
        item: {
          id: 'item-1',
          name: 'Milk',
          quantity: 1,
          unit: null,
          isChecked: false,
          checkedByName: null,
        },
        actor: { id: 'other-user', displayName: 'Other User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(store.getState().lists.currentListItems).toHaveLength(0);
    });
  });
});
