import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface RecurringItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  frequency: string;
  lastPurchased: string | null;
  isActive: boolean;
  pausedUntil: string | null;
  createdBy: {
    id: string;
    displayName: string;
  };
}

export interface RecurringItemsState {
  items: RecurringItem[];
  isLoading: boolean;
  error: string | null;
}

const initialState: RecurringItemsState = {
  items: [],
  isLoading: false,
  error: null,
};

const recurringItemsSlice = createSlice({
  name: 'recurringItems',
  initialState,
  reducers: {
    setRecurringItems(state, action: PayloadAction<RecurringItem[]>) {
      state.items = action.payload;
      state.error = null;
    },
    addRecurringItem(state, action: PayloadAction<RecurringItem>) {
      state.items.push(action.payload);
    },
    updateRecurringItem(state, action: PayloadAction<RecurringItem>) {
      const index = state.items.findIndex((i) => i.id === action.payload.id);
      if (index >= 0) {
        state.items[index] = action.payload;
      }
    },
    removeRecurringItem(state, action: PayloadAction<string>) {
      state.items = state.items.filter((i) => i.id !== action.payload);
    },
    setRecurringLoading(state, action: PayloadAction<boolean>) {
      state.isLoading = action.payload;
    },
    setRecurringError(state, action: PayloadAction<string | null>) {
      state.error = action.payload;
    },
  },
});

export const {
  setRecurringItems,
  addRecurringItem,
  updateRecurringItem,
  removeRecurringItem,
  setRecurringLoading,
  setRecurringError,
} = recurringItemsSlice.actions;

export default recurringItemsSlice.reducer;
