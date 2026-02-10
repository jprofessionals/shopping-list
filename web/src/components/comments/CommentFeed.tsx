import { useState, useEffect, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { type TFunction } from 'i18next';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import {
  setComments,
  addComment,
  updateComment,
  removeComment,
  type Comment,
} from '../../store/commentsSlice';

interface CommentFeedProps {
  targetType: 'LIST' | 'HOUSEHOLD';
  targetId: string;
}

function formatRelativeTime(dateString: string, t: TFunction): string {
  const now = Date.now();
  const date = new Date(dateString).getTime();
  const diffMs = now - date;
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffSeconds < 60) return t('comments.time.justNow');
  if (diffMinutes < 60) return t('comments.time.minutesAgo', { count: diffMinutes });
  if (diffHours < 24) return t('comments.time.hoursAgo', { count: diffHours });
  return t('comments.time.daysAgo', { count: diffDays });
}

function getApiBasePath(targetType: 'LIST' | 'HOUSEHOLD', targetId: string): string {
  if (targetType === 'LIST') {
    return `http://localhost:8080/lists/${targetId}/comments`;
  }
  return `http://localhost:8080/households/${targetId}/comments`;
}

export default function CommentFeed({ targetType, targetId }: CommentFeedProps) {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const token = useAppSelector((state) => state.auth.token);
  const currentUser = useAppSelector((state) => state.auth.user);
  const comments = useAppSelector(
    (state) => state.comments.commentsByTarget[`${targetType}:${targetId}`] ?? []
  );

  const [newText, setNewText] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editText, setEditText] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const prevCountRef = useRef(comments.length);

  // Fetch comments on mount
  useEffect(() => {
    if (!token) return;

    const fetchComments = async () => {
      try {
        const response = await fetch(getApiBasePath(targetType, targetId), {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (response.ok) {
          const data = await response.json();
          if (Array.isArray(data)) {
            dispatch(setComments({ targetType, targetId, comments: data }));
          }
        }
      } catch (err) {
        console.error('Failed to fetch comments:', err);
      }
    };

    fetchComments();
  }, [token, targetType, targetId, dispatch]);

  // Auto-scroll when new comments arrive
  useEffect(() => {
    if (comments.length > prevCountRef.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
    prevCountRef.current = comments.length;
  }, [comments.length]);

  const handleSubmit = useCallback(
    async (e?: React.FormEvent) => {
      e?.preventDefault();
      const text = newText.trim();
      if (!text || !token || !currentUser) return;

      // Optimistic add
      const tempId = `temp-${Date.now()}`;
      const optimisticComment: Comment = {
        id: tempId,
        text,
        authorId: currentUser.id,
        authorName: currentUser.displayName,
        authorAvatarUrl: currentUser.avatarUrl,
        editedAt: null,
        createdAt: new Date().toISOString(),
      };
      dispatch(addComment({ targetType, targetId, comment: optimisticComment }));
      setNewText('');

      try {
        const response = await fetch(getApiBasePath(targetType, targetId), {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ text }),
        });

        if (!response.ok) throw new Error('Failed to post comment');

        const created: Comment = await response.json();
        // Replace optimistic comment with server response
        dispatch(removeComment({ targetType, targetId, commentId: tempId }));
        dispatch(addComment({ targetType, targetId, comment: created }));
      } catch (err) {
        console.error('Failed to post comment:', err);
        // Remove optimistic comment on failure
        dispatch(removeComment({ targetType, targetId, commentId: tempId }));
      }
    },
    [newText, token, currentUser, targetType, targetId, dispatch]
  );

  const handleEdit = useCallback(
    async (commentId: string) => {
      const text = editText.trim();
      if (!text || !token) return;

      try {
        const response = await fetch(`${getApiBasePath(targetType, targetId)}/${commentId}`, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ text }),
        });

        if (!response.ok) throw new Error('Failed to update comment');

        const updated: Comment = await response.json();
        dispatch(
          updateComment({
            targetType,
            targetId,
            commentId,
            text: updated.text,
            editedAt: updated.editedAt ?? new Date().toISOString(),
          })
        );
        setEditingId(null);
        setEditText('');
      } catch (err) {
        console.error('Failed to update comment:', err);
      }
    },
    [editText, token, targetType, targetId, dispatch]
  );

  const handleDelete = useCallback(
    async (commentId: string) => {
      if (!token || !confirm(t('comments.confirmDelete'))) return;

      try {
        const response = await fetch(`${getApiBasePath(targetType, targetId)}/${commentId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` },
        });

        if (!response.ok) throw new Error('Failed to delete comment');

        dispatch(removeComment({ targetType, targetId, commentId }));
      } catch (err) {
        console.error('Failed to delete comment:', err);
      }
    },
    [token, targetType, targetId, dispatch, t]
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSubmit();
      }
    },
    [handleSubmit]
  );

  const startEditing = useCallback((comment: Comment) => {
    setEditingId(comment.id);
    setEditText(comment.text);
  }, []);

  const cancelEditing = useCallback(() => {
    setEditingId(null);
    setEditText('');
  }, []);

  return (
    <div className="rounded-lg bg-white p-4 shadow dark:bg-gray-800 dark:shadow-gray-900/20">
      <h3 className="mb-4 text-lg font-semibold text-gray-900 dark:text-white">
        {t('comments.title')}
      </h3>

      {/* Comments list */}
      <div className="space-y-4">
        {comments.length === 0 && (
          <p className="py-4 text-center text-sm text-gray-500 dark:text-gray-400">
            {t('comments.noComments')}
          </p>
        )}

        {comments.map((comment) => (
          <div key={comment.id} className="flex gap-3" data-testid={`comment-${comment.id}`}>
            {/* Avatar */}
            <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-gray-200 text-sm font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-300">
              {comment.authorName.charAt(0).toUpperCase()}
            </div>

            {/* Content */}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-gray-900 dark:text-white">
                  {comment.authorName}
                </span>
                <span className="text-xs text-gray-500 dark:text-gray-400">
                  {formatRelativeTime(comment.createdAt, t)}
                </span>
              </div>

              {editingId === comment.id ? (
                <div className="mt-1 flex gap-2">
                  <input
                    type="text"
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        handleEdit(comment.id);
                      }
                      if (e.key === 'Escape') {
                        cancelEditing();
                      }
                    }}
                    className="flex-1 rounded-md border border-gray-300 px-3 py-1 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white"
                    autoFocus
                  />
                  <button
                    onClick={() => handleEdit(comment.id)}
                    className="rounded-md bg-indigo-600 px-3 py-1 text-xs font-medium text-white hover:bg-indigo-500"
                  >
                    {t('comments.save')}
                  </button>
                  <button
                    onClick={cancelEditing}
                    className="rounded-md bg-gray-100 px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                  >
                    {t('common.cancel')}
                  </button>
                </div>
              ) : (
                <>
                  <p className="mt-0.5 text-sm text-gray-700 dark:text-gray-300">
                    {comment.text}
                    {comment.editedAt && (
                      <span className="ml-2 text-xs italic text-gray-400 dark:text-gray-500">
                        {t('comments.edited')}
                      </span>
                    )}
                  </p>

                  {currentUser && comment.authorId === currentUser.id && (
                    <div className="mt-1 flex gap-2">
                      <button
                        onClick={() => startEditing(comment)}
                        className="text-xs font-medium text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                      >
                        {t('comments.edit')}
                      </button>
                      <button
                        onClick={() => handleDelete(comment.id)}
                        className="text-xs font-medium text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                      >
                        {t('common.delete')}
                      </button>
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        ))}

        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <form onSubmit={handleSubmit} className="mt-4 flex gap-2">
        <input
          type="text"
          value={newText}
          onChange={(e) => setNewText(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t('comments.placeholder')}
          className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-400"
        />
        <button
          type="submit"
          disabled={!newText.trim()}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {t('comments.send')}
        </button>
      </form>
    </div>
  );
}
