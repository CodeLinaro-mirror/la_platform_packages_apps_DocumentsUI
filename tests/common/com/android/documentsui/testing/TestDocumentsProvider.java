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

package com.android.documentsui.testing;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Test doubles of {@link DocumentsProvider} to isolate document providers. This is not registered
 * or exposed through AndroidManifest, but only used locally.
 */
public class TestDocumentsProvider extends DocumentsProvider {

    private static final String TAG = "TestDocumentsProvider";

    private String[] DOCUMENTS_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SUMMARY,
            Document.COLUMN_SIZE,
            Document.COLUMN_ICON
    };

    /** Faked result for {@link #queryChildDocuments(String, String[], String)}. */
    private Cursor mNextChildDocuments;

    /** Faked result for {@link #queryRecentDocuments(String, String[])}. */
    private Cursor mNextRecentDocuments;

    /**
     * Faked result for {@link #queryTrashDocuments(String, String[], Bundle, CancellationSignal)}.
     */
    private Cursor mNextTrashDocuments;

    /** Runtime exception thrown in either querySearchDocuments() or queryChildDocuments(). */
    private String mRuntimeMessage;

    /** Artificial delay added before this provider returns its results, 0 means no delay. */
    private long mQueryDelayMs = 0;

    /** Maps from document ID to a summary, that will be used when querying for summaries. */
    private final Map<String, String> mNextSummaries = new HashMap<>();

    /** The last query args passed to {@link #queryChildDocuments()}. */
    private @Nullable Bundle mLastQueryArgs = null;

    /** Sets the summaries that should be emulated. */
    public void setDocumentSummaries(Map<String, String> summaries) {
        mNextSummaries.clear();
        mNextSummaries.putAll(summaries);
    }

    public String getAuthority() {
        return mAuthority;
    }

    @Nullable
    public Bundle getLastQueryArgs() {
        return mLastQueryArgs;
    }

    public void setLastQueryArgs(@Nullable Bundle lastQueryArgs) {
        mLastQueryArgs = lastQueryArgs;
    }

    /**
     * A latch that will be decremented when a query is about to be delayed. This allows tests to
     * synchronize with the start of the delay.
     */
    @Nullable private CountDownLatch mQueryDelayLatch = null;
    private final String mAuthority;

    // Emulates FileSystemProvider's support for search result limiting.
    private Boolean mSupportsSearchResultLimit = false;
    private static final int DEFAULT_MAX_RESULTS = 23;  /* FileSystemProvider.DEFAULT_MAX_RESULTS */

    public TestDocumentsProvider(Context context, String authority) {
        mAuthority = authority;
        ProviderInfo info = new ProviderInfo();
        info.authority = authority;
        attachInfoForTesting(context, info);
    }

    @Override
    public boolean refresh(Uri url, Bundle args, CancellationSignal signal) {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        maybeThrowException();
        maybeDelayQueryResults();

        if (projection == null) {
            projection = new String[] {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_SUMMARY};
        }
        final MatrixCursor result = new MatrixCursor(projection);

        // This provider might be used to check for the existence of a doc.
        // If it doesn't exist in the test model, we should return an empty cursor.
        if (!mNextSummaries.containsKey(documentId)) {
            Log.d(TAG, "queryDocument(): document not found: " + documentId);
            return result;
        }

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_SUMMARY, mNextSummaries.getOrDefault(documentId, null));

        Log.d(TAG, "queryDocument(): document found: " + documentId);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            String sortOrder) throws FileNotFoundException {
        maybeThrowException();
        maybeDelayQueryResults();

        if (mNextChildDocuments != null) {
            Log.d(TAG, "queryChildDocuments: returning from mNextChildDocuments");
            return mNextChildDocuments;
        }

        // If the summaries has been set, use it.
        if (!mNextSummaries.isEmpty()) {
            final MatrixCursor result =
                    new MatrixCursor(
                            new String[] {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_SUMMARY});
            final MatrixCursor.RowBuilder row = result.newRow();
            for (String documentId : mNextSummaries.keySet()) {
                row.add(Document.COLUMN_DOCUMENT_ID, documentId);
                row.add(Document.COLUMN_SUMMARY, mNextSummaries.getOrDefault(documentId, null));
            }
            Log.d(TAG, "queryChildDocuments: returning from mNextSummaries");
            return result;
        }
        Log.d(TAG, "queryChildDocuments: returning null");
        return null;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {
        mLastQueryArgs = queryArgs;
        return queryChildDocuments(parentDocumentId, projection, (String) null);
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection) {
        return mNextRecentDocuments;
    }

    @Nullable
    @Override
    public Cursor queryTrashDocuments(
            @NonNull String rootId,
            @Nullable String[] projection,
            @Nullable Bundle queryArgs,
            @Nullable CancellationSignal signal)
            throws FileNotFoundException {
        maybeThrowException();
        return mNextTrashDocuments;
    }

    private String getStringColumn(Cursor cursor, String name) {
        return cursor.getString(cursor.getColumnIndexOrThrow(name));
    }

    private long getLongColumn(Cursor cursor, String name) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(name));
    }

    @Override
    public Cursor querySearchDocuments(@NonNull String rootId, @Nullable String[] projection,
            @NonNull Bundle queryArgs) {
        mLastQueryArgs = queryArgs;
        maybeThrowException();
        maybeDelayQueryResults();
        TestCursor cursor = new TestCursor(DOCUMENTS_PROJECTION);

        int maxResults = -1;
        if (mSupportsSearchResultLimit) {
            // FileSystemProvider has no concept of "all search results" (the -1/ALL_RESULTS option
            // used within parts of DocumentsUI); if no limit, or a negative limit is sent, it
            // will always apply its default limit. We emulate that behaviour here for testing.
            maxResults = queryArgs.getInt(ContentResolver.QUERY_ARG_LIMIT, DEFAULT_MAX_RESULTS);
            if (maxResults < 0) {
                maxResults = DEFAULT_MAX_RESULTS;
            }
        }

        if (mNextChildDocuments == null) {
            return cursor;
        }
        for (boolean hasNext = mNextChildDocuments.moveToFirst();
                hasNext && ((maxResults < 0) || (cursor.getCount() < maxResults));
                hasNext = mNextChildDocuments.moveToNext()) {
            String displayName = getStringColumn(mNextChildDocuments, Document.COLUMN_DISPLAY_NAME);
            String mimeType = getStringColumn(mNextChildDocuments, Document.COLUMN_MIME_TYPE);
            long lastModified = getLongColumn(mNextChildDocuments, Document.COLUMN_LAST_MODIFIED);
            long size = getLongColumn(mNextChildDocuments, Document.COLUMN_SIZE);

            if (DocumentsContract.matchSearchQueryArguments(queryArgs, displayName, mimeType,
                    lastModified, size)) {
                cursor.newRow()
                        .add(Document.COLUMN_DOCUMENT_ID,
                                getStringColumn(mNextChildDocuments, Document.COLUMN_DOCUMENT_ID))
                        .add(Document.COLUMN_MIME_TYPE,
                                getStringColumn(mNextChildDocuments, Document.COLUMN_MIME_TYPE))
                        .add(Document.COLUMN_DISPLAY_NAME,
                                getStringColumn(mNextChildDocuments, Document.COLUMN_DISPLAY_NAME))
                        .add(Document.COLUMN_LAST_MODIFIED,
                                getLongColumn(mNextChildDocuments, Document.COLUMN_LAST_MODIFIED))
                        .add(Document.COLUMN_FLAGS,
                                getLongColumn(mNextChildDocuments, Document.COLUMN_FLAGS))
                        .add(Document.COLUMN_SUMMARY,
                                getStringColumn(mNextChildDocuments, Document.COLUMN_SUMMARY))
                        .add(Document.COLUMN_SIZE,
                                getLongColumn(mNextChildDocuments, Document.COLUMN_SIZE))
                        .add(Document.COLUMN_ICON,
                                getLongColumn(mNextChildDocuments, Document.COLUMN_ICON));
            }
        }
        Log.d(TAG, "Delivering " + cursor.getCount() + " results");
        return cursor;
    }

    @Override
    public Cursor querySearchDocuments(String rootId, String query, String[] projection) {
        maybeThrowException();
        maybeDelayQueryResults();
        if (mNextChildDocuments == null) {
            return null;
        }

        return filterCursorByString(mNextChildDocuments, query);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Sets the next return value for {@link #queryChildDocuments(String, String[], String)}.
     * @param docs docs to return for next query.
     */
    public void setNextChildDocumentsReturns(DocumentInfo... docs) {
        mNextChildDocuments = createDocumentsCursor(docs);
    }

    private void maybeThrowException() {
        if (mRuntimeMessage != null) {
            throw new RuntimeException(mRuntimeMessage);
        }
    }

    /**
     * Sets the artificial delay added before this provider returns its results. Setting the delay
     * to a non-positive number causes the results to be returned immediately.
     */
    public void setQueryDelay(long queryDelayMs) {
        Log.d(TAG, "Setting delay " + queryDelayMs + "ms on " + mAuthority);
        mQueryDelayMs = queryDelayMs;
    }

    /**
     * Sets a latch to be activated when the query delay is about to be triggered. If the set
     * `latch` is null, the latch is cleared and no latch count down is called.
     * @param latch Either a latch to be used or null.
     */
    public void setQueryDelayLatch(@Nullable CountDownLatch latch) {
        Log.d(TAG, "Setting query delay latch to " + latch);
        mQueryDelayLatch = latch;
    }

    private void maybeDelayQueryResults() {
        if (mQueryDelayMs <= 0) {
            Log.d(TAG, "Immediate delivery of results for " + mAuthority);
            return;
        }
        if (mQueryDelayLatch != null) {
            Log.d(TAG, "Decrementing count on the queryDealyLatch " + mQueryDelayLatch);
            mQueryDelayLatch.countDown();
        }
        Log.d(TAG, "Delaying query results by " + mQueryDelayMs + "ms for " + mAuthority);
        SystemClock.sleep(mQueryDelayMs);
        Log.d(TAG, "Delay of " + mQueryDelayMs + "ms for " + mAuthority + " done");
    }

    /**
     * Sets the runtime exception thrown in either querySearchDocuments or queryChildDocuments. If
     * the message is set to null, no exception is thrown. A non-null message causes an exception
     * to be thrown, interrupting a regular flow of document query.
     * @param message The message to be used with a Runtime exception.
     */
    public void setThrownRuntimeMessage(String message) {
        mRuntimeMessage = message;
    }

    /**
     * Allows TestDocumentsProvider to emulate search result limiting feature of FileSystemProvider.
     * @param supportsLimit Whether {@link #querySearchDocuments(String, String[], Bundle)}
     *                      should limit results.
     */
    public void setSupportsSearchResultLimit(Boolean supportsLimit) {
        mSupportsSearchResultLimit = supportsLimit;
    }

    public void setNextRecentDocumentsReturns(DocumentInfo... docs) {
        mNextRecentDocuments = createDocumentsCursor(docs);
    }

    /**
     * Sets the documents to be returned by the next call to {@link #queryTrashDocuments(String,
     * String[], Bundle, CancellationSignal)}.
     *
     * @param docs The documents to be returned in the cursor.
     */
    public void setNextTrashDocumentsReturns(DocumentInfo... docs) {
        mNextTrashDocuments = createDocumentsCursor(docs);
    }

    private Cursor createDocumentsCursor(DocumentInfo... docs) {
        TestCursor cursor = new TestCursor(DOCUMENTS_PROJECTION);
        for (DocumentInfo doc : docs) {
            cursor.newRow()
                    .add(Document.COLUMN_DOCUMENT_ID, doc.documentId)
                    .add(Document.COLUMN_MIME_TYPE, doc.mimeType)
                    .add(Document.COLUMN_DISPLAY_NAME, doc.displayName)
                    .add(Document.COLUMN_LAST_MODIFIED, doc.lastModified)
                    .add(Document.COLUMN_FLAGS, doc.flags)
                    .add(
                            Document.COLUMN_SUMMARY,
                            mNextSummaries.getOrDefault(doc.documentId, doc.summary))
                    .add(Document.COLUMN_SIZE, doc.size)
                    .add(Document.COLUMN_ICON, doc.icon);
        }

        return cursor;
    }

    private static Cursor filterCursorByString(@NonNull Cursor cursor, String query) {
        final int count = cursor.getCount();
        final String[] columnNames = cursor.getColumnNames();

        final MatrixCursor resultCursor = new MatrixCursor(columnNames, count);
        cursor.moveToPosition(-1);
        for (int i = 0; i < count; i++) {
            cursor.moveToNext();
            final int index = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME);
            if (!cursor.getString(index).contains(query)) {
                continue;
            }

            final MatrixCursor.RowBuilder builder = resultCursor.newRow();
            final int columnCount = cursor.getColumnCount();
            for (int j = 0; j < columnCount; j++) {
                final int type = cursor.getType(j);
                switch (type) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        builder.add(cursor.getLong(j));
                        break;

                    case Cursor.FIELD_TYPE_STRING:
                        builder.add(cursor.getString(j));
                        break;

                    default:
                        break;
                }
            }
        }
        cursor.moveToPosition(-1);
        return resultCursor;
    }
}
