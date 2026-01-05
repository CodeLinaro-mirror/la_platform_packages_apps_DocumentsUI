/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import static com.android.documentsui.util.FlagUtils.isCloudFeaturesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isDragsFromOtherAppsEnabled;
import static com.android.documentsui.util.FlagUtils.isHomeScreenFilesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.Trace;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.DragAndDropPermissions;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.IconHelper;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * Manager that tracks control key state, calculates the default file operation (move or copy)
 * when user drops, and updates drag shadow state.
 */
public interface DragAndDropManager {

    String TAG = "DragAndDropManager";

    /** Used to mock the output of {@link #requestPermissions()}. */
    @VisibleForTesting
    AtomicReference<BiFunction<Activity, DragEvent, Permissions>>
            REQUEST_PERMISSIONS_HANDLER_FOR_TESTING = new AtomicReference<>();

    /**
     * A thin wrapper around {@link DragAndDropPermissions} which exists solely to facilitate
     * testing being as the wrapped class is both final and uninstantiable.
     */
    class Permissions {

        private final DragAndDropPermissions mPermissions;

        public Permissions(DragAndDropPermissions permissions) {
            mPermissions = permissions;
        }

        /**
         * @see DragAndDropPermissions#release()
         */
        public void release() {
            mPermissions.release();
        }
    }

