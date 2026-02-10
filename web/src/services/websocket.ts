/**
 * WebSocket service for real-time shopping list updates.
 *
 * Features:
 * - Connection management with JWT authentication
 * - Automatic reconnection with exponential backoff
 * - Ping/pong keepalive
 * - Idle timeout (10 min) with automatic reconnect on activity
 * - Event handlers for server messages
 */

// Server -> Client event types
export type WebSocketEventType =
  | 'item:added'
  | 'item:updated'
  | 'item:checked'
  | 'item:removed'
  | 'list:created'
  | 'list:updated'
  | 'list:deleted'
  | 'comment:added'
  | 'comment:updated'
  | 'comment:deleted'
  | 'subscribed'
  | 'unsubscribed'
  | 'access:revoked'
  | 'pong'
  | 'error'
  | 'sync:incremental'
  | 'sync:full';

// Client -> Server command types
export type WebSocketCommandType = 'subscribe' | 'unsubscribe' | 'ping';

// Data types matching backend
export interface ActorInfo {
  id: string;
  displayName: string;
}

export interface ItemData {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  isChecked: boolean;
  checkedByName: string | null;
}

export interface ListData {
  id: string;
  name: string;
  householdId: string | null;
  isPersonal: boolean;
}

// Server -> Client events
export interface ItemAddedEvent {
  type: 'item:added';
  listId: string;
  item: ItemData;
  actor: ActorInfo;
  timestamp: string;
}

export interface ItemUpdatedEvent {
  type: 'item:updated';
  listId: string;
  item: ItemData;
  changes: string[];
  actor: ActorInfo;
  timestamp: string;
}

export interface ItemCheckedEvent {
  type: 'item:checked';
  listId: string;
  itemId: string;
  isChecked: boolean;
  actor: ActorInfo;
  timestamp: string;
}

export interface ItemRemovedEvent {
  type: 'item:removed';
  listId: string;
  itemId: string;
  actor: ActorInfo;
  timestamp: string;
}

export interface ListCreatedEvent {
  type: 'list:created';
  list: ListData;
  actor: ActorInfo;
  timestamp: string;
}

export interface ListUpdatedEvent {
  type: 'list:updated';
  list: ListData;
  changes: string[];
  actor: ActorInfo;
  timestamp: string;
}

export interface ListDeletedEvent {
  type: 'list:deleted';
  listId: string;
  actor: ActorInfo;
  timestamp: string;
}

export interface CommentAddedEvent {
  type: 'comment:added';
  targetType: string;
  targetId: string;
  comment: {
    id: string;
    text: string;
    authorId: string;
    authorName: string;
    authorAvatarUrl: string | null;
    editedAt: string | null;
    createdAt: string;
  };
  actor: ActorInfo;
  timestamp: string;
}

export interface CommentUpdatedEvent {
  type: 'comment:updated';
  targetType: string;
  targetId: string;
  commentId: string;
  text: string;
  editedAt: string;
  actor: ActorInfo;
  timestamp: string;
}

export interface CommentDeletedEvent {
  type: 'comment:deleted';
  targetType: string;
  targetId: string;
  commentId: string;
  actor: ActorInfo;
  timestamp: string;
}

export interface SubscribedEvent {
  type: 'subscribed';
  listIds: string[];
}

export interface UnsubscribedEvent {
  type: 'unsubscribed';
  listIds: string[];
}

export interface AccessRevokedEvent {
  type: 'access:revoked';
  listId: string;
}

export interface PongEvent {
  type: 'pong';
}

export interface ErrorEvent {
  type: 'error';
  message: string;
  code: string;
}

export type WebSocketEvent =
  | ItemAddedEvent
  | ItemUpdatedEvent
  | ItemCheckedEvent
  | ItemRemovedEvent
  | ListCreatedEvent
  | ListUpdatedEvent
  | ListDeletedEvent
  | CommentAddedEvent
  | CommentUpdatedEvent
  | CommentDeletedEvent
  | SubscribedEvent
  | UnsubscribedEvent
  | AccessRevokedEvent
  | PongEvent
  | ErrorEvent;

// Client -> Server commands
export interface SubscribeCommand {
  type: 'subscribe';
  listIds: string[];
}

export interface UnsubscribeCommand {
  type: 'unsubscribe';
  listIds: string[];
}

