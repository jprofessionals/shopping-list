/**
 * WebSocket-Redux Bridge
 *
 * This module connects the WebSocket service to the Redux store, handling:
 * - Connection state synchronization to websocketSlice
 * - WebSocket events dispatched to appropriate Redux actions
 * - Automatic connection management based on auth state
 */

import type { AppDispatch, RootState } from '../store/store';
import { setConnectionState, setLastEventTimestamp } from '../store/websocketSlice';
import {
  addList,
  updateList,
  removeList,
  addItem,
  updateItem,
  removeItem,
  toggleItemCheck,
} from '../store/listsSlice';
import { addComment, updateComment, removeComment } from '../store/commentsSlice';
import i18n from '../i18n/i18n';
import {
  getWebSocketService,
  type WebSocketEvent,
  type ItemAddedEvent,
  type ItemUpdatedEvent,
  type ItemCheckedEvent,
  type ItemRemovedEvent,
  type ListCreatedEvent,
  type ListUpdatedEvent,
  type ListDeletedEvent,
  type CommentAddedEvent,
  type CommentUpdatedEvent,
  type CommentDeletedEvent,
  type ConnectionState,
} from './websocket';

// Track current user ID to avoid processing own events
let currentUserId: string | null = null;

// Toast callback for showing notifications from outside React
let toastCallback: ((message: string) => void) | null = null;

// Track cleanup functions
let unsubscribeConnectionState: (() => void) | null = null;
let unsubscribeEvents: (() => void) | null = null;

/**
 * Initialize the WebSocket-Redux bridge.
 * This sets up listeners for WebSocket events and dispatches appropriate Redux actions.
 *
 * @param dispatch - Redux dispatch function
 * @param getState - Redux getState function
 */
export function initWebSocketBridge(dispatch: AppDispatch, getState: () => RootState): void {
  const wsService = getWebSocketService();

  // Clean up any existing subscriptions
  cleanupWebSocketBridge();

  // Listen for connection state changes
  unsubscribeConnectionState = wsService.onConnectionStateChange((state: ConnectionState) => {
    dispatch(setConnectionState(state));
  });

  // Listen for all WebSocket events
  unsubscribeEvents = wsService.on('*', (event: WebSocketEvent) => {
    handleWebSocketEvent(dispatch, getState, event);
  });
}

/**
 * Clean up the WebSocket-Redux bridge.
 */
export function cleanupWebSocketBridge(): void {
  if (unsubscribeConnectionState) {
    unsubscribeConnectionState();
    unsubscribeConnectionState = null;
  }
  if (unsubscribeEvents) {
    unsubscribeEvents();
    unsubscribeEvents = null;
  }
}

/**
 * Set the current user ID (used to filter out own events).
 */
export function setCurrentUserId(userId: string | null): void {
  currentUserId = userId;
}

/**
 * Get the current user ID.
 */
export function getCurrentUserId(): string | null {
  return currentUserId;
}

/**
 * Set a callback for showing toast notifications from the WebSocket bridge.
 */
export function setToastCallback(callback: ((message: string) => void) | null): void {
  toastCallback = callback;
}

/**
 * Handle incoming WebSocket events and dispatch appropriate Redux actions.
 */
function handleWebSocketEvent(
  dispatch: AppDispatch,
  getState: () => RootState,
  event: WebSocketEvent
): void {
  // Update last event timestamp for events that have one
  if ('timestamp' in event && event.timestamp) {
    dispatch(setLastEventTimestamp(event.timestamp));
  }

  switch (event.type) {
    case 'item:added':
      handleItemAdded(dispatch, getState, event);
      break;
    case 'item:updated':
      handleItemUpdated(dispatch, getState, event);
      break;
    case 'item:checked':
      handleItemChecked(dispatch, getState, event);
      break;
    case 'item:removed':
      handleItemRemoved(dispatch, getState, event);
      break;
    case 'list:created':
      handleListCreated(dispatch, event);
      break;
    case 'list:updated':
      handleListUpdated(dispatch, event);
      break;
    case 'list:deleted':
      handleListDeleted(dispatch, event);
      break;
    case 'comment:added':
      handleCommentAdded(dispatch, event);
      break;
    case 'comment:updated':
      handleCommentUpdated(dispatch, event);
      break;
    case 'comment:deleted':
      handleCommentDeleted(dispatch, event);
      break;
    // Other events (pong, subscribed, unsubscribed, error, access:revoked)
    // are handled elsewhere or don't need Redux updates
  }
}

/**
 * Handle item:added event
 */