    @IntDef({
        STATE_NOT_ALLOWED,
        STATE_UNKNOWN,
        STATE_MOVE,
        STATE_COPY,
        STATE_TRASH,
        STATE_RESTORES_FROM_TRASH
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface State {}
    int STATE_UNKNOWN = 0;
    int STATE_NOT_ALLOWED = 1;
    int STATE_MOVE = 2;
    int STATE_COPY = 3;
    int STATE_TRASH = 4;
    int STATE_RESTORES_FROM_TRASH = 5;

    /**
     * Intercepts and handles a {@link KeyEvent}. Used to track the state of Ctrl key state.
     */
    void onKeyEvent(KeyEvent event);

    /**
     * Starts a drag and drop.
     *
     * @param v the view which {@link View#startDragAndDrop(ClipData, View.DragShadowBuilder,
     *     Object, int)} will be called.
     * @param srcs documents that are dragged
     * @param itemInfo the root in which documents being dragged are
     * @param invalidDest destinations that don't accept this drag and drop
     * @param iconHelper used to load document icons
     * @param parent {@link DocumentInfo} of the container of srcs
     * @param canDragAndDrop Whether the items can be dragged and dropped.
     */
    void startDrag(
            View v,
            List<DocumentInfo> srcs,
            SidebarEntryItemInfo itemInfo,
            List<Uri> invalidDest,
            SelectionDetails selectionDetails,
            IconHelper iconHelper,
            @Nullable DocumentInfo parent,
            boolean canDragAndDrop);

    /**
     * Checks whether the document can be spring opened.
     * @param root the root in which the document is
     * @param doc the document to check
     * @return true if policy allows spring opening it; false otherwise
     */
    boolean canSpringOpen(RootInfo root, DocumentInfo doc);

    /**
     * Updates the state to {@link #STATE_NOT_ALLOWED} without any further checks. This is used when
     * the UI component that handles the drag event already has enough information to disallow
     * dropping by itself.
     *
     * @param v the view which {@link View#updateDragShadow(View.DragShadowBuilder)} will be called.
     */
    void updateStateToNotAllowed(View v);

    /**
     * Updates the state according to the destination passed.
     *
     * @param v the view which {@link View#updateDragShadow(View.DragShadowBuilder)} will be called.
     * @param destItemInfo the root or shortcut of the destination document.
     * @param destDoc the destination document. Can be null if this is TBD. Must be a folder.
     * @return the new state. Can be any state in {@link State}.
     */
    @State
    int updateState(
            View v, @Nullable SidebarEntryItemInfo destItemInfo, @Nullable DocumentInfo destDoc);

    /**
     * Resets state back to {@link #STATE_UNKNOWN}. This is used when user drags items leaving a UI
     * component.
     * @param v the view which {@link View#updateDragShadow(View.DragShadowBuilder)} will be called.
     */
    void resetState(View v);

    /**
     * Checks whether the drag was initiated from FilesApp.
     * @return true if initiated from Files app.
     */
    boolean isDragFromSameApp();

    /**
     * Drops items onto the a root.
     *
     * @param permissions permissions granted for the drop event, released once no longer needed.
     * @param clipData the clip data that contains sources information.
     * @param localState used to determine if this is a multi-window drag and drop.
     * @param itemInfo the target root
     * @param actions {@link ActionHandler} used to load root document.
     * @param callback callback called when file operation is rejected or scheduled.
     * @param invalidDest a list of URIs representing invalid drop destinations
     * @return true if target accepts this drop; false otherwise
     */
    boolean drop(
            @Nullable Permissions permissions,
            ClipData clipData,
            Object localState,
            SidebarEntryItemInfo itemInfo,
            ActionHandler actions,
            FileOperations.Callback callback,
            List<Uri> invalidDest);

    /**
     * Drops items onto the target.
     *
     * @param permissions permissions granted for the drop event, released once no longer needed.
     * @param clipData the clip data that contains sources information.
     * @param localState used to determine if this is a multi-window drag and drop.
     * @param dstStack the document stack pointing to the destination folder.
     * @param callback callback called when file operation is rejected or scheduled.
     * @return true if target accepts this drop; false otherwise
     */
    boolean drop(
            @Nullable Permissions permissions,
            ClipData clipData,
            Object localState,
            DocumentStack dstStack,
            ActionHandler actions,
            FileOperations.Callback callback);

    /**
     * Called when drag and drop ended.
     *
     * This can be called multiple times as multiple {@link View.OnDragListener} might delegate
     * {@link DragEvent#ACTION_DRAG_ENDED} events to this class so any work inside needs to be
     * idempotent.
     */
    void dragEnded();

    static DragAndDropManager create(Context context, DocumentClipper clipper) {
        return new RuntimeDragAndDropManager(context, clipper, Executors.newCachedThreadPool());
    }

    /**
     * Requests permissions for a drop event.
     *
     * @param activity the activity with which to request permissions.
     * @param event the drop event for which to request permissions.
     * @return permissions if granted successfully, otherwise {@code null}.
     */
    @Nullable
    static Permissions requestPermissions(Activity activity, DragEvent event) {
        final BiFunction<Activity, DragEvent, Permissions> requestPermissionsHandlerForTesting =
                REQUEST_PERMISSIONS_HANDLER_FOR_TESTING.get();

        if (requestPermissionsHandlerForTesting != null) {
            return requestPermissionsHandlerForTesting.apply(activity, event);
        }

        @Nullable DragAndDropPermissions permissions = null;

        if (isDragsFromOtherAppsEnabled()) {
            try {
                permissions = activity.requestDragAndDropPermissions(event);
            } catch (Exception e) {
                Log.e(TAG, "Unable to obtain permissions", e);
            }
        }

        return permissions != null ? new Permissions(permissions) : null;
    }

    /**
     * Returns the list of invalid destinations via their URIs to drop the documents.
     */
    List<Uri> getInvalidDestinations();

    class RuntimeDragAndDropManager implements DragAndDropManager {
        private static final String SRC_ROOT_KEY = "dragAndDropMgr:srcRoot";
        private static final String TAG = "DragAndDropManager";

        private final Context mContext;
        private final DocumentClipper mClipper;
        private final Executor mBgThreadExecutor;
        private final DragShadowBuilder mShadowBuilder;
        private final Drawable mDefaultShadowIcon;
        private final Executor mMainThreadExecutor;

        // NOTE: This is only utilized to mock out `MediaStore#getDocumentUri()` in unit tests.
        private final BiFunction<Context, Uri, Uri> mMediaStoreToDocumentUriRewriter;

        private @State int mState = STATE_UNKNOWN;
        private boolean mDragInitiated = false;

        // Key events info. This is used to derive state when user drags items into a view to derive
        // type of file operations.
        private boolean mIsCtrlPressed;

        // Drag events info. These are used to derive state and update drag shadow when user changes
        // Ctrl key state.
        private View mView;
        private List<Uri> mInvalidDest;
        private ClipData mClipData;
        private @Nullable RootInfo mDestRoot;
        private @Nullable DocumentInfo mDestDoc;

        // Boolean flag for current drag and drop operation. Returns true if the files can only
        // be copied (ie. files that don't support delete or remove).
        private boolean mMustBeCopied;
        private static final int DRAG_EVENT_COOKIE = 478919;

        // Track whether the set of files support trash operation or not.
        private boolean mIsFilesSupportTrash;

        // Tracks if the source of the drag operation is the trash root.
        private boolean mIsSrcRootTrash;

        // Tracks if the items can be dragged and dropped.
        private boolean mCanDragAndDrop = true;

        // The authority to restore to. Null if not a restore operation.
        private String mAuthorityToRestore;

        private RuntimeDragAndDropManager(
                Context context, DocumentClipper clipper, Executor bgThreadExecutor) {
            this(
                    context.getApplicationContext(),
                    clipper,
                    bgThreadExecutor,
                    new DragShadowBuilder(context),
                    IconUtils.loadMimeIcon(context, MimeTypes.GENERIC_TYPE),
                    MediaStore::getDocumentUri);
        }

        @VisibleForTesting
        RuntimeDragAndDropManager(
                Context context,
                DocumentClipper clipper,
                Executor bgThreadExecutor,
                DragShadowBuilder builder,
                Drawable defaultShadowIcon,
                BiFunction<Context, Uri, Uri> mediaStoreToDocumentUriRewriter) {
            mContext = context;
            mClipper = clipper;
            mBgThreadExecutor = bgThreadExecutor;
            mShadowBuilder = builder;
            mDefaultShadowIcon = defaultShadowIcon;
            mMainThreadExecutor = context.getMainExecutor();
            mMediaStoreToDocumentUriRewriter = mediaStoreToDocumentUriRewriter;
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT:
                    adjustCtrlKeyCount(event);
            }
        }

        private void adjustCtrlKeyCount(KeyEvent event) {
            assert(event.getKeyCode() == KeyEvent.KEYCODE_CTRL_LEFT
                    || event.getKeyCode() == KeyEvent.KEYCODE_CTRL_RIGHT);

            mIsCtrlPressed = event.isCtrlPressed();

            // There is an ongoing drag and drop if mView is not null.
            if (mView != null) {
                // There is no need to update the state if current state is unknown or not allowed.
                if (mState == STATE_COPY || mState == STATE_MOVE) {
                    updateState(mView, mDestRoot, mDestDoc);
                }
            }
        }

        @Override
        public void startDrag(
                View v,
                List<DocumentInfo> srcs,
                SidebarEntryItemInfo itemInfo,
                List<Uri> invalidDest,
                SelectionDetails selectionDetails,
                IconHelper iconHelper,
                @Nullable DocumentInfo parent,
                boolean canDragAndDrop) {

            Trace.beginAsyncSection("RuntimeDragAndDropManager.dragStartToDragEnd",
                    DRAG_EVENT_COOKIE);
            mDragInitiated = true;
            mView = v;
            mInvalidDest = invalidDest;
            mMustBeCopied = !selectionDetails.canDelete();
            mCanDragAndDrop = canDragAndDrop;
            if (isTrashFlowEnabled()) {
                mIsSrcRootTrash = itemInfo.getRoot().isTrash();
            }

            List<Uri> uris = new ArrayList<>(srcs.size());
            boolean isFilesSupportTrash = isTrashFlowEnabled();
            for (DocumentInfo doc : srcs) {
                isFilesSupportTrash &= doc.isTrashSupported();
                uris.add(doc.derivedUri);

                if (isTrashFlowEnabled() && mIsSrcRootTrash && doc.isRestoreSupported()) {
                    if (mAuthorityToRestore == null) {
                        mAuthorityToRestore = doc.authority;
                    } else if (!mAuthorityToRestore.equals(doc.authority)) {
                        // All documents must be from the same authority to be restored together.
                        mAuthorityToRestore = null;
                        break;
                    }
                }
            }
            mIsFilesSupportTrash = isFilesSupportTrash;
            mClipData = (parent == null)
                    ? mClipper.getClipDataForDocuments(uris, FileOperationService.OPERATION_UNKNOWN)
                    : mClipper.getClipDataForDocuments(
                            uris, FileOperationService.OPERATION_UNKNOWN, parent);
            mClipData.getDescription().getExtras()
                    .putString(SRC_ROOT_KEY, itemInfo.getRoot().getUri().toString());

            updateShadow(srcs, iconHelper);

            int flag = View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE;
            if (!selectionDetails.containsFilesInArchive()) {
                flag |= View.DRAG_FLAG_GLOBAL_URI_READ
                        | View.DRAG_FLAG_GLOBAL_URI_WRITE;
            }
            startDragAndDrop(
                    v,
                    mClipData,
                    mShadowBuilder,
                    this, // Used to detect multi-window drag and drop
                    flag);
        }

        private void updateShadow(List<DocumentInfo> srcs, IconHelper iconHelper) {
            final String title;
            final Drawable icon;

            final int size = srcs.size();
            // If use_material3 flag is ON, we always show the icon/title for the first file even
            // when we have multiple files.
            if (size == 1 || isUseMaterial3FlagEnabled()) {
                DocumentInfo doc = srcs.get(0);
                title = doc.displayName;
                icon = iconHelper.getDocumentIcon(mContext, doc);
            } else {
                title =
                        mContext.getResources()
                                .getQuantityString(getRes(R.plurals.elements_dragged), size, size);
                icon = mDefaultShadowIcon;
            }

            if (isUseMaterial3FlagEnabled()) {
                mShadowBuilder.updateDragFileCount(size);
            }

            mShadowBuilder.updateTitle(title);
            mShadowBuilder.updateIcon(icon);

            mShadowBuilder.onStateUpdated(STATE_UNKNOWN);
        }

        /**
         * A workaround of that
         * {@link View#startDragAndDrop(ClipData, View.DragShadowBuilder, Object, int)} is final.
         */
        @VisibleForTesting
        void startDragAndDrop(View v, ClipData clipData, DragShadowBuilder builder,
                Object localState, int flags) {
            v.startDragAndDrop(clipData, builder, localState, flags);
        }

        @Override
        public boolean canSpringOpen(RootInfo root, DocumentInfo doc) {
            return isValidDestination(root, doc.derivedUri, mInvalidDest);
        }

        @Override
        public void updateStateToNotAllowed(View v) {
            mView = v;
            updateState(STATE_NOT_ALLOWED);
        }

        @Override
        public @State int updateState(
                View v,
                @Nullable SidebarEntryItemInfo destItemInfo,
                @Nullable DocumentInfo destDoc) {
            // Due to drag and drop's async nature, this can unfortunately be null. Given at this
            // stage the destination is effectively unknown, it's not feasible to continue
            // calculating the state here.
            if (destItemInfo == null) {
                return STATE_UNKNOWN;
            }

            mView = v;
            mDestRoot = destItemInfo.getRoot();
            mDestDoc = destDoc;

            if (isCloudFeaturesFlagEnabled() && !mCanDragAndDrop) {
                updateState(STATE_NOT_ALLOWED);
                return STATE_NOT_ALLOWED;
            }

            final boolean isDragFromSameApp = isDragFromSameApp();
            if (isDragsFromOtherAppsEnabled() && mInvalidDest == null) {
                assert !isDragFromSameApp;
                mInvalidDest = new ArrayList<>();
            }

            if (!destItemInfo.isValidDropTarget()) {
                updateState(STATE_NOT_ALLOWED);
                return STATE_NOT_ALLOWED;
            }

            if (mDestRoot.isTrash()) {
                // If it's a trash root then check whether files are allowed to be trashed.
                if (destDoc != null
                        && !isValidDestination(destItemInfo, destDoc.derivedUri, mInvalidDest)) {
                    updateState(STATE_NOT_ALLOWED);
                    return STATE_NOT_ALLOWED;
                }
            } else {
                if (destDoc == null) {
                    updateState(STATE_UNKNOWN);
                    return STATE_UNKNOWN;
                }

                if (isTrashFlowEnabled()
                        && mIsSrcRootTrash
                        && !isValidDestination(destItemInfo, destDoc.derivedUri, mInvalidDest)) {
                    updateState(STATE_NOT_ALLOWED);
                    return STATE_NOT_ALLOWED;
                }

                assert (destDoc.isDirectory());

                if (!destDoc.isCreateSupported() || mInvalidDest.contains(destDoc.derivedUri)) {
                    updateState(STATE_NOT_ALLOWED);
                    return STATE_NOT_ALLOWED;
                }
            }

            @State int state = STATE_NOT_ALLOWED;
            final @OpType int opType =
                    DropOperation.calculateOpTypeSync(
                            mClipData,
                            mDestRoot,
                            mIsCtrlPressed,
                            isDragFromSameApp,
                            mIsSrcRootTrash,
                            mMustBeCopied);
            switch (opType) {
                case FileOperationService.OPERATION_COPY:
                    state = STATE_COPY;
                    break;
                case FileOperationService.OPERATION_MOVE:
                    state = STATE_MOVE;
                    break;
                case FileOperationService.OPERATION_TRASH:
                    if (isTrashFlowEnabled()) {
                        state = STATE_TRASH;
                    }
                    break;
                case FileOperationService.OPERATION_RESTORE:
                    if (isTrashFlowEnabled()) {
                        state = STATE_RESTORES_FROM_TRASH;
                    }
                    break;
                case FileOperationService.OPERATION_UNKNOWN:
                    if (isDragsFromOtherAppsEnabled()) {
                        assert !isDragFromSameApp;
                        state = STATE_UNKNOWN;
                        break;
                    }
                // fallthrough
                default:
                    throw new IllegalStateException("Unexpected opType: " + opType);
            }

            updateState(state);
            return state;
        }

        @Override
        public void resetState(View v) {
            mView = v;

            updateState(STATE_UNKNOWN);
        }

        @Override
        public boolean isDragFromSameApp() {
            return mDragInitiated;
        }

        private void updateState(@State int state) {
            mState = state;

            mShadowBuilder.onStateUpdated(state);
            updateDragShadow(mView);
        }

        /**
         * A workaround of that {@link View#updateDragShadow(View.DragShadowBuilder)} is final.
         */
        @VisibleForTesting
        void updateDragShadow(View v) {
            v.updateDragShadow(mShadowBuilder);
        }

        @Override
        public boolean drop(
                @Nullable Permissions permissions,
                ClipData clipData,
                Object localState,
                SidebarEntryItemInfo itemInfo,
                ActionHandler actions,
                FileOperations.Callback callback,
                List<Uri> invalidDest) {
            final Uri rootDocUri = DocumentsContract.buildDocumentUri(
                    itemInfo.getRoot().authority, itemInfo.getDocumentId());

            if ((isCloudFeaturesFlagEnabled() && !mCanDragAndDrop)
                    || !isValidDestination(itemInfo, rootDocUri, invalidDest)) {
                if (permissions != null) permissions.release();
                return false;
            }

            final CompletableFuture<DocumentStack> dstStack = new CompletableFuture<>();

            // NOTE: Create the drop operation helper early since mutable instance state could be
            // modified/reset while we're obtaining the root document in the background.
            final DropOperation dropOperation =
                    createDropOperation(permissions, clipData, dstStack);

            actions.getDocument(
                    itemInfo.getRoot().authority,
                    itemInfo.getDocumentId(),
                    itemInfo.getRoot().userId,
                    TimeoutTask.DEFAULT_TIMEOUT,
                    (DocumentInfo doc) -> {
                        dropOnRootDocument(
                                localState,
                                itemInfo.getRoot(),
                                doc,
                                actions,
                                callback,
                                dstStack,
                                dropOperation,
                                mMainThreadExecutor);
                    });

            return true;
        }

        // TODO(440196110): Inline this method.
        private static void dropOnRootDocument(
                Object localState,
                RootInfo destRoot,
                @Nullable DocumentInfo destRootDoc,
                ActionHandler actions,
                FileOperations.Callback callback,
                CompletableFuture<DocumentStack> destStack,
                DropOperation dropOperation,
                Executor mainThreadExecutor) {
            destStack.complete(
                    (destRootDoc == null)
                            ? new DocumentStack(destRoot)
                            : new DocumentStack(destRoot, destRootDoc));

            // Fail early: A drop onto a root is disallowed if the destination
            // is a non-trash root and its corresponding document is missing.
            if (destRootDoc == null && !destRoot.isTrash()) {
                final CompletableFuture<Void> unused =
                        dropOperation
                                .getOpType()
                                .thenAcceptAsync(
                                        opType ->
                                                callback.onOperationResult(
                                                        FileOperations.Callback.STATUS_FAILED,
                                                        opType,
                                                        0),
                                        mainThreadExecutor);
                return;
            }

            // For all valid cases, proceed.
            dropOperation.dropChecked(localState, actions, callback);
        }

        @Override
        public boolean drop(
                @Nullable Permissions permissions,
                ClipData clipData,
                Object localState,
                DocumentStack dstStack,
                ActionHandler actions,
                FileOperations.Callback callback) {

            if ((isCloudFeaturesFlagEnabled() && !mCanDragAndDrop)
                    || !isValidDocumentStack(dstStack)) {
                if (permissions != null) permissions.release();
                return false;
            }

            createDropOperation(permissions, clipData, dstStack)
                    .dropChecked(localState, actions, callback);

            return true;
        }

        @Override
        public void dragEnded() {
            // Multiple drag listeners might delegate drag ended event to this method, so anything
            // in this method needs to be idempotent. Otherwise we need to designate one listener
            // that always exists and only let it notify us when drag ended, which will further
            // complicate code and introduce one more coupling. This is a Android framework
            // limitation.

            mView = null;
            mInvalidDest = null;
            mClipData = null;
            mDestDoc = null;
            mDestRoot = null;
            mMustBeCopied = false;
            mDragInitiated = false;
            mIsFilesSupportTrash = false;
            mCanDragAndDrop = true;
            mIsSrcRootTrash = false;
            mAuthorityToRestore = null;
            Trace.endAsyncSection("RuntimeDragAndDropManager.dragStartToDragEnd",
                    DRAG_EVENT_COOKIE);
        }

        @Override
        public List<Uri> getInvalidDestinations() {
            return mInvalidDest;
        }

        private boolean isValidDocumentStack(DocumentStack dstStack) {
            final RootInfo root = dstStack.getRoot();
            final DocumentInfo dst = dstStack.peek();
            return isValidDestination(root, dst.derivedUri, mInvalidDest);
        }

        private boolean isValidDestination(
                SidebarEntryItemInfo sidebarEntryItemInfo, Uri dstUri, List<Uri> invalidDest) {
            // A destination is invalid if the drop is on a folder inside the trash root.
            // A non-null authority on the destination URI indicates a drop on a specific
            // document (a folder in this case), which is not allowed for the trash root.
            if (isTrashFlowEnabled()
                    && sidebarEntryItemInfo.getRoot().isTrash()
                    && dstUri.getAuthority() != null
                    && !mIsFilesSupportTrash) {
                return false;
            }

            // Restore case
            if (isTrashFlowEnabled() && mIsSrcRootTrash) {
                return isValidRestoreDestination(sidebarEntryItemInfo, dstUri);
            }

            // We pass in the invalid destinations since this check can also be called from an
            // asynchronous task. This method needs to maintain the same invalid destination
            // values as when the asynchronous task starts, but mInvalidDest can be mutated in the
            // meantime.
            return sidebarEntryItemInfo.isValidDropTarget() && !invalidDest.contains(dstUri);
        }

        /**
         * A restore destination is valid if the destination authority is the same as the source
         * authority. This is to prevent restoring files to a different authority. We only allow
         * restoring to the same authority from which the files were trashed.
         *
         * @param sidebarEntryItemInfo The destination sidebar entry item info.
         * @param dstUri The destination URI.
         * @return true if the destination is valid for restore.
         */
        private boolean isValidRestoreDestination(
                SidebarEntryItemInfo sidebarEntryItemInfo, Uri dstUri) {
            if (!isTrashFlowEnabled()) {
                return false;
            }

            final String destAuthority = dstUri.getAuthority();
            if (destAuthority == null || mAuthorityToRestore == null) {
                return false;
            }

            final boolean isSameAuthority =
                    sidebarEntryItemInfo.getRoot().authority.equals(destAuthority);
            return mAuthorityToRestore.equals(destAuthority) && isSameAuthority;
        }

        private DropOperation createDropOperation(
                @Nullable Permissions permissions, ClipData clipData, DocumentStack dstStack) {
            return createDropOperation(
                    permissions, clipData, CompletableFuture.completedFuture(dstStack));
        }

        private DropOperation createDropOperation(
                @Nullable Permissions permissions,
                ClipData clipData,
                CompletableFuture<DocumentStack> dstStack) {
            return new DropOperation(
                    permissions,
                    clipData,
                    mClipper,
                    mContext,
                    dstStack,
                    mBgThreadExecutor,
                    mIsCtrlPressed,
                    isDragFromSameApp(),
                    mIsSrcRootTrash,
                    mMainThreadExecutor,
                    mMediaStoreToDocumentUriRewriter,
                    mMustBeCopied);
        }

        /**
         * Helper class for handling system-level drop events asynchronously. Note that this class
         * must remain static so as not to rely on mutable {@link DragAndDropManager} instance state
         * which might otherwise be modified/reset during asynchronous execution.
         */
        private static final class DropOperation {

            private final CompletableFuture<ClipData> mClipData;
            private final DocumentClipper mClipper;
            private final Context mContext;
            private final CompletableFuture<DocumentStack> mDstStack;
            private final boolean mIsDragFromSameApp;
            private final boolean mIsSrcRootTrash;
            private final Executor mMainThreadExecutor;
            private final CompletableFuture<Integer> mOpType;

            private DropOperation(
                    @Nullable Permissions permissions,
                    ClipData clipData,
                    DocumentClipper clipper,
                    Context context,
                    CompletableFuture<DocumentStack> dstStack,
                    Executor bgThreadExecutor,
                    boolean isCtrlPressed,
                    boolean isDragFromSameApp,
                    boolean isSrcRootTrash,
                    Executor mainThreadExecutor,
                    BiFunction<Context, Uri, Uri> mediaStoreToDocumentUriRewriter,
                    boolean mustBeCopied) {
                mClipper = clipper;
                mContext = context;
                mDstStack = dstStack;
                mIsDragFromSameApp = isDragFromSameApp;
                mIsSrcRootTrash = isSrcRootTrash;
                mMainThreadExecutor = mainThreadExecutor;

                mClipData =
                        rewrite(
                                permissions,
                                clipData,
                                bgThreadExecutor,
                                mediaStoreToDocumentUriRewriter);

                mOpType = calculateOpTypeAsync(bgThreadExecutor, isCtrlPressed, mustBeCopied);

                if (permissions != null) {
                    final CompletableFuture<Void> unused =
                            mClipData.handleAsync(
                                    (result, throwable) -> {
                                        permissions.release();
                                        return null;
                                    },
                                    mMainThreadExecutor);
                }
            }

            private static @OpType int calculateOpTypeSync(
                    @Nullable ClipData clipData,
                    RootInfo destRoot,
                    boolean isCtrlPressed,
                    boolean isDragFromSameApp,
                    boolean isSrcRootTrash,
                    boolean mustBeCopied) {
                if (isDragsFromOtherAppsEnabled() && clipData == null) {
                    assert !isDragFromSameApp;
                    return FileOperationService.OPERATION_UNKNOWN;
                }

                // If the src root is Trash, then it will be the restore operation.
                if (isTrashFlowEnabled() && isSrcRootTrash) {
                    return FileOperationService.OPERATION_RESTORE;
                }

                // If the destination root is Trash, then it will be the trash operation.
                if (isTrashFlowEnabled() && destRoot.isTrash()) {
                    return FileOperationService.OPERATION_TRASH;
                }

                if (mustBeCopied) {
                    return FileOperationService.OPERATION_COPY;
                }

                final PersistableBundle bundle = clipData.getDescription().getExtras();
                if (!isDragsFromOtherAppsEnabled() || bundle.containsKey(SRC_ROOT_KEY)) {
                    final String srcRootUri = bundle.getString(SRC_ROOT_KEY);
                    final String destUri = destRoot.getUri().toString();

                    assert (srcRootUri != null);
                    assert (destUri != null);

                    if (srcRootUri.equals(destUri)) {
                        return isCtrlPressed
                                ? FileOperationService.OPERATION_COPY
                                : FileOperationService.OPERATION_MOVE;
                    } else {
                        return isCtrlPressed
                                ? FileOperationService.OPERATION_MOVE
                                : FileOperationService.OPERATION_COPY;
                    }
                }

                if (isDragsFromOtherAppsEnabled()) {
                    assert !isDragFromSameApp;
                }

                return FileOperationService.OPERATION_UNKNOWN;
            }

            private CompletableFuture<Integer> calculateOpTypeAsync(
                    Executor bgThreadExecutor, boolean isCtrlPressed, boolean mustBeCopied) {
                return CompletableFuture.allOf(mClipData, mDstStack)
                        .thenComposeAsync(
                                unused ->
                                        calculateOpTypeAsyncImpl(
                                                mClipData.join(),
                                                mDstStack.join(),
                                                bgThreadExecutor,
                                                isCtrlPressed,
                                                mustBeCopied),
                                mMainThreadExecutor);
            }

            private CompletableFuture<Integer> calculateOpTypeAsyncImpl(
                    @Nullable ClipData clipData,
                    DocumentStack dstStack,
                    Executor bgThreadExecutor,
                    boolean isCtrlPressed,
                    boolean mustBeCopied) {
                if (!isDragsFromOtherAppsEnabled()) {
                    return CompletableFuture.completedFuture(
                            calculateOpTypeSync(
                                    clipData,
                                    dstStack.getRoot(),
                                    isCtrlPressed,
                                    mIsDragFromSameApp,
                                    mIsSrcRootTrash,
                                    mustBeCopied));
                }
                return CompletableFuture.supplyAsync(
                        () -> {
                            final RootInfo dstRoot = dstStack.getRoot();

                            final @OpType int opType =
                                    calculateOpTypeSync(
                                            clipData,
                                            dstRoot,
                                            isCtrlPressed,
                                            mIsDragFromSameApp,
                                            mIsSrcRootTrash,
                                            mustBeCopied);

                            if (opType != FileOperationService.OPERATION_UNKNOWN
                                    || clipData == null) {
                                return opType;
                            }

                            assert !mIsDragFromSameApp;

                            final Uri dstRootUri = dstRoot.getUri();

                            boolean canCopyAll = true;
                            boolean canMoveAll = true;
                            Uri uri = null;

                            for (int i = 0; i < clipData.getItemCount(); ++i) {
                                uri = clipData.getItemAt(i).getUri();

                                if (uri == null) {
                                    canCopyAll = false;
                                    canMoveAll = false;
                                    break;
                                }

                                if (Providers.isSameProvider(dstRootUri, uri)) {
                                    // NOTE: Drag-and-drop within the same provider should never
                                    // result in a copy.
                                    canCopyAll = false;

                                    // TODO(b/440196110): Disallow dropping uri on immediate parent.
                                } else {
                                    // NOTE: Drag-and-drop across different providers should never
                                    // result in a move.
                                    canMoveAll = false;
                                }

                                if (!canCopyAll && !canMoveAll) {
                                    break;
                                }
                            }

                            return canMoveAll
                                    ? FileOperationService.OPERATION_MOVE
                                    : canCopyAll
                                            ? FileOperationService.OPERATION_COPY
                                            : FileOperationService.OPERATION_UNKNOWN;
                        },
                        bgThreadExecutor);
            }

            private void dropChecked(
                    Object localState, ActionHandler actions, FileOperations.Callback callback) {
                final CompletableFuture<Void> unused =
                        CompletableFuture.allOf(mClipData, mDstStack, mOpType)
                                .thenAcceptAsync(
                                        result ->
                                                dropCheckedImpl(
                                                        mClipData.join(),
                                                        mOpType.join(),
                                                        localState,
                                                        mDstStack.join(),
                                                        actions,
                                                        callback),
                                        mMainThreadExecutor);
            }

            private void dropCheckedImpl(
                    @Nullable ClipData clipData,
                    @OpType int opType,
                    Object localState,
                    DocumentStack dstStack,
                    ActionHandler actions,
                    FileOperations.Callback callback) {
                if (isDragsFromOtherAppsEnabled()
                        && opType == FileOperationService.OPERATION_UNKNOWN) {
                    assert !mIsDragFromSameApp;
                    callback.onOperationResult(FileOperations.Callback.STATUS_FAILED, opType, 0);
                    return;
                }

                // System-defined shortcuts should be protected against the file move operation.
                if (isHomeScreenFilesFlagEnabled()
                        && opType == FileOperationService.OPERATION_MOVE) {
                    List<Uri> uris = new ArrayList<>();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        uris.add(clipData.getItemAt(i).getUri());
                    }
                    if (actions.blockOperationForShortcuts(uris, dstStack.getRoot().userId)) {
                        Log.e(TAG, "Failed to move because a protected folder is selected.");
                        return;
                    }
                }

                // Recognize multi-window drag and drop based on the fact that localState is not
                // carried between processes. It will stop working when the localsState behavior is
                // changed. The info about window should be passed in the localState then. The
                // localState could also be null for copying from Recents in single window mode, but
                // Recents doesn't offer this functionality (no directories).
                Metrics.logUserAction(
                        localState == null
                                ? MetricConsts.USER_ACTION_DRAG_N_DROP_MULTI_WINDOW
                                : MetricConsts.USER_ACTION_DRAG_N_DROP);

                if (isTrashFlowEnabled() && mIsSrcRootTrash) {
                    mClipper.restoreFromTrashClipData(dstStack, clipData, callback);
                    return;
                }

                if (isTrashFlowEnabled() && dstStack.getRoot().isTrash()) {
                    mClipper.trashFromClipData(dstStack, clipData, callback);
                    return;
                }

                mClipper.copyFromClipData(dstStack, clipData, opType, callback);
            }

            private CompletableFuture<Integer> getOpType() {
                return mOpType;
            }

            // NOTE: DocumentsUI expects URIs to adhere to the Documents contract but some apps may
            // put non-Document URIs in clip data that we can still handle. The Launcher, for
            // instance, puts MediaStore URIs in clip data which we are able to rewrite to External
            // Storage Provider URIs. This method returns clip data in the form that DocumentsUI
            // expects, when possible, or `null` when rewriting is not possible.
            private CompletableFuture<ClipData> rewrite(
                    @Nullable Permissions permissions,
                    ClipData clipData,
                    Executor bgThreadExecutor,
                    BiFunction<Context, Uri, Uri> mediaStoreToDocumentUriRewriter) {
                if (!isDragsFromOtherAppsEnabled()) {
                    return CompletableFuture.completedFuture(clipData);
                }
                return CompletableFuture.supplyAsync(
                        () -> {
                            // NOTE: We only rewrite clip data from other apps.
                            if (mIsDragFromSameApp || clipData == null) {
                                return clipData;
                            }

                            // NOTE: If permissions were not obtained the drag source application
                            // could be a bad actor attempting to leverage DocUI's MANAGE_DOCUMENTS
                            // permission. Abort rewriting clip data to reject the drop operation.
                            if (permissions == null) {
                                return null;
                            }

                            try {
                                ClipData result = null;
                                ClipData.Item item = null;
                                for (int i = 0; i < clipData.getItemCount(); ++i) {
                                    item = clipData.getItemAt(i);
                                    item = rewrite(item, mediaStoreToDocumentUriRewriter);

                                    if (item == null) {
                                        Log.e(TAG, "Unable to rewrite item");
                                        return null;
                                    }

                                    if (result == null) {
                                        result = new ClipData(clipData.getDescription(), item);
                                    } else {
                                        result.addItem(item);
                                    }
                                }
                                return result;
                            } catch (Exception e) {
                                Log.e(TAG, "Unable to rewrite clip data", e);
                                return null;
                            }
                        },
                        bgThreadExecutor);
            }

            private @Nullable ClipData.Item rewrite(
                    @Nullable ClipData.Item item,
                    BiFunction<Context, Uri, Uri> mediaStoreToDocumentUriRewriter) {
                if (item == null) {
                    Log.e(TAG, "Unable to rewrite `NULL` item");
                    return null;
                }

                Uri uri = item.getUri();
                if (uri == null) {
                    Log.e(TAG, "Unable to rewrite `NULL` URI");
                    return null;
                }

                // NOTE: We can only safely rewrite `MediaStore` URIs since `MediaProvider` enables
                // the `forceUriPermissions` attribute in its manifest definition. Absent this
                // attribute, DocumentsUI would be unable to guarantee that the drag source
                // application actually holds permissions for the URIs we are attempting to rewrite.
                // TODO(b/454036239): Remove once the confused deputy vulnerability is mitigated.
                if (!Providers.isMediaStoreUri(uri)) {
                    Log.e(TAG, "Unable to rewrite non-MediaStore URI");
                    return null;
                }

                uri = mediaStoreToDocumentUriRewriter.apply(mContext, uri);
                if (uri == null) {
                    Log.e(TAG, "Unable to obtain document URI");
                    return null;
                }

                return new ClipData.Item(uri);
            }
        }
    }
}
