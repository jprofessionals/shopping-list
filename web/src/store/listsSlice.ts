import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface LastActivity {
  type:
    | 'item:added'
    | 'item:checked'
    | 'item:unchecked'
    | 'item:removed'
    | 'list:created'
    | 'list:updated';
  actorName: string;
  itemName?: string;
  timestamp: string;
}

export interface ShoppingList {
  id: string;
  name: string;
  householdId: string | null;
  isPersonal: boolean;
  createdAt: string;
  updatedAt?: string;
  isOwner: boolean;
  itemCount?: number;
  uncheckedCount?: number;
  previewItems?: string[];
  lastActivity?: LastActivity;
  isPinned?: boolean;
}

export interface ListItem {
  id: string;
  name: string;
  quantity: number;
  unit: string | null;
  isChecked: boolean;
  checkedByName: string | null;
  createdAt: string;
}

export interface ListsState {
  items: ShoppingList[];
  currentListId: string | null;
  currentListItems: ListItem[];
  isLoading: boolean;
  error: string | null;
}

const initialState: ListsState = {
  items: [],
  currentListId: null,
  currentListItems: [],
  isLoading: false,
  error: null,
};

const listsSlice = createSlice({
  name: 'lists',
  initialState,
  reducers: {
    setLists(state, action: PayloadAction<ShoppingList[]>) {
      state.items = action.payload;
      state.error = null;
    },
    addList(state, action: PayloadAction<ShoppingList>) {
      const existing = state.items.findIndex((l) => l.id === action.payload.id);
      if (existing >= 0) {
        state.items[existing] = action.payload;
      } else {
        state.items.push(action.payload);
      }
    },
    updateList(state, action: PayloadAction<{ id: string; name: string }>) {
      const list = state.items.find((l) => l.id === action.payload.id);
      if (list) {
        list.name = action.payload.name;
      }
    },
    removeList(state, action: PayloadAction<string>) {
      state.items = state.items.filter((l) => l.id !== action.payload);
    },
    setCurrentList(state, action: PayloadAction<string | null>) {
      state.currentListId = action.payload;
    },
    setItems(state, action: PayloadAction<ListItem[]>) {
      state.currentListItems = action.payload;
    },
    addItem(state, action: PayloadAction<ListItem>) {
      const exists = state.currentListItems.some((i) => i.id === action.payload.id);
      if (!exists) {
        state.currentListItems.push(action.payload);
      }
    },
    updateItem(state, action: PayloadAction<Partial<ListItem> & { id: string }>) {
      const item = state.currentListItems.find((i) => i.id === action.payload.id);
      if (item) {
        Object.assign(item, action.payload);
      }
    },
    removeItem(state, action: PayloadAction<string>) {
      state.currentListItems = state.currentListItems.filter((i) => i.id !== action.payload);
    },
    removeItems(state, action: PayloadAction<string[]>) {
      const idsToRemove = new Set(action.payload);
      state.currentListItems = state.currentListItems.filter((i) => !idsToRemove.has(i.id));
    },
    addItems(state, action: PayloadAction<ListItem[]>) {
      state.currentListItems.push(...action.payload);
    },
    toggleItemCheck(
      state,
      action: PayloadAction<{ id: string; isChecked: boolean; checkedByName: string | null }>
    ) {
      const item = state.currentListItems.find((i) => i.id === action.payload.id);
      if (item) {
        item.isChecked = action.payload.isChecked;
        item.checkedByName = action.payload.checkedByName;
      }
    },
    setLoading(state, action: PayloadAction<boolean>) {
      state.isLoading = action.payload;
    },
    setError(state, action: PayloadAction<string | null>) {
      state.error = action.payload;
    },
  },
});

export const {
  setLists,
  addList,
  updateList,
  removeList,
  setCurrentList,
  setItems,
  addItem,
  addItems,
  updateItem,
  removeItem,
  removeItems,
  toggleItemCheck,
  setLoading,
  setError,
} = listsSlice.actions;

export default listsSlice.reducer;
