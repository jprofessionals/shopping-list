import { describe, it, expect } from 'vitest';
import websocketReducer, {
  setConnectionState,
  setLastEventTimestamp,
  selectConnectionState,
  selectIsConnected,
  selectLastEventTimestamp,
  selectUIConnectionState,
  type WebSocketState,
  type ConnectionState,
} from './websocketSlice';
import type { RootState } from './store';

describe('websocketSlice', () => {
  const initialState: WebSocketState = {
    connectionState: 'disconnected',
    lastEventTimestamp: null,
  };

  it('should handle initial state', () => {
    expect(websocketReducer(undefined, { type: 'unknown' })).toEqual(initialState);
  });

  describe('setConnectionState', () => {
    it('should set connection state to connecting', () => {
      const state = websocketReducer(initialState, setConnectionState('connecting'));
      expect(state.connectionState).toBe('connecting');
    });

    it('should set connection state to connected', () => {
      const state = websocketReducer(initialState, setConnectionState('connected'));
      expect(state.connectionState).toBe('connected');
    });

    it('should set connection state to reconnecting', () => {
      const state = websocketReducer(initialState, setConnectionState('reconnecting'));
      expect(state.connectionState).toBe('reconnecting');
    });

    it('should set connection state to disconnected', () => {
      const connectedState: WebSocketState = {
        connectionState: 'connected',
        lastEventTimestamp: '2024-01-01T00:00:00Z',
      };
      const state = websocketReducer(connectedState, setConnectionState('disconnected'));
      expect(state.connectionState).toBe('disconnected');
    });
  });

  describe('setLastEventTimestamp', () => {
    it('should set last event timestamp', () => {
      const timestamp = '2024-01-15T10:30:00Z';
      const state = websocketReducer(initialState, setLastEventTimestamp(timestamp));
      expect(state.lastEventTimestamp).toBe(timestamp);
    });

    it('should clear last event timestamp when set to null', () => {
      const stateWithTimestamp: WebSocketState = {
        connectionState: 'connected',
        lastEventTimestamp: '2024-01-15T10:30:00Z',
      };
      const state = websocketReducer(stateWithTimestamp, setLastEventTimestamp(null));
      expect(state.lastEventTimestamp).toBeNull();
    });
  });

  describe('selectors', () => {
    const createMockRootState = (websocket: WebSocketState): RootState =>
      ({
        websocket,
        auth: {} as RootState['auth'],
        households: {} as RootState['households'],
        lists: {} as RootState['lists'],
      }) as RootState;

    describe('selectConnectionState', () => {
      it('should return the connection state', () => {
        const state = createMockRootState({
          connectionState: 'connected',
          lastEventTimestamp: null,
        });
        expect(selectConnectionState(state)).toBe('connected');
      });
    });

    describe('selectIsConnected', () => {
      it('should return true when connected', () => {
        const state = createMockRootState({
          connectionState: 'connected',
          lastEventTimestamp: null,
        });
        expect(selectIsConnected(state)).toBe(true);
      });

      it('should return false when disconnected', () => {
        const state = createMockRootState({
          connectionState: 'disconnected',
          lastEventTimestamp: null,
        });
        expect(selectIsConnected(state)).toBe(false);
      });

      it('should return false when connecting', () => {
        const state = createMockRootState({
          connectionState: 'connecting',
          lastEventTimestamp: null,
        });
        expect(selectIsConnected(state)).toBe(false);
      });

      it('should return false when reconnecting', () => {
        const state = createMockRootState({
          connectionState: 'reconnecting',
          lastEventTimestamp: null,
        });
        expect(selectIsConnected(state)).toBe(false);
      });
    });

    describe('selectLastEventTimestamp', () => {
      it('should return the last event timestamp', () => {
        const timestamp = '2024-01-15T10:30:00Z';
        const state = createMockRootState({
          connectionState: 'connected',
          lastEventTimestamp: timestamp,
        });
        expect(selectLastEventTimestamp(state)).toBe(timestamp);
      });

      it('should return null when no timestamp is set', () => {
        const state = createMockRootState({
          connectionState: 'connected',
          lastEventTimestamp: null,
        });
        expect(selectLastEventTimestamp(state)).toBeNull();
      });
    });

    describe('selectUIConnectionState', () => {
      const testCases: Array<{ input: ConnectionState; expected: string }> = [
        { input: 'disconnected', expected: 'OFFLINE' },
        { input: 'connecting', expected: 'CONNECTING' },
        { input: 'connected', expected: 'CONNECTED' },
        { input: 'reconnecting', expected: 'RECONNECTING' },
      ];

      testCases.forEach(({ input, expected }) => {
        it(`should return ${expected} when connection state is ${input}`, () => {
          const state = createMockRootState({
            connectionState: input,
            lastEventTimestamp: null,
          });
          expect(selectUIConnectionState(state)).toBe(expected);
        });
      });
    });
  });
});
