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

package com.android.documentsui.services;

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.redact;
import static com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Notification;
import android.app.Notification.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.R;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.UrisSupplier;

/**
 * CompressJob creates a new ZIP archive and zips the selected items into this archive.
 * It performs most of its work by delegating it to its base CopyJob class.
 */
final class CompressJob extends CopyJob {

    private static final String TAG = "CompressJob";
    private static final String NEW_ARCHIVE_EXTENSION = ".zip";

    private Uri mArchiveUri;

    /**
     * Zips items to a new ZIP archive which will be created in the folder identified by
     * {@code destination}.
     */
    CompressJob(Context service, Listener listener, String id, DocumentStack destination,
            UrisSupplier srcs, Messenger messenger, Features features) {
        super(service, listener, id, OPERATION_COMPRESS, destination, srcs, messenger, features);
    }

    @Override
    Builder createProgressBuilder() {
        return super.createProgressBuilder(
                service.getString(getRes(R.string.compress_notification_title)),
                getRes(R.drawable.ic_menu_compress),
                service.getString(android.R.string.cancel),
                getRes(R.drawable.ic_cab_cancel));
    }

    @Override
    public Notification getSetupNotification() {
        return getSetupNotification(service.getString(getRes(R.string.compress_preparing)));
    }

    @Override
    public Notification getProgressNotification() {
        return getProgressNotification(getRes(R.string.copy_remaining));
    }

    @Override
    public Notification getFailureNotification() {
        return getFailureNotification(
                getFailureContentTitle(getRes(R.string.compress_error_notification_title)),
                getRes(R.drawable.ic_menu_compress));
    }

    @Override
    protected String getProgressMessage() {
        return getProgressMessage(R.string.compress_in_progress);
    }

    @Override
    public boolean setUp() {
        if (!super.setUp()) {
            return false;
        }

        String displayName;
        if (mResolvedDocs.size() == 1) {
            displayName = mResolvedDocs.get(0).displayName + NEW_ARCHIVE_EXTENSION;
        } else {
            displayName =
                    service.getString(
                            getRes(R.string.new_archive_file_name), NEW_ARCHIVE_EXTENSION);
        }

        try {
            final ContentResolver resolver = appContext.getContentResolver();
            mArchiveUri = DocumentsContract.createDocument(resolver, mDstInfo.derivedUri,
                    "application/zip", displayName);
            if (DEBUG) Log.d(TAG, "Created archive " + redact(mArchiveUri));

            mDstInfo = DocumentInfo.fromUri(resolver,
                    ArchivesProvider.buildUriForArchive(mArchiveUri,
                            ParcelFileDescriptor.MODE_WRITE_ONLY), UserId.DEFAULT_USER);
            ArchivesProvider.acquireArchive(getClient(mDstInfo), mDstInfo.derivedUri);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Cannot create archive '" + redact(displayName) + "' in " + redact(mDstInfo),
                    e);
            onFileFailed(mResolvedDocs);
            return false;
        }
    }

    @Override
    void finish() {
        if (mDstInfo != null) {
            try {
                ArchivesProvider.releaseArchive(getClient(mDstInfo), mDstInfo.derivedUri);
            } catch (Exception e) {
                Log.e(TAG, "Cannot release archive " + redact(mDstInfo), e);
            }
        }

        // Remove the archive file in case of an error.
        if (mArchiveUri != null && (!isFinished() || isCanceled())) {
            try {
                DocumentsContract.deleteDocument(wrap(getClient(mArchiveUri)), mArchiveUri);
            } catch (Exception e) {
                Log.e(TAG, "Cannot remove partial archive " + redact(mArchiveUri), e);
            }
        }

        super.finish();
    }

    @Override
    boolean checkSpace() {
        // We're unable to determine how much space the archive will take, so we assume it will fit.
        return true;
    }

    @Override
    public String toString() {
        return "CompressJob {id=" + id + ", uris=" + mResourceUris + ", docs=" + mResolvedDocs
                + ", destination=" + stack + "}";
    }
}
