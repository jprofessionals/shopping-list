import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { RootState } from './store';

// Connection states matching the WebSocket service
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

// Mapping to more user-friendly UI states
export type UIConnectionState = 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'OFFLINE';

export interface WebSocketState {
  connectionState: ConnectionState;
  lastEventTimestamp: string | null;
}

const initialState: WebSocketState = {
  connectionState: 'disconnected',
  lastEventTimestamp: null,
};

const websocketSlice = createSlice({
  name: 'websocket',
  initialState,
  reducers: {
    setConnectionState(state, action: PayloadAction<ConnectionState>) {
      state.connectionState = action.payload;
    },
    setLastEventTimestamp(state, action: PayloadAction<string | null>) {
      state.lastEventTimestamp = action.payload;
    },
  },
});

export const { setConnectionState, setLastEventTimestamp } = websocketSlice.actions;

// Selectors
export const selectConnectionState = (state: RootState): ConnectionState =>
  state.websocket.connectionState;

export const selectIsConnected = (state: RootState): boolean =>
  state.websocket.connectionState === 'connected';

export const selectLastEventTimestamp = (state: RootState): string | null =>
  state.websocket.lastEventTimestamp;

/**
 * Map the internal connection state to a UI-friendly state.
 */
export const selectUIConnectionState = (state: RootState): UIConnectionState => {
  switch (state.websocket.connectionState) {
    case 'connecting':
      return 'CONNECTING';
    case 'connected':
      return 'CONNECTED';
    case 'reconnecting':
      return 'RECONNECTING';
    case 'disconnected':
    default:
      return 'OFFLINE';
  }
};

export default websocketSlice.reducer;
