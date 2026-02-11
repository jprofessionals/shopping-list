import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { apiFetch } from '../services/api';

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

export interface RecurringItemInput {
  name: string;
  quantity: number;
  unit: string | null;
  frequency: string;
}

export interface RecurringItemsState {
  items: RecurringItem[];
  isLoading: boolean;
  error: string | null;
}

export const fetchRecurringItems = createAsyncThunk(
  'recurringItems/fetch',
  async (householdId: string) => {
    const response = await apiFetch(`/households/${householdId}/recurring-items`);
    if (!response.ok) throw new Error('Failed to fetch recurring items');
    const data = await response.json();
    return Array.isArray(data) ? (data as RecurringItem[]) : [];
  }
);

export const createRecurringItem = createAsyncThunk(
  'recurringItems/create',
  async ({ householdId, data }: { householdId: string; data: RecurringItemInput }) => {
    const response = await apiFetch(`/households/${householdId}/recurring-items`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error('Failed to create recurring item');
    return (await response.json()) as RecurringItem;
  }
);

export const updateRecurringItem = createAsyncThunk(
  'recurringItems/update',
  async ({
    householdId,
    itemId,
    data,
  }: {
    householdId: string;
    itemId: string;
    data: RecurringItemInput;
  }) => {
    const response = await apiFetch(`/households/${householdId}/recurring-items/${itemId}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error('Failed to update recurring item');
    return (await response.json()) as RecurringItem;
  }
);

export const deleteRecurringItem = createAsyncThunk(
  'recurringItems/delete',
  async ({ householdId, itemId }: { householdId: string; itemId: string }) => {
    const response = await apiFetch(`/households/${householdId}/recurring-items/${itemId}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('Failed to delete');
    return itemId;
  }
);

export const pauseRecurringItem = createAsyncThunk(
  'recurringItems/pause',
  async ({
    householdId,
    itemId,
    until,
  }: {
    householdId: string;
    itemId: string;
    until: string | null;
  }) => {
    const response = await apiFetch(`/households/${householdId}/recurring-items/${itemId}/pause`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ until }),
    });
    if (!response.ok) throw new Error('Failed to pause');
    return (await response.json()) as RecurringItem;
  }
);

export const resumeRecurringItem = createAsyncThunk(
  'recurringItems/resume',
  async ({ householdId, itemId }: { householdId: string; itemId: string }) => {
    const response = await apiFetch(`/households/${householdId}/recurring-items/${itemId}/resume`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to resume');
    return (await response.json()) as RecurringItem;
  }
);

const initialState: RecurringItemsState = {
  items: [],
  isLoading: false,
  error: null,
};

const recurringItemsSlice = createSlice({
  name: 'recurringItems',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchRecurringItems.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(fetchRecurringItems.fulfilled, (state, action) => {
        state.items = action.payload;
        state.isLoading = false;
      })
      .addCase(fetchRecurringItems.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.error.message ?? 'Failed to fetch';
      })
      .addCase(createRecurringItem.fulfilled, (state, action) => {
        state.items.push(action.payload);
      })
      .addCase(createRecurringItem.rejected, (state, action) => {
        state.error = action.error.message ?? 'Failed to create';
      })
      .addCase(updateRecurringItem.fulfilled, (state, action) => {
        const index = state.items.findIndex((i) => i.id === action.payload.id);
        if (index >= 0) state.items[index] = action.payload;
      })
      .addCase(updateRecurringItem.rejected, (state, action) => {
        state.error = action.error.message ?? 'Failed to update';
      })
      .addCase(deleteRecurringItem.fulfilled, (state, action) => {
        state.items = state.items.filter((i) => i.id !== action.payload);
      })
      .addCase(deleteRecurringItem.rejected, (state, action) => {
        state.error = action.error.message ?? 'Failed to delete';
      })
      .addCase(pauseRecurringItem.fulfilled, (state, action) => {
        const index = state.items.findIndex((i) => i.id === action.payload.id);
        if (index >= 0) state.items[index] = action.payload;
      })
      .addCase(pauseRecurringItem.rejected, (state, action) => {
        state.error = action.error.message ?? 'Failed to pause';
      })
      .addCase(resumeRecurringItem.fulfilled, (state, action) => {
        const index = state.items.findIndex((i) => i.id === action.payload.id);
        if (index >= 0) state.items[index] = action.payload;
      })
      .addCase(resumeRecurringItem.rejected, (state, action) => {
        state.error = action.error.message ?? 'Failed to resume';
      });
  },
});

export default recurringItemsSlice.reducer;
