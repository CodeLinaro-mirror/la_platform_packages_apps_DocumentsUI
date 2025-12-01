/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.files;

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isGetInfoDialogEnabled;
import static com.android.documentsui.util.FlagUtils.isHomeScreenFilesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isSearchV2Enabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseApprovedDocumentHandlerEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUsePeekPreviewFlagEnabled;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.FileUtils;
import android.os.Trace;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.DragEvent;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.ActionModeAddons;
import com.android.documentsui.ActivityConfig;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.Injector;
import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.TimeoutTask;
import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.DocumentFilters;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.ShortcutInfo;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.ClipStore;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.files.getinfo.GetInfoDialogFragment;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.peek.PeekViewManager;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Provides {@link FilesActivity} action specializations to fragments.
 * @param <T> activity which extends {@link FragmentActivity} and implements
 *              {@link AbstractActionHandler.CommonAddons}.
 */
public class ActionHandler<T extends FragmentActivity & AbstractActionHandler.CommonAddons>
        extends AbstractActionHandler<T> {

    private static final String TAG = "ManagerActionHandler";
    private static final int SHARE_FILES_COUNT_LIMIT = 100;

    private final ActionModeAddons mActionModeAddons;
    private final Features mFeatures;
    private final ActivityConfig mConfig;
    private final DocumentClipper mClipper;
    private final ClipStore mClipStore;
    private final DragAndDropManager mDragAndDropManager;
    private final Runnable mCloseSelectionBar;
    private final @Nullable PeekViewManager mPeekViewManager;

    ActionHandler(
            T activity,
            State state,
            ProvidersAccess providers,
            DocumentsAccess docs,
            SearchViewManager searchMgr,
            Lookup<String, Executor> executors,
            @Nullable ActionModeAddons actionModeAddons,
            Runnable closeSelectionBar,
            DocumentClipper clipper,
            ClipStore clipStore,
            DragAndDropManager dragAndDropManager,
            @Nullable PeekViewManager peekViewManager,
            Injector injector) {

        super(activity, state, providers, docs, searchMgr, executors, injector);

        mActionModeAddons = actionModeAddons;
        mCloseSelectionBar = closeSelectionBar;
        mFeatures = injector.features;
        mConfig = injector.config;
        mClipper = clipper;
        mClipStore = clipStore;
        mDragAndDropManager = dragAndDropManager;
        mPeekViewManager = peekViewManager;
    }

    @Override
    public boolean dropOn(DragEvent event, RootInfo root) {
        if (!root.isValidDropTarget()) {
            return false;
        }

        // Except trash root, other library roots do not support drag & drop operations.
        if (root.isLibrary() && !root.isTrash()) {
            return false;
        }

        // DragEvent gets recycled, so it is possible that by the time the callback is called,
        // event.getLocalState() and event.getClipData() returns null. Thus, we want to save
        // references to ensure they are non null.
        final ClipData clipData = event.getClipData();
        final Object localState = event.getLocalState();

        return mDragAndDropManager.drop(
                DragAndDropManager.requestPermissions(mActivity, event),
                clipData,
                localState,
                root,
                this,
                mDialogs::showFileOperationStatus,
                mDragAndDropManager.getInvalidDestinations());
    }

    @Override
    public boolean dropOn(DragEvent event, ShortcutInfo shortcut) {
        if (!shortcut.isValidDropTarget()) {
            return false;
        }

        final ClipData clipData = event.getClipData();
        final Object localState = event.getLocalState();

        return mDragAndDropManager.drop(
                DragAndDropManager.requestPermissions(mActivity, event),
                clipData,
                localState,
                shortcut,
                this,
                mDialogs::showFileOperationStatus,
                mDragAndDropManager.getInvalidDestinations());
    }

    @Override
    public void openSelectedInNewWindow() {
        Selection<String> selection = getStableSelection();
        if (selection.isEmpty()) {
            return;
        }

        assert(selection.size() == 1);
        DocumentInfo doc = mModel.getDocument(selection.iterator().next());
        assert(doc != null);
        openInNewWindow(new DocumentStack(mState.stack, doc), mState.shortcut);
    }

    @Override
    public void openSettings(RootInfo root) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_SETTINGS);
        final Intent intent = new Intent(DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS);
        intent.setDataAndType(root.getUri(), DocumentsContract.Root.MIME_TYPE_ITEM);
        root.userId.startActivityAsUser(mActivity, intent);
    }

    @Override
    public void pasteIntoFolder(SidebarEntryItemInfo itemInfo) {
        this.getDocument(
                itemInfo.getRoot().authority,
                itemInfo.getDocumentId(),
                itemInfo.getRoot().userId,
                TimeoutTask.DEFAULT_TIMEOUT,
                (DocumentInfo doc) -> pasteIntoFolder(itemInfo.getRoot(), doc));
    }

    private void pasteIntoFolder(RootInfo root, @Nullable DocumentInfo doc) {
        DocumentStack stack = new DocumentStack(root, doc);
        mClipper.copyFromClipboard(doc, stack, mDialogs::showFileOperationStatus);
    }

    @Override
    public @Nullable DocumentInfo renameDocument(String name, DocumentInfo document) {
        if (isHomeScreenFilesFlagEnabled()
                && blockOperationForShortcuts(List.of(document.derivedUri), document.userId)) {
            // This should have been blocked earlier before the popup appears, but leave here
            // just in case.
            Log.e(TAG, "Failed to rename because a protected folder is selected.");
            return null;
        }

        ContentResolver resolver = document.userId.getContentResolver(mActivity);
        ContentProviderClient client = null;

        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(
                    resolver, document.derivedUri.getAuthority());
            Uri newUri = DocumentsContract.renameDocument(
                    wrap(client), document.derivedUri, name);
            return DocumentInfo.fromUri(resolver, newUri, document.userId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to rename file", e);
            return null;
        } finally {
            FileUtils.closeQuietly(client);
        }
    }

    @Override
    public void openRoot(RootInfo root) {
        Metrics.logRootVisited(MetricConsts.FILES_SCOPE, root);
        mActivity.onRootPicked(root);
    }

    @Override
    public void openShortcut(ShortcutInfo shortcut) {
        mActivity.onShortcutPicked(shortcut);
    }

    @Override
    public boolean openItem(ItemDetails<String> details, @ViewType int type,
            @ViewType int fallback) {
        Trace.beginSection("documentsui.files.ActionHandler#openItem");
        DocumentInfo doc = mModel.getDocument(details.getSelectionKey());
        if (doc == null) {
            Log.w(TAG, "Can't view item. No Document available for modeId: "
                    + details.getSelectionKey());
            Trace.endSection();
            return false;
        }
        mInjector.searchManager.recordHistory();

        boolean result = openDocument(doc, type, fallback);
        Trace.endSection();
        return result;
    }

    // TODO: Make this private and make tests call openDocument(DocumentDetails, int, int) instead.
    @VisibleForTesting
    public boolean openDocument(DocumentInfo doc, @ViewType int type, @ViewType int fallback) {
        // Opening an item in the trash root is not allowed.
        if (mState.stack.isTrashRoot() && !doc.isDirectory()) {
            showFileOpenFromTrashDialog(doc);
            return false;
        }
        if (mConfig.isDocumentEnabled(doc, mState, mInjector.networkMonitor.isOnline())) {
            onDocumentOpened(doc, type, fallback, false);
            mSelectionMgr.clearSelection();
            return !doc.isContainer();
        }
        return false;
    }

    @Override
    public void openDocumentViewOnly(DocumentInfo doc) {
        mInjector.searchManager.recordHistory();
        openDocument(doc, VIEW_TYPE_REGULAR, VIEW_TYPE_NONE);
    }

    @Override
    public void springOpenDirectory(DocumentInfo doc) {
        assert (doc.isDirectory());
        if (isUseMaterial3FlagEnabled()) {
            mCloseSelectionBar.run();
        } else {
            mActionModeAddons.finishActionMode();
        }
        openContainerDocument(doc);
    }

    private Selection<String> getSelectedOrFocused() {
        final MutableSelection<String> selection = this.getStableSelection();
        if (selection.isEmpty()) {
            String focusModelId = mFocusHandler.getFocusModelId();
            if (focusModelId != null) {
                selection.add(focusModelId);
            }
        }

        return selection;
    }

    @Override
    public void cutToClipboard() {
        Metrics.logUserAction(MetricConsts.USER_ACTION_CUT_CLIPBOARD);
        Selection<String> selection = getSelectedOrFocused();

        if (selection.isEmpty()) {
            return;
        }

        if (mModel.hasDocuments(selection, DocumentFilters.NOT_MOVABLE)) {
            mDialogs.showOperationUnsupported();
            return;
        }

        if (isHomeScreenFilesFlagEnabled()) {
            List<DocumentInfo> docs = mModel.getDocuments(selection);
            if (docs == null || docs.isEmpty()) {
                mDialogs.showOperationUnsupported();
                return;
            }

            List<Uri> uris = new ArrayList<>();
            for (DocumentInfo doc : docs) {
                uris.add(doc.derivedUri);
            }

            if (blockOperationForShortcuts(uris, mActivity.getSelectedUser())) {
                Log.e(TAG, "Failed to cut because a protected folder is selected.");
                return;
            }
        }

        mSelectionMgr.clearSelection();

        mClipper.clipDocumentsForCut(
                mModel::getItemUri, selection, mState.stack.peek(), mState.stack.isRecents());

        mDialogs.showDocumentsClipped(selection.size());
    }

    @Override
    public void copyToClipboard() {
        Metrics.logUserAction(MetricConsts.USER_ACTION_COPY_CLIPBOARD);
        Selection<String> selection = getSelectedOrFocused();

        if (selection.isEmpty()) {
            return;
        }
        mSelectionMgr.clearSelection();

        mClipper.clipDocumentsForCopy(mModel::getItemUri, selection);

        mDialogs.showDocumentsClipped(selection.size());
    }

    /** Base method for creating a share intent. */
    private @Nullable Intent createShareIntentBase(Selection<String> selection) {
        // Model must be accessed in UI thread, since underlying cursor is not thread safe.
        List<DocumentInfo> docs =
                mModel.loadDocuments(selection, DocumentFilters.sharable(mFeatures));

        if (docs.size() < 1) {
            return null;
        }

        Intent intent;
        if (docs.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            DocumentInfo doc = docs.get(0);
            intent.setDataAndType(doc.getDocumentUri(), doc.mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, doc.getDocumentUri());

        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);

            final ArrayList<String> mimeTypes = new ArrayList<>();
            final ArrayList<Uri> uris = new ArrayList<>();
            for (DocumentInfo doc : docs) {
                mimeTypes.add(doc.mimeType);
                uris.add(doc.getDocumentUri());
            }

            intent.setType(MimeTypes.findCommonMimeType(mimeTypes));
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (mFeatures.isVirtualFilesSharingEnabled()
                && mModel.hasDocuments(selection, DocumentFilters.VIRTUAL)) {
            intent.addCategory(Intent.CATEGORY_TYPED_OPENABLE);
        }

        return intent;
    }

    /** Creates the intent for the Share menu. */
    private @Nullable Intent createShareIntent(Selection<String> selection) {
        Intent intent = createShareIntentBase(selection);
        if (intent == null) {
            return null;
        }
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        return intent;
    }

    /** Creates the intent for the Approved Doc Handler. */
    @VisibleForTesting
    public @Nullable Intent createApprovedHandlerIntent(Selection<String> selection) {
        Intent intent = createShareIntentBase(selection);
        if (intent == null) {
            return null;
        }
        // TODO: b/464388012 - Reference actual intent category when it's available.
        intent.addCategory("android.provider.category.APPROVED_DOCUMENT_HANDLER");

        return intent;
    }

    @Override
    public boolean sendToApprovedDocHandler(ComponentName app) {
        if (!isUseApprovedDocumentHandlerEnabled()) {
            return false;
        }
        Selection<String> selection = getSelectedOrFocused();
        final Intent intent = createApprovedHandlerIntent(selection);

        if (intent == null) {
            if (DEBUG) {
                Log.d(TAG, "Cannot send to approved document handler, intent is null");
            }
            return false;
        }

        intent.setComponent(app);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (isDesktopFileHandlingFlagEnabled()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        mActivity.startActivity(intent);
        return true;
    }

    @Override
    public void viewInOwner() {
        Metrics.logUserAction(MetricConsts.USER_ACTION_VIEW_IN_APPLICATION);
        Selection<String> selection = getSelectedOrFocused();

        if (selection.isEmpty() || selection.size() > 1) {
            return;
        }
        DocumentInfo doc = mModel.getDocument(selection.iterator().next());
        Intent intent = new Intent(DocumentsContract.ACTION_DOCUMENT_SETTINGS);
        intent.setPackage(mProviders.getPackageName(UserId.DEFAULT_USER, doc.authority));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(doc.derivedUri);
        try {
            doc.userId.startActivityAsUser(mActivity, intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to view settings in application for " + doc.derivedUri, e);
            mDialogs.showNoApplicationFoundToast();
        }
    }

    @Override
    public void showDeleteDialog() {
        Selection selection = getSelectedOrFocused();
        if (selection.isEmpty()) {
            return;
        }

        // The DocumentInfo of the parent of the document(s) to be deleted is used to send the URI
        // of that parent to FileOperationService for the DeleteJob. If specified, DeleteJob will
        // try to remove the document from the parent rather than deleting the document, this
        // distinction is important if the DocumentProvider supports the document appearing under
        // multiple parents.
        //
        // When viewing the "Recent" root, it is considered the parent, however it's a synthetic
        // root and not the actual parent of the documents. Its URI, when passed to
        // FileOperationService, is meaningless and causes DeleteJob to unnecessarily fail for
        // documents in Recents.
        //
        // If the user is in the "Recent" view, pass a null DocumentInfo as parent, causing a null
        // parentUri to be specified for DeleteJob.
        DocumentInfo parentDocumentInfo = mState.stack.peek();
        if (isSearchV2Enabled() && mState.stack.isRecents()) {
            parentDocumentInfo = null;
        }

        // The document in trash folder can not be removed from the parent, since it will be
        // permanently deleted. Pass a null parent so that DeleteJob can do a permanent delete.
        if (isTrashFlowEnabled() && mState.stack.isTrashTopLevel()) {
            parentDocumentInfo = null;
        }

        DeleteDocumentFragment.show(mActivity.getSupportFragmentManager(),
                mModel.getDocuments(selection),
                parentDocumentInfo);
    }


    @Override
    public void deleteSelectedDocuments(List<DocumentInfo> docs, @Nullable DocumentInfo srcParent) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        if (isUseMaterial3FlagEnabled()) {
            mCloseSelectionBar.run();
        } else {
            mActionModeAddons.finishActionMode();
        }

        List<Uri> uris = new ArrayList<>(docs.size());
        for (DocumentInfo doc : docs) {
            uris.add(doc.derivedUri);
        }

        if (isHomeScreenFilesFlagEnabled()
                && blockOperationForShortcuts(uris, mActivity.getSelectedUser())) {
            Log.e(TAG, "Failed to delete because a protected folder is selected.");
            return;
        }

        UrisSupplier srcs;
        try {
            srcs = UrisSupplier.create(
                    uris,
                    mClipStore);
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete a file because we were unable to get item URIs.", e);
            mDialogs.showFileOperationStatus(
                    FileOperations.Callback.STATUS_FAILED,
                    FileOperationService.OPERATION_DELETE,
                    uris.size());
            return;
        }

        // srcParent can be null, such as when the user is viewing the "Recent" root.
        FileOperation operation = new FileOperation.Builder()
                .withOpType(FileOperationService.OPERATION_DELETE)
                .withDestination(mState.stack)
                .withSrcs(srcs)
                .withSrcParent(srcParent == null ? null : srcParent.derivedUri)
                .build();

        FileOperations.start(mActivity, operation, mDialogs::showFileOperationStatus,
                FileOperations.createJobId());
    }

    @Override
    public void trashSelectedDocuments() {
        Selection selection = getSelectedOrFocused();
        if (selection.isEmpty()) {
            return;
        }

        List<DocumentInfo> docs = mModel.getDocuments(selection);
        if (docs == null || docs.isEmpty()) {
            return;
        }

        if (isUseMaterial3FlagEnabled()) {
            mCloseSelectionBar.run();
        } else {
            mActionModeAddons.finishActionMode();
        }

        List<Uri> uris = new ArrayList<>(docs.size());
        for (DocumentInfo doc : docs) {
            uris.add(doc.derivedUri);
        }

        if (isHomeScreenFilesFlagEnabled()
                && blockOperationForShortcuts(uris, mActivity.getSelectedUser())) {
            Log.e(TAG, "Failed to trash because a protected folder is selected.");
            return;
        }

        UrisSupplier srcs;
        try {
            srcs = UrisSupplier.create(
                    uris,
                    mClipStore);
        } catch (Exception e) {
            Log.e(TAG, "Failed to trash because we were unable to get item URIs.", e);
            mDialogs.showFileOperationStatus(
                    FileOperations.Callback.STATUS_FAILED,
                    FileOperationService.OPERATION_TRASH,
                    uris.size());
            return;
        }

        FileOperation operation = new FileOperation.Builder()
                .withOpType(FileOperationService.OPERATION_TRASH)
                .withDestination(mState.stack)
                .withSrcs(srcs)
                .build();

        FileOperations.start(mActivity, operation, mDialogs::showFileOperationStatus,
                FileOperations.createJobId());
    }

    @Override
    public void restoreSelectedDocumentsFromTrash(List<DocumentInfo> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }

        if (isUseMaterial3FlagEnabled()) {
            mCloseSelectionBar.run();
        } else {
            mActionModeAddons.finishActionMode();
        }

        List<Uri> uris = new ArrayList<>(docs.size());
        for (DocumentInfo doc : docs) {
            uris.add(doc.derivedUri);
        }

        UrisSupplier srcs;
        try {
            srcs = UrisSupplier.create(
                    uris,
                    mClipStore);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore a file because we were unable to get item URIs.", e);
            mDialogs.showFileOperationStatus(
                    FileOperations.Callback.STATUS_FAILED,
                    FileOperationService.OPERATION_RESTORE,
                    uris.size());
            return;
        }

        FileOperation operation = new FileOperation.Builder()
                .withOpType(FileOperationService.OPERATION_RESTORE)
                .withDestination(mState.stack)
                .withSrcs(srcs)
                .build();

        FileOperations.start(mActivity, operation, mDialogs::showFileOperationStatus,
                FileOperations.createJobId());
    }

    @Override
    public void shareSelectedDocuments() {
        Metrics.logUserAction(MetricConsts.USER_ACTION_SHARE);

        Selection<String> selection = getStableSelection();
        if (selection.isEmpty()) {
            return;
        } else if (selection.size() > SHARE_FILES_COUNT_LIMIT) {
            mDialogs.showShareOverLimit(SHARE_FILES_COUNT_LIMIT);
            return;
        }

        Intent intent = createShareIntent(selection);

        if (intent == null) {
            if (DEBUG) {
                Log.d(TAG, "Cannot share files, intent is null");
            }
            return;
        }

        Intent chooserIntent =
                Intent.createChooser(intent, mActivity.getResources().getText(R.string.share_via));

        mActivity.startActivity(chooserIntent);
    }

    @Override
    public void loadDocumentsForCurrentStack() {
        super.loadDocumentsForCurrentStack();
    }

    @Override
    public void initLocation(Intent intent) {
        assert(intent != null);

        // stack is initialized if it's restored from bundle, which means we're restoring a
        // previously stored state.
        if (mState.stack.isInitialized()) {
            if (DEBUG) {
                Log.d(TAG, "Stack already resolved for uri: " + intent.getData());
            }
            restoreRootAndDirectory();
            return;
        }

        if (launchToStackLocation(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launched to location from stack.");
            }
            return;
        }

        if (launchToRoot(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launched to root for browsing.");
            }
            return;
        }

        if (launchToDocument(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launched to a document.");
            }
            return;
        }

        if (launchToDownloads(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launched to a downloads.");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Launching directly into Home directory.");
        }
        launchToDefaultLocation();
    }

    @Override
    protected void launchToDefaultLocation() {
        loadHomeDir();
    }

    @Override
    public void showEmptyTrashConfirmationDialog() {
        if (!mState.stack.isTrashTopLevel()) {
            return;
        }

        // If there are no trash documents, don't show the dialog.
        if (mModel.getModelIds().length == 0) {
            return;
        }

        EmptyTrashDialogFragment.show(mActivity.getSupportFragmentManager());
    }

    @Override
    public void permanentlyDeleteTrashDocuments() {
        // If this is not the trash page then ignore.
        if (!mState.stack.isTrashTopLevel()) {
            return;
        }

        // Select all documents in the trash and then perform the permanent delete operation.
        selectAllFiles();
        Selection<String> selection = getSelectedOrFocused();
        if (selection.isEmpty()) {
            return;
        }

        List<DocumentInfo> docs = mModel.getDocuments(selection);
        if (docs == null || docs.isEmpty()) {
            return;
        }

        deleteSelectedDocuments(docs, /* srcParent */ null);
    }

    // If EXTRA_STACK is not null in intent, we'll skip other means of loading
    // or restoring the stack (like URI).
    //
    // When restoring from a stack, if a URI is present, it should only ever be:
    // -- a launch URI: Launch URIs support sensible activity management,
    //    but don't specify a real content target)
    // -- a fake Uri from notifications. These URIs have no authority (TODO: details).
    //
    // Any other URI is *sorta* unexpected...except when browsing an archive
    // in downloads.
    private boolean launchToStackLocation(Intent intent) {
        DocumentStack stack = intent.getParcelableExtra(Shared.EXTRA_STACK);
        if (stack == null || stack.getRoot() == null) {
            return false;
        }

        if (isHomeScreenFilesFlagEnabled()) {
            ShortcutInfo shortcut = intent.getParcelableExtra(Shared.EXTRA_SELECTED_SHORTCUT);
            if (stack.isEmpty() && shortcut == null) {
                mActivity.onRootPicked(stack.getRoot());
            } else if (stack.isEmpty()) {
                mActivity.onShortcutPicked(shortcut);
            } else {
                mState.stack.reset(stack);
                mState.shortcut = shortcut;
                mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
            }
        } else {
            if (stack.isEmpty()) {
                mActivity.onRootPicked(stack.getRoot());
            } else {
                mState.stack.reset(stack);
                mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
            }
        }

        return true;
    }

    private boolean launchToRoot(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (DocumentsContract.isRootUri(mActivity, uri)) {
                if (DEBUG) {
                    Log.d(TAG, "Launching with root URI.");
                }
                // If we've got a specific root to display, restore that root using a dedicated
                // authority. That way a misbehaving provider won't result in an ANR.
                loadRoot(uri, UserId.DEFAULT_USER);
                return true;
            } else if (DocumentsContract.isRootsUri(mActivity, uri)) {
                if (DEBUG) {
                    Log.d(TAG, "Launching first root with roots URI.");
                }
                // TODO: b/116760996 Let the user can disambiguate between roots if there are
                // multiple from DocumentsProvider instead of launching the first root in default
                loadFirstRoot(uri);
                return true;
            }
        }
        return false;
    }

    private boolean launchToDocument(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (DocumentsContract.isDocumentUri(mActivity, uri)) {
                return launchToDocument(intent.getData());
            }
        }

        return false;
    }

    private boolean launchToDownloads(Intent intent) {
        if (DownloadManager.ACTION_VIEW_DOWNLOADS.equals(intent.getAction())) {
            Uri uri = DocumentsContract.buildRootUri(Providers.AUTHORITY_DOWNLOADS,
                    Providers.ROOT_ID_DOWNLOADS);
            loadRoot(uri, UserId.DEFAULT_USER);
            return true;
        }

        return false;
    }

    /**
     * Trashes the selected documents if the trash feature is enabled and all documents support it.
     * Otherwise, it initiates the delete flow for the selected documents.
     */
    public void runDeleteOrTrashHandler() {
        Selection<String> selection = getSelectedOrFocused();
        if (selection.isEmpty()) {
            return;
        }

        if (isTrashFlowEnabled()
                && !mModel.hasDocuments(selection, DocumentFilters.NOT_SUPPORT_TRASH)) {
            trashSelectedDocuments();
        } else {
            showDeleteDialog();
        }
    }

    @Override
    public void showChooserForDoc(DocumentInfo doc) {
        assert(!doc.isDirectory());

        if (manageDocument(doc)) {
            Log.w(TAG, "Open with is not yet supported for managed doc.");
            return;
        }

        if (isDesktopFileHandlingFlagEnabled()) {
            Intent intent = buildViewIntent(doc);
            if (intent.resolveActivity(mActivity.getPackageManager()) == null) {
                mDialogs.showNoApplicationFoundDialog(mActivity.getSupportFragmentManager(), doc);
                return;
            }
            intent.setComponent(
                    new ComponentName("android", "com.android.internal.app.ResolverActivity"));

            try {
                doc.userId.startActivityAsUser(mActivity, intent);
            } catch (ActivityNotFoundException e) {
                mDialogs.showNoApplicationFoundDialog(
                        mActivity.getSupportFragmentManager(), doc);
            }
        } else {
            Intent intent = Intent.createChooser(buildViewIntent(doc), null);
            intent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);

            try {
                doc.userId.startActivityAsUser(mActivity, intent);
            } catch (ActivityNotFoundException e) {
                mDialogs.showNoApplicationFoundToast();
            }
        }

    }

    /** Shows a dialog with the metadata of the selected document. */
    private void showGetInfoDialog(DocumentInfo doc) {
        GetInfoDialogFragment.show(mActivity.getSupportFragmentManager(), doc);
    }

    private void showInspector(DocumentInfo doc) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_INSPECTOR);
        Intent intent = InspectorActivity.createIntent(mActivity, doc.derivedUri, doc.userId);

        // permit the display of debug info about the file.
        intent.putExtra(
                Shared.EXTRA_SHOW_DEBUG,
                mFeatures.isDebugSupportEnabled() &&
                        (DEBUG || DebugFlags.getDocumentDetailsEnabled()));

        // The "root document" (top level folder in a root) don't usually have a
        // human friendly display name. That's because we've never shown the root
        // folder's name to anyone.
        // For that reason when the doc being inspected is the root folder,
        // we override the displayName of the doc w/ the Root's name instead.
        // The Root's name is shown to the user in the sidebar.
        if (doc.isDirectory() && mState.stack.size() == 1 && mState.stack.get(0).equals(doc)) {
            RootInfo root = mActivity.getCurrentRoot();
            // Recents root title isn't defined, but inspector is disabled for recents root folder.
            assert !TextUtils.isEmpty(root.title);
            intent.putExtra(Intent.EXTRA_TITLE, root.title);
        }
        mActivity.startActivity(intent);
    }

    private void showPeek(DocumentInfo doc) {
        if (mPeekViewManager == null) {
            Log.e(TAG, "Attempting to show Peek when PeekViewManager is not defined");
            return;
        }
        mPeekViewManager.peekDocument(doc);
    }

    private void showFileOpenFromTrashDialog(DocumentInfo doc) {
        if (!mState.stack.isTrashRoot()) {
            return;
        }

        // Directory is allowed to open.
        if (doc.isDirectory()) {
            return;
        }

        List<DocumentInfo> documentInfos = new ArrayList<>();
        documentInfos.add(doc);
        FileOpenFromTrashDialogFragment.show(mActivity.getSupportFragmentManager(), documentInfos);
    }

    @Override
    public void showPreview(DocumentInfo doc) {
        if (isUsePeekPreviewFlagEnabled()) {
            showPeek(doc);
        } else if (isGetInfoDialogEnabled()) {
            showGetInfoDialog(doc);
        } else {
            showInspector(doc);
        }
    }
}
