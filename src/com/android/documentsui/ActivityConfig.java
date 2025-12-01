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

package com.android.documentsui;

import static com.android.documentsui.util.FlagUtils.isCloudFeaturesFlagEnabled;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.State;

/**
 * Provides support for specializing the DirectoryFragment to the "host" Activity.
 * Feel free to expand the role of this class to handle other specializations.
 */
public abstract class ActivityConfig {

    // TODO(b/458129770): Delete this and use Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY instead
    // when it exists in the SDK.
    public static final int SYNC_STATE_FLAG_AVAILABLE_LOCALLY = 1 << 0;

    /**
     * Subtly different from isDocumentEnabled. The reason may be illuminated as follows. A folder
     * is enabled such that it may be double clicked, even in settings when the folder itself cannot
     * be selected. This may also be true of container types.
     */
    public boolean canSelectType(DocumentInfo doc, State state, boolean isOnline) {
        return true;
    }

    /** Returns whether a document is enabled. */
    public boolean isDocumentEnabled(DocumentInfo doc, State state, boolean isOnline) {
        if (!isCloudFeaturesFlagEnabled()) {
            return true;
        }
        if (doc.isDirectory()) {
            // Directories don't have content, so they are enabled.
            return true;
        }
        return isContentAvailable(doc, state, isOnline);
    }

    /**
     * Returns whether the document has content available. If the system is offline, the document is
     * non-virtual and on a root that has limited functionality, then the content might not be
     * available. Otherwise content is assumed available.
     *
     * <p>If the former is true for files, then return false when the sync state does not include
     * the `SYNC_STATE_FLAG_AVAILABLE_LOCALLY` flag. For folders, return false unconditionally
     * because they may contain files that do not have content available.
     */
    public boolean isContentAvailable(DocumentInfo doc, State state, boolean isOnline) {
        if (!isCloudFeaturesFlagEnabled()) {
            return true;
        }

        if (doc.isVirtual()) {
            return true;
        }

        if (!state.stack.getRoot().hasLimitedFunctionalityWhenOffline()) {
            // Documents on roots that are not affected by the online status are always available.
            return true;
        }

        if (isOnline) {
            // The content should be downloadable and thus available.
            return true;
        }

        if (doc.isDirectory()) {
            // Directories may contain files that don't have available content.
            return false;
        }

        if (doc.syncStateFlags == null) {
            // When the availability of a file is unknown, we default to available and thus return
            // true.
            return true;
        }

        // TODO(b/458129770): Update to using Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY when it
        // exists in the SDK.
        if ((doc.syncStateFlags & SYNC_STATE_FLAG_AVAILABLE_LOCALLY) != 0) {
            // The file's content is available locally, so it is enabled.
            return true;
        }

        // The file is definitely unavailable locally, so it should be disabled.
        return false;
    }

    /**
     * When managed mode is enabled, there will be special UI behaviors:
     * 1) active downloads will be visible in the UI.
     * 2) Android/[data|obb|sandbox] directories will not be hidden.
     */
    public boolean managedModeEnabled(DocumentStack stack) {
        return false;
    }

    /**
     * Whether drag n' drop is allowed in this context
     */
    public boolean dragAndDropEnabled() {
        return false;
    }
}
