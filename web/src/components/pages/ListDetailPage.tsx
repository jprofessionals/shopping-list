import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import {
  setItems,
  addList,
  setLists,
  setCurrentList,
  type ShoppingList,
} from '../../store/listsSlice';
import { ShoppingListView, ShareListModal } from '../shopping-list';
import { LoadingSpinner, ErrorAlert } from '../common';

export default function ListDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);
  const lists = useAppSelector((state) => state.lists.items);
  const list = lists.find((l) => l.id === id);
  const [showShareModal, setShowShareModal] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id) {
      dispatch(setCurrentList(id));
    }
    return () => {
      dispatch(setCurrentList(null));
    };
  }, [id, dispatch]);

  useEffect(() => {
    const fetchListItems = async () => {
      if (!token || !id) return;
      setIsLoading(true);
      setError(null);
      try {
        const response = await fetch(`http://localhost:8080/lists/${id}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!response.ok) {
          throw new Error('Failed to load list');
        }
        const data = await response.json();
        dispatch(setItems(data.items || []));

        // If the list isn't in Redux yet (direct navigation), add it
        const listMeta: ShoppingList = {
          id: data.id,
          name: data.name,
          householdId: data.householdId ?? null,
          isPersonal: data.isPersonal,
          createdAt: data.createdAt,
          isOwner: data.isOwner,
          isPinned: data.isPinned ?? false,
        };
        dispatch(addList(listMeta));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setIsLoading(false);
      }
    };

    fetchListItems();
  }, [id, token, dispatch]);

  const handleBack = () => {
    navigate('/lists');
  };

  const handlePinToggle = async () => {
    if (!token || !id) return;
    const isPinned = list?.isPinned;
    try {
      const response = await fetch(`http://localhost:8080/lists/${id}/pin`, {
        method: isPinned ? 'DELETE' : 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (response.ok) {
        dispatch(setLists(lists.map((l) => (l.id === id ? { ...l, isPinned: !isPinned } : l))));
      }
    } catch (err) {
      console.error('Failed to toggle pin:', err);
    }
  };

  if (!id) {
    return (
      <ErrorAlert message="Invalid list ID">
        <button onClick={handleBack} className="mt-2 text-sm text-red-600 underline">
          Go back to lists
        </button>
      </ErrorAlert>
    );
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorAlert message={error}>
        <button onClick={handleBack} className="mt-2 text-sm text-red-600 underline">
          Go back to lists
        </button>
      </ErrorAlert>
    );
  }

  return (
    <>
      <ShoppingListView
        listId={id}
        onBack={handleBack}
        onShareClick={list?.isOwner ? () => setShowShareModal(true) : undefined}
        onPinToggle={handlePinToggle}
      />
      {showShareModal && <ShareListModal listId={id} onClose={() => setShowShareModal(false)} />}
    </>
  );
}