function handleItemAdded(
  dispatch: AppDispatch,
  getState: () => RootState,
  event: ItemAddedEvent
): void {
  const state = getState();

  // Only update if we're viewing this list
  if (state.lists.currentListId === event.listId) {
    // Check if item already exists (avoid duplicates)
    const existingItem = state.lists.currentListItems.find((i) => i.id === event.item.id);
    if (!existingItem) {
      dispatch(
        addItem({
          id: event.item.id,
          name: event.item.name,
          quantity: event.item.quantity,
          unit: event.item.unit,
          isChecked: event.item.isChecked,
          checkedByName: event.item.checkedByName,
          createdAt: event.timestamp,
        })
      );
    }
  }
}

/**
 * Handle item:updated event
 */
function handleItemUpdated(
  dispatch: AppDispatch,
  getState: () => RootState,
  event: ItemUpdatedEvent
): void {
  const state = getState();

  // Only update if we're viewing this list
  if (state.lists.currentListId === event.listId) {
    dispatch(
      updateItem({
        id: event.item.id,
        name: event.item.name,
        quantity: event.item.quantity,
        unit: event.item.unit,
        isChecked: event.item.isChecked,
        checkedByName: event.item.checkedByName,
      })
    );
  }
}

/**
 * Handle item:checked event
 */
function handleItemChecked(
  dispatch: AppDispatch,
  getState: () => RootState,
  event: ItemCheckedEvent
): void {
  const state = getState();

  // Only update if we're viewing this list
  if (state.lists.currentListId === event.listId) {
    dispatch(
      toggleItemCheck({
        id: event.itemId,
        isChecked: event.isChecked,
        checkedByName: event.isChecked ? event.actor.displayName : null,
      })
    );
  }
}

/**
 * Handle item:removed event
 */
function handleItemRemoved(
  dispatch: AppDispatch,
  getState: () => RootState,
  event: ItemRemovedEvent
): void {
  const state = getState();

  // Only update if we're viewing this list
  if (state.lists.currentListId === event.listId) {
    dispatch(removeItem(event.itemId));
  }
}

/**
 * Handle list:created event
 */
function handleListCreated(dispatch: AppDispatch, event: ListCreatedEvent): void {
  dispatch(
    addList({
      id: event.list.id,
      name: event.list.name,
      householdId: event.list.householdId,
      isPersonal: event.list.isPersonal,
      createdAt: event.timestamp,
      isOwner: false, // The current user is not the owner since it was created by someone else
    })
  );

  // Show toast for auto-generated lists from the recurring scheduler
  if (event.actor.id === 'system' && toastCallback) {
    toastCallback(i18n.t('recurring.autoListCreated', { name: event.list.name }));
  }
}

/**
 * Handle list:updated event
 */
function handleListUpdated(dispatch: AppDispatch, event: ListUpdatedEvent): void {
  dispatch(
    updateList({
      id: event.list.id,
      name: event.list.name,
    })
  );
}

/**
 * Handle list:deleted event
 */
function handleListDeleted(dispatch: AppDispatch, event: ListDeletedEvent): void {
  dispatch(removeList(event.listId));
}

/**
 * Handle comment:added event
 */
function handleCommentAdded(dispatch: AppDispatch, event: CommentAddedEvent): void {
  dispatch(
    addComment({
      targetType: event.targetType,
      targetId: event.targetId,
      comment: {
        id: event.comment.id,
        text: event.comment.text,
        authorId: event.comment.authorId,
        authorName: event.comment.authorName,
        authorAvatarUrl: event.comment.authorAvatarUrl,
        editedAt: event.comment.editedAt,
        createdAt: event.comment.createdAt,
      },
    })
  );
}

/**
 * Handle comment:updated event
 */
function handleCommentUpdated(dispatch: AppDispatch, event: CommentUpdatedEvent): void {
  dispatch(
    updateComment({
      targetType: event.targetType,
      targetId: event.targetId,
      commentId: event.commentId,
      text: event.text,
      editedAt: event.editedAt,
    })
  );
}

/**
 * Handle comment:deleted event
 */
function handleCommentDeleted(dispatch: AppDispatch, event: CommentDeletedEvent): void {
  dispatch(
    removeComment({
      targetType: event.targetType,
      targetId: event.targetId,
      commentId: event.commentId,
    })
  );
}

/**
 * Connect the WebSocket service with the user's JWT token.
 */
export function connectWebSocket(token: string): void {
  const wsService = getWebSocketService();
  wsService.connect(token);
}

/**
 * Disconnect the WebSocket service.
 */
export function disconnectWebSocket(): void {
  const wsService = getWebSocketService();
  wsService.disconnect();
}

/**
 * Subscribe to specific lists.
 */
export function subscribeToLists(listIds: string[]): void {
  const wsService = getWebSocketService();
  wsService.subscribe(listIds);
}

/**
 * Unsubscribe from specific lists.
 */
export function unsubscribeFromLists(listIds: string[]): void {
  const wsService = getWebSocketService();
  wsService.unsubscribe(listIds);
}
