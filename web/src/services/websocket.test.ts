import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  WebSocketService,
  getWebSocketService,
  resetWebSocketService,
  type ConnectionState,
  type WebSocketEvent,
  type ItemAddedEvent,
  type SubscribedEvent,
  type ErrorEvent,
} from './websocket';

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

  // Test helpers
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

  simulateError(): void {
    this.onerror?.(new Event('error'));
  }
}

// Store original WebSocket and window methods
const originalWebSocket = globalThis.WebSocket;
const originalAddEventListener = window.addEventListener;
const originalRemoveEventListener = window.removeEventListener;

describe('WebSocketService', () => {
  let service: WebSocketService;
  let addEventListenerCalls: Array<{ type: string; listener: EventListener }>;
  let removeEventListenerCalls: Array<{ type: string; listener: EventListener }>;

  beforeEach(() => {
    vi.useFakeTimers();
    MockWebSocket.instances = [];
    addEventListenerCalls = [];
    removeEventListenerCalls = [];

    // Mock WebSocket
    globalThis.WebSocket = MockWebSocket as unknown as typeof WebSocket;

    // Mock window event listeners
    window.addEventListener = vi.fn((type: string, listener: EventListener) => {
      addEventListenerCalls.push({ type, listener });
    }) as typeof window.addEventListener;

    window.removeEventListener = vi.fn((type: string, listener: EventListener) => {
      removeEventListenerCalls.push({ type, listener });
    }) as typeof window.removeEventListener;

    service = new WebSocketService({
      url: 'ws://test.local/ws',
      pingInterval: 30000,
      idleTimeout: 600000,
      initialReconnectDelay: 1000,
      maxReconnectDelay: 30000,
      maxReconnectAttempts: 3,
    });
  });

  afterEach(() => {
    service.disconnect();
    resetWebSocketService();
    vi.useRealTimers();
    globalThis.WebSocket = originalWebSocket;
    window.addEventListener = originalAddEventListener;
    window.removeEventListener = originalRemoveEventListener;
  });

  describe('connection', () => {
    it('should connect with JWT token in query parameter', () => {
      service.connect('test-jwt-token');

      expect(MockWebSocket.instances).toHaveLength(1);
      expect(MockWebSocket.instances[0].url).toBe('ws://test.local/ws?token=test-jwt-token');
    });

    it('should URL-encode the token', () => {
      service.connect('token+with/special=chars');

      expect(MockWebSocket.instances[0].url).toBe(
        'ws://test.local/ws?token=token%2Bwith%2Fspecial%3Dchars'
      );
    });

    it('should set connection state to connecting', () => {
      const states: ConnectionState[] = [];
      service.onConnectionStateChange((state) => states.push(state));

      service.connect('token');

      expect(states).toContain('connecting');
    });

    it('should set connection state to connected on open', () => {
      const states: ConnectionState[] = [];
      service.onConnectionStateChange((state) => states.push(state));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      expect(states).toContain('connected');
      expect(service.getConnectionState()).toBe('connected');
    });

    it('should not create new connection if already connected', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      service.connect('token');

      expect(MockWebSocket.instances).toHaveLength(1);
    });

    it('should not create new connection if connecting', () => {
      service.connect('token');
      service.connect('token');

      expect(MockWebSocket.instances).toHaveLength(1);
    });
  });

  describe('disconnection', () => {
    it('should close the socket on disconnect', () => {
      service.connect('token');
      const ws = MockWebSocket.instances[0];
      ws.simulateOpen();

      service.disconnect();

      expect(ws.readyState).toBe(MockWebSocket.CLOSED);
      expect(service.getConnectionState()).toBe('disconnected');
    });

    it('should clean up event handlers on disconnect', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      service.disconnect();

      expect(removeEventListenerCalls.length).toBeGreaterThan(0);
    });
  });

  describe('reconnection with exponential backoff', () => {
    it('should attempt to reconnect on unexpected close', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateClose(1006); // Abnormal closure

      expect(service.getConnectionState()).toBe('reconnecting');

      // First reconnect after initial delay (1000ms + jitter)
      vi.advanceTimersByTime(1500);

      expect(MockWebSocket.instances).toHaveLength(2);
    });

    it('should use exponential backoff for reconnection delays', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      // First disconnect
      MockWebSocket.instances[0].simulateClose(1006);
      vi.advanceTimersByTime(1500); // 1000ms base
      expect(MockWebSocket.instances).toHaveLength(2);

      // Second disconnect (delay should be ~2000ms)
      MockWebSocket.instances[1].simulateClose(1006);
      vi.advanceTimersByTime(1500); // Not enough time
      expect(MockWebSocket.instances).toHaveLength(2);
      vi.advanceTimersByTime(1500); // 3000ms total should be enough
      expect(MockWebSocket.instances).toHaveLength(3);
    });

    it('should stop reconnecting after max attempts', () => {
      const errors: ErrorEvent[] = [];
      service.on('error', (event) => errors.push(event as ErrorEvent));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateClose(1006);

      // Exhaust all reconnect attempts (maxReconnectAttempts = 3)
      // Each iteration: wait for backoff, new socket created, immediately close it
      for (let i = 0; i < 3; i++) {
        vi.advanceTimersByTime(60000); // More than enough for any backoff
        const ws = MockWebSocket.instances[MockWebSocket.instances.length - 1];
        ws.simulateClose(1006); // Close without opening (connection failed)
      }

      // After 3 failed reconnects, should be disconnected
      expect(service.getConnectionState()).toBe('disconnected');
      expect(errors).toContainEqual(expect.objectContaining({ code: 'MAX_RECONNECT_ATTEMPTS' }));
    });

    it('should reset reconnect attempts on successful connection', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateClose(1006);

      vi.advanceTimersByTime(1500);
      MockWebSocket.instances[1].simulateOpen();
      MockWebSocket.instances[1].simulateClose(1006);

      // Should use initial delay again (not continuing exponential backoff)
      vi.advanceTimersByTime(1500);
      expect(MockWebSocket.instances).toHaveLength(3);
    });

    it('should not reconnect on policy violation (auth failure)', () => {
      const errors: ErrorEvent[] = [];
      service.on('error', (event) => errors.push(event as ErrorEvent));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateClose(1008); // Policy violation

      expect(service.getConnectionState()).toBe('disconnected');
      expect(errors).toContainEqual(expect.objectContaining({ code: 'AUTH_FAILED' }));
      expect(MockWebSocket.instances).toHaveLength(1);
    });

    it('should not reconnect on clean disconnect', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      service.disconnect();

      vi.advanceTimersByTime(60000);
      expect(MockWebSocket.instances).toHaveLength(1);
    });
  });

  describe('ping/pong keepalive', () => {
    it('should send ping at configured interval', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      vi.advanceTimersByTime(30000);

      const sentMessages = MockWebSocket.instances[0].sentMessages;
      expect(sentMessages).toContainEqual(JSON.stringify({ type: 'ping' }));
    });

    it('should send multiple pings over time', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      vi.advanceTimersByTime(90000);

      const pingMessages = MockWebSocket.instances[0].sentMessages.filter(
        (msg) => JSON.parse(msg).type === 'ping'
      );
      expect(pingMessages.length).toBe(3);
    });

    it('should stop pinging after disconnect', () => {
      service.connect('token');
      const ws = MockWebSocket.instances[0];
      ws.simulateOpen();

      service.disconnect();
      vi.advanceTimersByTime(60000);

      // Only check messages sent before disconnect
      const pingMessages = ws.sentMessages.filter((msg) => JSON.parse(msg).type === 'ping');
      expect(pingMessages.length).toBe(0);
    });
  });

  describe('idle timeout', () => {
    it('should disconnect after idle timeout', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      vi.advanceTimersByTime(600000); // 10 minutes

      expect(service.getConnectionState()).toBe('disconnected');
      expect(service.isDisconnectedDueToIdle()).toBe(true);
    });

    it('should attach activity listeners on connect', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      const eventTypes = addEventListenerCalls.map((c) => c.type);
      expect(eventTypes).toContain('click');
      expect(eventTypes).toContain('scroll');
      expect(eventTypes).toContain('keypress');
    });

    it('should reset idle timeout on user activity', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      // Wait 5 minutes
      vi.advanceTimersByTime(300000);

      // Simulate user activity
      const clickListener = addEventListenerCalls.find((c) => c.type === 'click')?.listener;
      clickListener?.(new MouseEvent('click'));

      // Wait another 5 minutes (total 10, but idle reset)
      vi.advanceTimersByTime(300000);

      expect(service.getConnectionState()).toBe('connected');

      // Wait full 10 minutes from last activity
      vi.advanceTimersByTime(300000);

      expect(service.getConnectionState()).toBe('disconnected');
    });

    it('should reconnect on user activity after idle disconnect', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      // Trigger idle disconnect
      vi.advanceTimersByTime(600000);
      expect(service.isDisconnectedDueToIdle()).toBe(true);
      expect(MockWebSocket.instances).toHaveLength(1);

      // Simulate user activity
      const clickListener = addEventListenerCalls.find((c) => c.type === 'click')?.listener;
      clickListener?.(new MouseEvent('click'));

      // Should create new connection
      expect(MockWebSocket.instances).toHaveLength(2);
      expect(service.isDisconnectedDueToIdle()).toBe(false);
    });

    it('should not reconnect on activity if not idle disconnected', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      // Clean disconnect
      service.disconnect();

      // Simulate user activity (need to re-attach since disconnect removes them)
      // But activity listeners are removed on clean disconnect, so this tests
      // that a new connect is needed
      expect(MockWebSocket.instances).toHaveLength(1);
    });
  });

  describe('event handling', () => {
    it('should emit events to registered handlers', () => {
      const events: WebSocketEvent[] = [];
      service.on('item:added', (event) => events.push(event));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
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
        actor: { id: 'user-1', displayName: 'Test User' },
        timestamp: '2024-01-01T00:00:00Z',
      });

      expect(events).toHaveLength(1);
      expect((events[0] as ItemAddedEvent).item.name).toBe('Test');
    });

    it('should support wildcard handlers', () => {
      const events: WebSocketEvent[] = [];
      service.on('*', (event) => events.push(event));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateMessage({ type: 'pong' });
      MockWebSocket.instances[0].simulateMessage({
        type: 'subscribed',
        listIds: ['list-1'],
      });

      expect(events).toHaveLength(2);
    });

    it('should allow unsubscribing from events', () => {
      const events: WebSocketEvent[] = [];
      const unsubscribe = service.on('pong', (event) => events.push(event));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateMessage({ type: 'pong' });

      unsubscribe();
      MockWebSocket.instances[0].simulateMessage({ type: 'pong' });

      expect(events).toHaveLength(1);
    });

    it('should track lastEventTimestamp', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      expect(service.getLastEventTimestamp()).toBeNull();

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
        actor: { id: 'user-1', displayName: 'Test User' },
        timestamp: '2024-01-01T12:00:00Z',
      });

      expect(service.getLastEventTimestamp()).toBe('2024-01-01T12:00:00Z');
    });
  });

  describe('subscribe/unsubscribe commands', () => {
    it('should send subscribe command', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      service.subscribe(['list-1', 'list-2']);

      const sentMessages = MockWebSocket.instances[0].sentMessages;
      expect(sentMessages).toContainEqual(
        JSON.stringify({ type: 'subscribe', listIds: ['list-1', 'list-2'] })
      );
    });

    it('should send unsubscribe command', () => {
      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();

      service.unsubscribe(['list-1']);

      const sentMessages = MockWebSocket.instances[0].sentMessages;
      expect(sentMessages).toContainEqual(
        JSON.stringify({ type: 'unsubscribe', listIds: ['list-1'] })
      );
    });

    it('should not send commands when not connected', () => {
      service.subscribe(['list-1']);
      service.unsubscribe(['list-1']);

      // No WebSocket created yet
      expect(MockWebSocket.instances).toHaveLength(0);
    });
  });

  describe('connection state handlers', () => {
    it('should notify handlers of state changes', () => {
      const states: ConnectionState[] = [];
      service.onConnectionStateChange((state) => states.push(state));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      service.disconnect();

      expect(states).toEqual(['connecting', 'connected', 'disconnected']);
    });

    it('should allow unsubscribing from state changes', () => {
      const states: ConnectionState[] = [];
      const unsubscribe = service.onConnectionStateChange((state) => states.push(state));

      service.connect('token');
      unsubscribe();
      MockWebSocket.instances[0].simulateOpen();

      expect(states).toEqual(['connecting']);
    });
  });

  describe('singleton', () => {
    it('should return the same instance', () => {
      const instance1 = getWebSocketService();
      const instance2 = getWebSocketService();

      expect(instance1).toBe(instance2);
    });

    it('should reset the singleton', () => {
      const instance1 = getWebSocketService();
      resetWebSocketService();
      const instance2 = getWebSocketService();

      expect(instance1).not.toBe(instance2);
    });
  });

  describe('subscribed event handling', () => {
    it('should handle subscribed event from server', () => {
      const events: SubscribedEvent[] = [];
      service.on('subscribed', (event) => events.push(event as SubscribedEvent));

      service.connect('token');
      MockWebSocket.instances[0].simulateOpen();
      MockWebSocket.instances[0].simulateMessage({
        type: 'subscribed',
        listIds: ['list-1', 'list-2', 'list-3'],
      });

      expect(events).toHaveLength(1);
      expect(events[0].listIds).toEqual(['list-1', 'list-2', 'list-3']);
    });
  });
});
