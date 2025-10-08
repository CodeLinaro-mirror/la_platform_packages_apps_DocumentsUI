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
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
import android.provider.DocumentsContract.Document.COLUMN_SUMMARY
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.loader.app.LoaderManager.LoaderCallbacks
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import com.android.documentsui.base.DocumentInfo

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
    private val authority: String,
    private val parentDoc: DocumentInfo?,
    private val modelIds: List<String>,
) : AsyncTaskLoader<Summaries>(context) {
    private var summaries: Summaries? = null

    companion object {
        private const val TAG = "SummaryLoader"
        private val summaryProjection = arrayOf(COLUMN_DOCUMENT_ID, COLUMN_SUMMARY)

        /** Creates a new SummaryLoader and callback to deliver the result. */
        @JvmStatic
        fun createCallback(
            context: Context,
            authority: String,
            parentDoc: DocumentInfo?,
            docIds: List<String>,
            finishCallback: (Summaries) -> Unit,
        ): LoaderCallbacks<Summaries> {
            return object : LoaderCallbacks<Summaries> {
                override fun onCreateLoader(id: Int, args: Bundle?): Loader<Summaries> {
                    return SummaryLoader(context, authority, parentDoc, docIds)
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
            // When loading for a local root or folder.
            if (parentDoc?.authority != null && !parentDoc.documentId.isNullOrEmpty()) {
                return loadInBackgroundByFolder()
            }
            // When loading for Recents.
            return loadInBackgroundByIds()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch summaries from provider: $authority", e)
        }
        return mutableMapOf()
    }

    /** Fetches the summary for documents children of the active directory/root. */
    private fun loadInBackgroundByFolder(): Summaries {
        val loadedSummaries = mutableMapOf<String, String>()

        if (parentDoc == null) {
            return loadedSummaries
        }
        // If the load has been canceled, stop processing and return empty result.
        if (isLoadInBackgroundCanceled) {
            return loadedSummaries
        }

        /** Map from the input modelId to the documentId that will be returned from the provider. */
        val docIdToModelId = mutableMapOf<String, String>()
        for (modelId in modelIds) {
            val (_, _, docId) = modelId.split('|')
            docIdToModelId[docId] = modelId
        }
        val contentResolver: ContentResolver = context.contentResolver
        val parentUri = DocumentsContract.buildChildDocumentsUri(authority, parentDoc.documentId)

        val queryArgs = Bundle()
        queryArgs.putParcelable(EXTRA_URI, DocumentsContract.buildRootsUri(parentDoc.authority))
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

    /**
     * Fetches the summary for each document ID provided, it sends one query per document, so prefer
     * to use `loadInBackgroundByFolder()` when possible.
     */
    private fun loadInBackgroundByIds(): Summaries {
        val loadedSummaries = mutableMapOf<String, String>()
        val contentResolver: ContentResolver = context.contentResolver

        for (modelId in modelIds) {
            // If the load has been canceled, stop processing and return empty result.
            if (isLoadInBackgroundCanceled) {
                return loadedSummaries
            }

            val (_, docAuthority, docId) = modelId.split('|')
            val docUri = DocumentsContract.buildDocumentUri(authority, docId)

            val queryArgs = Bundle()
            queryArgs.putParcelable(EXTRA_URI, DocumentsContract.buildRootsUri(docAuthority))

            val cursor = contentResolver.query(docUri, summaryProjection, queryArgs, null)
            if (cursor == null) {
                Log.d(TAG, "Null cursor for: $modelId")
            } else {
                cursor.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val summary = cursor.getStringOrNull(cursor.getColumnIndex(COLUMN_SUMMARY))
                        if (!summary.isNullOrEmpty()) {
                            loadedSummaries[modelId] = summary
                        }
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
