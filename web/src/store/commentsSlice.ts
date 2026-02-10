import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface Comment {
  id: string;
  text: string;
  authorId: string;
  authorName: string;
  authorAvatarUrl: string | null;
  editedAt: string | null;
  createdAt: string;
}

export interface CommentsState {
  commentsByTarget: Record<string, Comment[]>;
  loading: boolean;
}

const initialState: CommentsState = {
  commentsByTarget: {},
  loading: false,
};

function targetKey(targetType: string, targetId: string): string {
  return `${targetType}:${targetId}`;
}

const commentsSlice = createSlice({
  name: 'comments',
  initialState,
  reducers: {
    setComments(
      state,
      action: PayloadAction<{ targetType: string; targetId: string; comments: Comment[] }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      state.commentsByTarget[key] = action.payload.comments;
    },
    addComment(
      state,
      action: PayloadAction<{ targetType: string; targetId: string; comment: Comment }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      if (!state.commentsByTarget[key]) {
        state.commentsByTarget[key] = [];
      }
      const existing = state.commentsByTarget[key].find((c) => c.id === action.payload.comment.id);
      if (!existing) {
        state.commentsByTarget[key].push(action.payload.comment);
      }
    },
    updateComment(
      state,
      action: PayloadAction<{
        targetType: string;
        targetId: string;
        commentId: string;
        text: string;
        editedAt: string;
      }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      const comments = state.commentsByTarget[key];
      if (comments) {
        const comment = comments.find((c) => c.id === action.payload.commentId);
        if (comment) {
          comment.text = action.payload.text;
          comment.editedAt = action.payload.editedAt;
        }
      }
    },
    removeComment(
      state,
      action: PayloadAction<{ targetType: string; targetId: string; commentId: string }>
    ) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      const comments = state.commentsByTarget[key];
      if (comments) {
        state.commentsByTarget[key] = comments.filter((c) => c.id !== action.payload.commentId);
      }
    },
    setCommentsLoading(state, action: PayloadAction<boolean>) {
      state.loading = action.payload;
    },
    clearComments(state, action: PayloadAction<{ targetType: string; targetId: string }>) {
      const key = targetKey(action.payload.targetType, action.payload.targetId);
      delete state.commentsByTarget[key];
    },
  },
});

export const {
  setComments,
  addComment,
  updateComment,
  removeComment,
  setCommentsLoading,
  clearComments,
} = commentsSlice.actions;

export default commentsSlice.reducer;
