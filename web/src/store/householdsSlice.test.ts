import { describe, it, expect } from 'vitest';
import householdsReducer, {
  setHouseholds,
  addHousehold,
  updateHousehold,
  removeHousehold,
  setLoading,
  setError,
  type HouseholdsState,
  type Household,
} from './householdsSlice';

describe('householdsSlice', () => {
  const initialState: HouseholdsState = {
    items: [],
    isLoading: false,
    error: null,
  };

  const mockHousehold: Household = {
    id: '1',
    name: 'Home',
    createdAt: '2024-01-01T00:00:00Z',
    memberCount: 2,
    isOwner: true,
  };

  it('should handle initial state', () => {
    expect(householdsReducer(undefined, { type: 'unknown' })).toEqual(initialState);
  });

  it('should handle setHouseholds', () => {
    const state = householdsReducer(initialState, setHouseholds([mockHousehold]));
    expect(state.items).toHaveLength(1);
    expect(state.items[0].name).toBe('Home');
  });

  it('should handle addHousehold', () => {
    const state = householdsReducer(initialState, addHousehold(mockHousehold));
    expect(state.items).toHaveLength(1);
  });

  it('should handle updateHousehold', () => {
    const stateWithHousehold = { ...initialState, items: [mockHousehold] };
    const state = householdsReducer(
      stateWithHousehold,
      updateHousehold({ id: '1', name: 'New Name' })
    );
    expect(state.items[0].name).toBe('New Name');
  });

  it('should handle removeHousehold', () => {
    const stateWithHousehold = { ...initialState, items: [mockHousehold] };
    const state = householdsReducer(stateWithHousehold, removeHousehold('1'));
    expect(state.items).toHaveLength(0);
  });

  it('should handle setLoading', () => {
    const state = householdsReducer(initialState, setLoading(true));
    expect(state.isLoading).toBe(true);
  });

  it('should handle setError', () => {
    const state = householdsReducer(initialState, setError('Something went wrong'));
    expect(state.error).toBe('Something went wrong');
  });
});