export interface PingCommand {
  type: 'ping';
}

export type WebSocketCommand = SubscribeCommand | UnsubscribeCommand | PingCommand;

// Connection state
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

// Event handler type
export type EventHandler<T extends WebSocketEvent = WebSocketEvent> = (event: T) => void;

// Configuration
export interface WebSocketConfig {
  /** Base WebSocket URL (default: ws://localhost:8080/ws) */
  url?: string;
  /** Ping interval in ms (default: 30000) */
  pingInterval?: number;
  /** Idle timeout in ms (default: 600000 = 10 minutes) */
  idleTimeout?: number;
  /** Initial reconnect delay in ms (default: 1000) */
  initialReconnectDelay?: number;
  /** Max reconnect delay in ms (default: 30000) */
  maxReconnectDelay?: number;
  /** Max reconnect attempts before giving up (default: 10) */
  maxReconnectAttempts?: number;
}

function getDefaultWsUrl(): string {
  // VITE_API_URL is '' in production (same-origin), undefined in dev, or an explicit URL
  const apiUrl = import.meta.env.VITE_API_URL;
  if (apiUrl === undefined) {
    // Dev mode: match the hardcoded API base in api.ts
    return 'ws://localhost:8080/ws';
  }
  if (apiUrl === '') {
    // Production: same-origin
    if (typeof window !== 'undefined' && window.location?.host) {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${protocol}//${window.location.host}/ws`;
    }
    return 'ws://localhost:8080/ws';
  }
  // Explicit API URL: derive WS URL from it
  const url = new URL(apiUrl);
  const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${url.host}/ws`;
}

const DEFAULT_CONFIG: Required<WebSocketConfig> = {
  url: import.meta.env.VITE_WS_URL || getDefaultWsUrl(),
  pingInterval: 30000,
  idleTimeout: 600000, // 10 minutes
  initialReconnectDelay: 1000,
  maxReconnectDelay: 30000,
  maxReconnectAttempts: 10,
};

/**
 * WebSocket service class for managing real-time connections.
 */
export class WebSocketService {
  private config: Required<WebSocketConfig>;
  private socket: WebSocket | null = null;
  private connectionState: ConnectionState = 'disconnected';
  private token: string | null = null;

  // Reconnection state
  private reconnectAttempts = 0;
  private reconnectTimeoutId: ReturnType<typeof setTimeout> | null = null;

  // Keepalive state
  private pingIntervalId: ReturnType<typeof setInterval> | null = null;

  // Idle timeout state
  private idleTimeoutId: ReturnType<typeof setTimeout> | null = null;
  private isIdleDisconnected = false;
  private activityListenersAttached = false;

  // Event tracking for future delta sync
  private lastEventTimestamp: string | null = null;

  // Event handlers
  private eventHandlers: Map<WebSocketEventType | '*', Set<EventHandler>> = new Map();
  private connectionStateHandlers: Set<(state: ConnectionState) => void> = new Set();

  constructor(config: WebSocketConfig = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Get the current connection state.
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Get the last event timestamp (for future delta sync support).
   */
  getLastEventTimestamp(): string | null {
    return this.lastEventTimestamp;
  }

  /**
   * Check if disconnected due to idle timeout.
   */
  isDisconnectedDueToIdle(): boolean {
    return this.isIdleDisconnected;
  }

  /**
   * Connect to the WebSocket server with JWT authentication.
   */
  connect(token: string): void {
    if (this.connectionState === 'connected' || this.connectionState === 'connecting') {
      return;
    }

    this.token = token;
    this.isIdleDisconnected = false;
    this.reconnectAttempts = 0;
    this.doConnect();
  }

  /**
   * Disconnect from the WebSocket server.
   */
  disconnect(): void {
    this.cleanup();
    this.setConnectionState('disconnected');
  }

  /**
   * Subscribe to additional lists.
   */
  subscribe(listIds: string[]): void {
    if (this.connectionState !== 'connected' || !this.socket) {
      return;
    }

    const command: SubscribeCommand = {
      type: 'subscribe',
      listIds,
    };
    this.socket.send(JSON.stringify(command));
  }

  /**
   * Unsubscribe from lists.
   */
  unsubscribe(listIds: string[]): void {
    if (this.connectionState !== 'connected' || !this.socket) {
      return;
    }

    const command: UnsubscribeCommand = {
      type: 'unsubscribe',
      listIds,
    };
    this.socket.send(JSON.stringify(command));
  }

  /**
   * Register an event handler for a specific event type or all events ('*').
   * Returns an unsubscribe function.
   */
  on<T extends WebSocketEvent>(eventType: T['type'] | '*', handler: EventHandler<T>): () => void {
    if (!this.eventHandlers.has(eventType)) {
      this.eventHandlers.set(eventType, new Set());
    }
    this.eventHandlers.get(eventType)!.add(handler as EventHandler);

    return () => {
      this.eventHandlers.get(eventType)?.delete(handler as EventHandler);
    };
  }

  /**
   * Register a connection state change handler.
   * Returns an unsubscribe function.
   */
  onConnectionStateChange(handler: (state: ConnectionState) => void): () => void {
    this.connectionStateHandlers.add(handler);
    return () => {
      this.connectionStateHandlers.delete(handler);
    };
  }

  /**
   * Internal: Perform the actual connection.
   */
  private doConnect(): void {
    if (!this.token) {
      return;
    }

    this.setConnectionState(this.reconnectAttempts > 0 ? 'reconnecting' : 'connecting');

    const url = `${this.config.url}?token=${encodeURIComponent(this.token)}`;

    try {
      this.socket = new WebSocket(url);
      this.setupSocketHandlers();
    } catch {
      this.handleConnectionFailure();
    }
  }

  /**
   * Internal: Set up WebSocket event handlers.
   */
  private setupSocketHandlers(): void {
    if (!this.socket) return;

    this.socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.setConnectionState('connected');
      this.startPingInterval();
      this.startIdleTimeout();
      this.attachActivityListeners();
    };

    this.socket.onclose = (event) => {
      this.stopPingInterval();

      // Don't reconnect if this was a clean disconnect or idle disconnect
      if (this.connectionState === 'disconnected' || this.isIdleDisconnected) {
        return;
      }

      // Check if this was an authentication failure
      if (event.code === 1008) {
        // Policy violation - likely invalid/expired token
        this.setConnectionState('disconnected');
        this.emitEvent({
          type: 'error',
          message: 'Authentication failed',
          code: 'AUTH_FAILED',
        });
        return;
      }

      this.scheduleReconnect();
    };

    this.socket.onerror = () => {
      // Error will be followed by close event, so we handle reconnection there
    };

    this.socket.onmessage = (event) => {
      this.handleMessage(event.data);
    };
  }

  /**
   * Internal: Handle incoming WebSocket message.
   */
  private handleMessage(data: string): void {
    try {
      const event = JSON.parse(data) as WebSocketEvent;

      // Track timestamp for future delta sync
      if ('timestamp' in event && event.timestamp) {
        this.lastEventTimestamp = event.timestamp;
      }

      this.emitEvent(event);
    } catch {
      // Ignore malformed messages
    }
  }

  /**
   * Internal: Emit event to handlers.
   */
  private emitEvent(event: WebSocketEvent): void {
    // Call specific handlers
    const handlers = this.eventHandlers.get(event.type);
    if (handlers) {
      handlers.forEach((handler) => handler(event));
    }

    // Call wildcard handlers
    const wildcardHandlers = this.eventHandlers.get('*');
    if (wildcardHandlers) {
      wildcardHandlers.forEach((handler) => handler(event));
    }
  }

  /**
   * Internal: Set connection state and notify handlers.
   */
  private setConnectionState(state: ConnectionState): void {
    if (this.connectionState === state) return;

    this.connectionState = state;
    this.connectionStateHandlers.forEach((handler) => handler(state));
  }

  /**
   * Internal: Start ping interval for keepalive.
   */
  private startPingInterval(): void {
    this.stopPingInterval();

    this.pingIntervalId = setInterval(() => {
      if (this.socket?.readyState === WebSocket.OPEN) {
        const command: PingCommand = { type: 'ping' };
        this.socket.send(JSON.stringify(command));
      }
    }, this.config.pingInterval);
  }

  /**
   * Internal: Stop ping interval.
   */
  private stopPingInterval(): void {
    if (this.pingIntervalId) {
      clearInterval(this.pingIntervalId);
      this.pingIntervalId = null;
    }
  }

  /**
   * Internal: Start idle timeout.
   */
  private startIdleTimeout(): void {
    this.resetIdleTimeout();
  }

  /**
   * Internal: Reset idle timeout (called on user activity).
   */
  private resetIdleTimeout(): void {
    if (this.idleTimeoutId) {
      clearTimeout(this.idleTimeoutId);
    }

    // Only set idle timeout if connected
    if (this.connectionState === 'connected') {
      this.idleTimeoutId = setTimeout(() => {
        this.handleIdleTimeout();
      }, this.config.idleTimeout);
    }
  }

  /**
   * Internal: Handle idle timeout - disconnect to save resources.
   */
  private handleIdleTimeout(): void {
    this.isIdleDisconnected = true;
    this.cleanup(false); // Don't detach activity listeners
    this.setConnectionState('disconnected');
  }

  /**
   * Internal: Handle user activity - reconnect if idle disconnected.
   */
  private handleUserActivity = (): void => {
    // Reset idle timeout if connected
    if (this.connectionState === 'connected') {
      this.resetIdleTimeout();
      return;
    }

    // Reconnect if we were idle disconnected
    if (this.isIdleDisconnected && this.token) {
      this.isIdleDisconnected = false;
      this.reconnectAttempts = 0;
      this.doConnect();
    }
  };

  /**
   * Internal: Attach activity listeners for idle detection.
   */
  private attachActivityListeners(): void {
    if (this.activityListenersAttached) return;

    const events = ['click', 'scroll', 'keypress', 'mousemove', 'touchstart'];
    events.forEach((event) => {
      window.addEventListener(event, this.handleUserActivity, { passive: true });
    });
    this.activityListenersAttached = true;
  }

  /**
   * Internal: Detach activity listeners.
   */
  private detachActivityListeners(): void {
    if (!this.activityListenersAttached) return;

    const events = ['click', 'scroll', 'keypress', 'mousemove', 'touchstart'];
    events.forEach((event) => {
      window.removeEventListener(event, this.handleUserActivity);
    });
    this.activityListenersAttached = false;
  }

  /**
   * Internal: Schedule a reconnection attempt with exponential backoff.
   */
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
      this.setConnectionState('disconnected');
      this.emitEvent({
        type: 'error',
        message: 'Max reconnection attempts reached',
        code: 'MAX_RECONNECT_ATTEMPTS',
      });
      return;
    }

    this.setConnectionState('reconnecting');

    // Exponential backoff with jitter
    const delay = Math.min(
      this.config.initialReconnectDelay * Math.pow(2, this.reconnectAttempts),
      this.config.maxReconnectDelay
    );
    const jitter = delay * 0.2 * Math.random();
    const totalDelay = delay + jitter;

    this.reconnectTimeoutId = setTimeout(() => {
      this.reconnectAttempts++;
      this.doConnect();
    }, totalDelay);
  }

  /**
   * Internal: Handle connection failure.
   */
  private handleConnectionFailure(): void {
    this.scheduleReconnect();
  }

  /**
   * Internal: Clean up resources.
   */
  private cleanup(detachActivityListeners = true): void {
    // Clear reconnect timeout
    if (this.reconnectTimeoutId) {
      clearTimeout(this.reconnectTimeoutId);
      this.reconnectTimeoutId = null;
    }

    // Clear idle timeout
    if (this.idleTimeoutId) {
      clearTimeout(this.idleTimeoutId);
      this.idleTimeoutId = null;
    }

    // Stop ping interval
    this.stopPingInterval();

    // Close socket
    if (this.socket) {
      this.socket.onopen = null;
      this.socket.onclose = null;
      this.socket.onerror = null;
      this.socket.onmessage = null;

      if (this.socket.readyState === WebSocket.OPEN) {
        this.socket.close();
      }
      this.socket = null;
    }

    // Optionally detach activity listeners
    if (detachActivityListeners) {
      this.detachActivityListeners();
    }
  }
}

// Singleton instance
let instance: WebSocketService | null = null;

/**
 * Get the singleton WebSocket service instance.
 */
export function getWebSocketService(config?: WebSocketConfig): WebSocketService {
  if (!instance) {
    instance = new WebSocketService(config);
  }
  return instance;
}

/**
 * Reset the singleton instance (useful for testing).
 */
export function resetWebSocketService(): void {
  if (instance) {
    instance.disconnect();
    instance = null;
  }
}
