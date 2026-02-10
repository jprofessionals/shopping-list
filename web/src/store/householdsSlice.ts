import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface Household {
  id: string;
  name: string;
  createdAt: string;
  memberCount: number;
  isOwner: boolean;
}

export interface HouseholdsState {
  items: Household[];
  isLoading: boolean;
  error: string | null;
}

const initialState: HouseholdsState = {
  items: [],
  isLoading: false,
  error: null,
};

const householdsSlice = createSlice({
  name: 'households',
  initialState,
  reducers: {
    setHouseholds(state, action: PayloadAction<Household[]>) {
      state.items = action.payload;
      state.error = null;
    },
    addHousehold(state, action: PayloadAction<Household>) {
      state.items.push(action.payload);
    },
    updateHousehold(state, action: PayloadAction<{ id: string; name: string }>) {
      const household = state.items.find((h) => h.id === action.payload.id);
      if (household) {
        household.name = action.payload.name;
      }
    },
    removeHousehold(state, action: PayloadAction<string>) {
      state.items = state.items.filter((h) => h.id !== action.payload);
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
  setHouseholds,
  addHousehold,
  updateHousehold,
  removeHousehold,
  setLoading,
  setError,
} = householdsSlice.actions;

export default householdsSlice.reducer;
