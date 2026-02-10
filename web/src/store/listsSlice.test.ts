import { describe, it, expect } from 'vitest';
import listsReducer, {
  setLists,
  addList,
  updateList,
  removeList,
  setCurrentList,
  setItems,
  addItem,
  updateItem,
  removeItem,
  toggleItemCheck,
  setLoading,
  setError,
  type ShoppingList,
  type ListItem,
  type ListsState,
} from './listsSlice';

const createMockList = (overrides: Partial<ShoppingList> = {}): ShoppingList => ({
  id: 'list-1',
  name: 'Groceries',
  householdId: 'household-1',
  isPersonal: false,
  createdAt: '2024-01-01T00:00:00Z',
  isOwner: true,
  ...overrides,
});

const createMockItem = (overrides: Partial<ListItem> = {}): ListItem => ({
  id: 'item-1',
  name: 'Milk',
  quantity: 2,
  unit: 'liters',
  isChecked: false,
  checkedByName: null,
  createdAt: '2024-01-01T00:00:00Z',
  ...overrides,
});

const initialState: ListsState = {
  items: [],
  currentListId: null,
  currentListItems: [],
  isLoading: false,
  error: null,
};

describe('listsSlice', () => {
  describe('list actions', () => {
    it('should return the initial state', () => {
      expect(listsReducer(undefined, { type: 'unknown' })).toEqual(initialState);
    });

    it('should set lists', () => {
      const lists = [createMockList(), createMockList({ id: 'list-2', name: 'Work' })];
      const state = listsReducer(initialState, setLists(lists));

      expect(state.items).toEqual(lists);
      expect(state.error).toBeNull();
    });

    it('should add a list', () => {
      const list = createMockList();
      const state = listsReducer(initialState, addList(list));

      expect(state.items).toHaveLength(1);
      expect(state.items[0]).toEqual(list);
    });

    it('should update a list', () => {
      const list = createMockList();
      const stateWithList = { ...initialState, items: [list] };
      const state = listsReducer(
        stateWithList,
        updateList({ id: 'list-1', name: 'Updated Groceries' })
      );

      expect(state.items[0].name).toBe('Updated Groceries');
    });

    it('should not update a non-existent list', () => {
      const list = createMockList();
      const stateWithList = { ...initialState, items: [list] };
      const state = listsReducer(
        stateWithList,
        updateList({ id: 'non-existent', name: 'Updated' })
      );

      expect(state.items[0].name).toBe('Groceries');
    });

    it('should remove a list', () => {
      const list = createMockList();
      const stateWithList = { ...initialState, items: [list] };
      const state = listsReducer(stateWithList, removeList('list-1'));

      expect(state.items).toHaveLength(0);
    });

    it('should set current list', () => {
      const state = listsReducer(initialState, setCurrentList('list-1'));

      expect(state.currentListId).toBe('list-1');
    });

    it('should clear current list', () => {
      const stateWithCurrentList = { ...initialState, currentListId: 'list-1' };
      const state = listsReducer(stateWithCurrentList, setCurrentList(null));

      expect(state.currentListId).toBeNull();
    });
  });

  describe('item actions', () => {
    it('should set items', () => {
      const items = [createMockItem(), createMockItem({ id: 'item-2', name: 'Bread' })];
      const state = listsReducer(initialState, setItems(items));

      expect(state.currentListItems).toEqual(items);
    });

    it('should add an item', () => {
      const item = createMockItem();
      const state = listsReducer(initialState, addItem(item));

      expect(state.currentListItems).toHaveLength(1);
      expect(state.currentListItems[0]).toEqual(item);
    });

    it('should update an item', () => {
      const item = createMockItem();
      const stateWithItem = { ...initialState, currentListItems: [item] };
      const state = listsReducer(
        stateWithItem,
        updateItem({ id: 'item-1', name: 'Whole Milk', quantity: 3 })
      );

      expect(state.currentListItems[0].name).toBe('Whole Milk');
      expect(state.currentListItems[0].quantity).toBe(3);
    });

    it('should not update a non-existent item', () => {
      const item = createMockItem();
      const stateWithItem = { ...initialState, currentListItems: [item] };
      const state = listsReducer(
        stateWithItem,
        updateItem({ id: 'non-existent', name: 'Updated' })
      );

      expect(state.currentListItems[0].name).toBe('Milk');
    });

    it('should remove an item', () => {
      const item = createMockItem();
      const stateWithItem = { ...initialState, currentListItems: [item] };
      const state = listsReducer(stateWithItem, removeItem('item-1'));

      expect(state.currentListItems).toHaveLength(0);
    });

    it('should toggle item check', () => {
      const item = createMockItem();
      const stateWithItem = { ...initialState, currentListItems: [item] };
      const state = listsReducer(
        stateWithItem,
        toggleItemCheck({ id: 'item-1', isChecked: true, checkedByName: 'John' })
      );

      expect(state.currentListItems[0].isChecked).toBe(true);
      expect(state.currentListItems[0].checkedByName).toBe('John');
    });

    it('should uncheck item and clear checkedByName', () => {
      const item = createMockItem({ isChecked: true, checkedByName: 'John' });
      const stateWithItem = { ...initialState, currentListItems: [item] };
      const state = listsReducer(
        stateWithItem,
        toggleItemCheck({ id: 'item-1', isChecked: false, checkedByName: null })
      );

      expect(state.currentListItems[0].isChecked).toBe(false);
      expect(state.currentListItems[0].checkedByName).toBeNull();
    });

    it('should not toggle check for non-existent item', () => {
      const item = createMockItem();
      const stateWithItem = { ...initialState, currentListItems: [item] };
      const state = listsReducer(
        stateWithItem,
        toggleItemCheck({ id: 'non-existent', isChecked: true, checkedByName: 'John' })
      );

      expect(state.currentListItems[0].isChecked).toBe(false);
    });
  });

  describe('loading and error actions', () => {
    it('should set loading', () => {
      const state = listsReducer(initialState, setLoading(true));

      expect(state.isLoading).toBe(true);
    });

    it('should set error', () => {
      const state = listsReducer(initialState, setError('Something went wrong'));

      expect(state.error).toBe('Something went wrong');
    });

    it('should clear error', () => {
      const stateWithError = { ...initialState, error: 'Previous error' };
      const state = listsReducer(stateWithError, setError(null));

      expect(state.error).toBeNull();
    });
  });
});
