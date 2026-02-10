import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect } from 'vitest';
import ConnectionStatus from './ConnectionStatus';
import websocketReducer, { type ConnectionState } from '../../store/websocketSlice';

const createTestStore = (connectionState: ConnectionState = 'disconnected') =>
  configureStore({
    reducer: { websocket: websocketReducer },
    preloadedState: {
      websocket: {
        connectionState,
        lastEventTimestamp: null,
      },
    },
  });

describe('ConnectionStatus', () => {
  it('shows spinner when connecting', () => {
    render(
      <Provider store={createTestStore('connecting')}>
        <ConnectionStatus />
      </Provider>
    );

    expect(screen.getByTestId('connection-spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('connection-dot')).not.toBeInTheDocument();
    expect(screen.queryByTestId('connection-text')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/connection status: connecting/i)).toBeInTheDocument();
  });

  it('shows green dot when connected', () => {
    render(
      <Provider store={createTestStore('connected')}>
        <ConnectionStatus />
      </Provider>
    );

    const dot = screen.getByTestId('connection-dot');
    expect(dot).toBeInTheDocument();
    expect(dot).toHaveClass('bg-green-500');
    expect(screen.queryByTestId('connection-spinner')).not.toBeInTheDocument();
    expect(screen.queryByTestId('connection-text')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/connection status: connected/i)).toBeInTheDocument();
  });

  it('shows yellow dot and "Reconnecting..." text when reconnecting', () => {
    render(
      <Provider store={createTestStore('reconnecting')}>
        <ConnectionStatus />
      </Provider>
    );

    const dot = screen.getByTestId('connection-dot');
    expect(dot).toBeInTheDocument();
    expect(dot).toHaveClass('bg-yellow-500');
    expect(screen.getByTestId('connection-text')).toHaveTextContent('Reconnecting...');
    expect(screen.queryByTestId('connection-spinner')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/connection status: reconnecting/i)).toBeInTheDocument();
  });

  it('shows red dot and "Offline" text when disconnected', () => {
    render(
      <Provider store={createTestStore('disconnected')}>
        <ConnectionStatus />
      </Provider>
    );

    const dot = screen.getByTestId('connection-dot');
    expect(dot).toBeInTheDocument();
    expect(dot).toHaveClass('bg-red-500');
    expect(screen.getByTestId('connection-text')).toHaveTextContent('Offline');
    expect(screen.queryByTestId('connection-spinner')).not.toBeInTheDocument();
    expect(screen.getByLabelText(/connection status: offline/i)).toBeInTheDocument();
  });

  it('accepts custom className', () => {
    render(
      <Provider store={createTestStore('connected')}>
        <ConnectionStatus className="custom-class" />
      </Provider>
    );

    expect(screen.getByTestId('connection-status')).toHaveClass('custom-class');
  });
});
