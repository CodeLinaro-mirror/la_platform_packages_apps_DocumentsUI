/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.loaders

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
import android.provider.DocumentsContract.Document.COLUMN_SUMMARY
import android.text.TextUtils
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.loader.app.LoaderManager.LoaderCallbacks
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import com.android.documentsui.ModelId
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Providers

/** Maps from the ModelId to the summary of the document. */
typealias Summaries = Map<String, String>

/**
 * A loader that fetches summaries for a list of documents from a given provider.
 *
 * This loader is designed to be initiated after the main directory content has loaded. It queries
 * the summary provider in the background for each Model ID provided.
 */
class SummaryLoader(
    context: Context,
    private val summaryAuthorityUri: Uri?,
    private val parentDoc: DocumentInfo?,
    private val modelIds: List<String>,
    private val options: QueryOptions?,
    private val query: String?,
) : AsyncTaskLoader<Summaries>(context) {
    private var summaries: Summaries? = null

    /** The authority part of the URI. The URI is a root URI. */
    private val summaryAuthority: String? = summaryAuthorityUri?.authority

    companion object {
        private const val TAG = "SummaryLoader"
        private val summaryProjection = arrayOf(COLUMN_DOCUMENT_ID, COLUMN_SUMMARY)

        /** Creates a new SummaryLoader and callback to deliver the result. */
        @JvmStatic
        fun createCallback(
            context: Context,
            summaryAuthorityUri: Uri,
            parentDoc: DocumentInfo?,
            docIds: List<String>,
            options: QueryOptions?,
            query: String?,
            finishCallback: (Summaries) -> Unit,
        ): LoaderCallbacks<Summaries> {
            return object : LoaderCallbacks<Summaries> {
                override fun onCreateLoader(id: Int, args: Bundle?): Loader<Summaries> {
                    return SummaryLoader(
                        context,
                        summaryAuthorityUri,
                        parentDoc,
                        docIds,
                        options,
                        query,
                    )
                }

                override fun onLoadFinished(loader: Loader<Summaries>, summaries: Summaries?) {
                    finishCallback(summaries ?: mutableMapOf())
                }

                override fun onLoaderReset(loader: Loader<Summaries>) {}
            }
        }
    }

    override fun loadInBackground(): Summaries {
        try {
            return loadInBackgroundByFolder()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch summaries from provider: $summaryAuthority", e)
        }
        return mutableMapOf()
    }

    /** Fetches the summary for documents children of the active directory/root. */
    private fun loadInBackgroundByFolder(): Summaries {
        val loadedSummaries = mutableMapOf<String, String>()

        // If the load has been canceled, stop processing and return empty result.
        if (isLoadInBackgroundCanceled) {
            return loadedSummaries
        }

        // For Recents, the parent doc is the Recents root which is empty/null.
        val isRecents = parentDoc?.authority == null && parentDoc?.documentId.isNullOrEmpty()

        // For recents use the summary provider root.
        val parentUri =
            if (isRecents) {
                DocumentsContract.buildChildDocumentsUri(
                    summaryAuthority,
                    DocumentsContract.getRootId(summaryAuthorityUri),
                )
            } else {
                // For others use the parent folder URI.
                DocumentsContract.buildChildDocumentsUri(summaryAuthority, parentDoc.documentId)
            }

        // For context we use the parent URI.
        val contextUri =
            if (isRecents) {
                // For Recents it's the Media "Files" root.
                DocumentsContract.buildDocumentUri(
                    Providers.AUTHORITY_MEDIA,
                    Providers.ROOT_ID_FILES,
                )
            } else {
                DocumentsContract.buildDocumentUri(parentDoc.authority, parentDoc.documentId)
            }

        /** Map from the input modelId to the documentId that will be returned from the provider. */
        val docIdToModelId = mutableMapOf<String, String>()
        for (modelId in modelIds) {
            val docId = ModelId.getDocumentId(modelId)
            if (docId == null) {
                Log.d(TAG, "Invalid docId for modelId: $modelId")
                continue
            }
            docIdToModelId[docId] = modelId
        }

        val contentResolver: ContentResolver = context.contentResolver

        val queryArgs = Bundle()

        // Apply the same query options for Recents and Search.
        if (options != null) {
            val rejectBefore = options.getRejectBeforeTimestamp()
            if (rejectBefore > 0L) {
                queryArgs.putLong(DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER, rejectBefore)
            }
            if (!TextUtils.isEmpty(query)) {
                queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, query)
            }
            if (options.maxResultsPerRoot > ALL_RESULTS) {
                queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, options.maxResultsPerRoot)
            }
            queryArgs.putAll(options.otherQueryArgs)
        }
        queryArgs.putParcelable(EXTRA_URI, contextUri)

        val cursor = contentResolver.query(parentUri, summaryProjection, queryArgs, null)
        if (cursor == null) {
            Log.d(TAG, "Null cursor for: $parentUri")
        } else {
            cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_DOCUMENT_ID))
                    val summary = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_SUMMARY))
                    if (!summary.isNullOrEmpty()) {
                        val modelId = docIdToModelId[docId]
                        if (modelId == null) {
                            Log.d(TAG, "Null modelId for: $docId, skipping summary")
                            continue
                        }
                        loadedSummaries[modelId] = summary
                    }
                }
            }
        }

        return loadedSummaries
    }

    override fun deliverResult(data: Summaries?) {
        if (isReset) {
            // The Loader has been reset; ignore the result and invalidate the data.
            return
        }
        summaries = data
        if (isStarted) {
            super.deliverResult(data)
        }
    }

    override fun onStartLoading() {
        if (summaries != null) {
            // If we already have a result, deliver it immediately.
            deliverResult(summaries)
        }
        if (takeContentChanged() || summaries == null) {
            // If the data has changed or we don't have a result yet, start a new load.
            forceLoad()
        }
    }

    override fun onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad()
    }

    override fun onReset() {
        super.onReset()
        // Ensure the loader is stopped.
        onStopLoading()
        summaries = null
    }
}
