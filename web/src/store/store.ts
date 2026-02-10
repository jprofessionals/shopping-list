import { configureStore } from '@reduxjs/toolkit';
import authReducer from './authSlice';
import householdsReducer from './householdsSlice';
import listsReducer from './listsSlice';
import websocketReducer from './websocketSlice';
import commentsReducer from './commentsSlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    households: householdsReducer,
    lists: listsReducer,
    websocket: websocketReducer,
    comments: commentsReducer,
  },
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
