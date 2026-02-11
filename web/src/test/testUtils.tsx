/* eslint-disable react-refresh/only-export-components */
import type { ReactElement, ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom';
import { configureStore, type EnhancedStore } from '@reduxjs/toolkit';
import { I18nextProvider } from 'react-i18next';
import authReducer from '../store/authSlice';
import commentsReducer from '../store/commentsSlice';
import householdsReducer from '../store/householdsSlice';
import listsReducer from '../store/listsSlice';
import recurringItemsReducer from '../store/recurringItemsSlice';
import websocketReducer from '../store/websocketSlice';
import { ToastProvider } from '../components/common';
import i18nForTests from './i18nForTests';

// Reducer map shared between store creation and type inference
const testReducers = {
  auth: authReducer,
  comments: commentsReducer,
  households: householdsReducer,
  lists: listsReducer,
  recurringItems: recurringItemsReducer,
  websocket: websocketReducer,
};

// Type for the preloaded state
type TestRootState = {
  [K in keyof typeof testReducers]: ReturnType<(typeof testReducers)[K]>;
};

// Create a test store with optional preloaded state
export const createTestStore = (preloadedState: Partial<TestRootState> = {}) =>
  configureStore({
    reducer: testReducers,
    preloadedState: preloadedState as TestRootState,
  });

interface WrapperOptions {
  store?: EnhancedStore;
  routerProps?: MemoryRouterProps;
}

// Wrapper component that provides Redux store, Router, Toast context, and i18n
function AllTheProviders({
  children,
  store,
  routerProps,
}: {
  children: ReactNode;
  store: EnhancedStore;
  routerProps?: MemoryRouterProps;
}) {
  return (
    <I18nextProvider i18n={i18nForTests}>
      <Provider store={store}>
        <ToastProvider>
          <MemoryRouter {...routerProps}>{children}</MemoryRouter>
        </ToastProvider>
      </Provider>
    </I18nextProvider>
  );
}

// Custom render function that wraps with providers
function customRender(
  ui: ReactElement,
  { store = createTestStore(), routerProps, ...renderOptions }: WrapperOptions & RenderOptions = {}
) {
  return render(ui, {
    wrapper: ({ children }) => (
      <AllTheProviders store={store} routerProps={routerProps}>
        {children}
      </AllTheProviders>
    ),
    ...renderOptions,
  });
}

// Re-export everything from testing library
export * from '@testing-library/react';

// Override render method
export { customRender as render };
