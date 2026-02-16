import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { setLists, removeList, setLoading as setListsLoading } from '../../store/listsSlice';
import { apiFetch } from '../../services/api';
import { ShoppingListsPage, CreateListModal } from '../shopping-list';

export default function ListsPage() {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);
  const lists = useAppSelector((state) => state.lists.items);

  useEffect(() => {
    const fetchLists = async () => {
      if (!token) return;
      dispatch(setListsLoading(true));
      try {
        const response = await apiFetch('/lists');
        if (response.ok) {
          const data = await response.json();
          dispatch(setLists(data));
        }
      } catch (err) {
        console.error('Failed to fetch lists:', err);
      } finally {
        dispatch(setListsLoading(false));
      }
    };

    fetchLists();
  }, [token, dispatch]);

  const handleSelectList = (listId: string) => {
    navigate(`/lists/${listId}`);
  };

  const handleCreateClick = () => {
    setShowCreateModal(true);
  };

  const handlePin = useCallback(
    async (listId: string) => {
      if (!token) return;
      try {
        const response = await apiFetch(`/lists/${listId}/pin`, {
          method: 'POST',
        });
        if (response.ok) {
          dispatch(setLists(lists.map((l) => (l.id === listId ? { ...l, isPinned: true } : l))));
        }
      } catch (err) {
        console.error('Failed to pin list:', err);
      }
    },
    [token, lists, dispatch]
  );

  const handleUnpin = useCallback(
    async (listId: string) => {
      if (!token) return;
      try {
        const response = await apiFetch(`/lists/${listId}/pin`, {
          method: 'DELETE',
        });
        if (response.ok) {
          dispatch(setLists(lists.map((l) => (l.id === listId ? { ...l, isPinned: false } : l))));
        }
      } catch (err) {
        console.error('Failed to unpin list:', err);
      }
    },
    [token, lists, dispatch]
  );

  const handleDelete = useCallback(
    async (listId: string) => {
      if (!token) return;
      try {
        const response = await apiFetch(`/lists/${listId}`, {
          method: 'DELETE',
        });
        if (response.ok) {
          dispatch(removeList(listId));
        }
      } catch (err) {
        console.error('Failed to delete list:', err);
      }
    },
    [token, dispatch]
  );

  return (
    <>
      <ShoppingListsPage
        onSelectList={handleSelectList}
        onCreateClick={handleCreateClick}
        onPin={handlePin}
        onUnpin={handleUnpin}
        onDelete={handleDelete}
      />
      {showCreateModal && <CreateListModal onClose={() => setShowCreateModal(false)} />}
    </>
  );
}
