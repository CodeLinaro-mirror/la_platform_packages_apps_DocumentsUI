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
package com.android.documentsui.base

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.android.documentsui.roots.ProvidersAccess

/**
 * Encapsulates functionality needed to extract full path of a DocumentInfo. Typical use would be to
 * create this class once, and call the `getDocumentStack` for each document for which we wish to
 * have the path represented by a DocumentStack object.
 */
open class PathExtractor(
    private val context: Context,
    private val providerAccess: ProvidersAccess,
) {
    companion object {
        private const val TAG = "PathExtractor"
    }

    /**
     * Extracts a full stack for the given DocumentInfo. If the stack extraction fails, this method
     * throws NoSuchElementException. It is the responsibility of the caller to correctly handle
     * both success and failure cases.
     *
     * @throws NoSuchElementException if the stack extraction fails.
     */
    fun getDocumentStack(docInfo: DocumentInfo): DocumentStack {
        val uri = docInfo.derivedUri
        val rootInfo = getRootInfo(docInfo)
        try {
            val path = getDocumentPath(uri)
            if (path === null || path.path === null || path.path.isEmpty()) {
                throw NoSuchElementException("Failed to resolve path for ${docInfo.documentId}")
            }
            val docInfoArray = Array(path.path.size) { getDocumentInfo(docInfo, path.path[it]) }
            return DocumentStack(rootInfo, *docInfoArray)
        } catch (e: IllegalArgumentException) {
            throw NoSuchElementException("Failed to resolve path for ${docInfo.documentId}: $e")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "$uri does not support path extraction: $e: ${e.stackTraceToString()}")
            return approximateDocumentStack(rootInfo, docInfo)
        } catch (e: Exception) {
            throw e
        }
    }

    /** Fallback method if we cannot get the path for the given DocumentInfo. */
    private fun approximateDocumentStack(rootInfo: RootInfo, docInfo: DocumentInfo): DocumentStack {
        // We create a root dir as the rest of the code is not prepared to have a document stack
        // that consists of the root and file, with no directory represented by DocumentInfo.
        val rootDir =
            DocumentInfo().apply {
                userId = rootInfo.userId
                authority = rootInfo.authority
                displayName = rootInfo.title
                documentId = rootInfo.documentId
                deriveFields()
            }
        if (docInfo.authority == Providers.AUTHORITY_MEDIA) {
            return DocumentStack(providerAccess.getRecentsRoot(docInfo.userId), rootDir, docInfo)
        }
        return DocumentStack(rootInfo, rootDir, docInfo)
    }

    /** Attempts to get the root for the given DocumentInfo. */
    private fun getRootInfo(docInfo: DocumentInfo): RootInfo {
        var cursor: Cursor? = null
        val uri = DocumentsContract.buildRootsUri(docInfo.authority)
        try {
            cursor = getCursorForUri(uri)
            if (cursor != null && cursor.moveToFirst()) {
                return RootInfo.fromRootsCursor(docInfo.userId, docInfo.authority, cursor)
            }
        } finally {
            cursor?.close()
        }
        throw NoSuchElementException("Can't find matching root for id=${docInfo.documentId}")
    }

    /** Extracts the document info for the given `pathItemId` of the path of `docInfo`. */
    private fun getDocumentInfo(docInfo: DocumentInfo, pathItemId: String): DocumentInfo {
        var cursor: Cursor? = null
        val itemUri = DocumentsContract.buildDocumentUri(docInfo.authority, pathItemId)
        try {
            cursor = getCursorForUri(itemUri)
            if (cursor != null && cursor.moveToFirst()) {
                return DocumentInfo.fromCursor(cursor, docInfo.userId, docInfo.authority)
            }
        } finally {
            cursor?.close()
        }
        throw NoSuchElementException("Failed to obtain cursor for $itemUri")
    }

    /** Fetches DocumentsContract.Path for the given `documentUri` */
    protected open fun getDocumentPath(documentUri: Uri) =
        DocumentsContract.findDocumentPath(context.contentResolver, documentUri)

    /** Fetches a cursor for the given Uri. */
    protected open fun getCursorForUri(uri: Uri) =
        context.contentResolver.query(uri, null, null, null, null)
}
