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
import com.android.documentsui.R

/**
 * Encapsulates functionality needed to extract full path of a DocumentInfo. Typical use would be to
 * create this class once, and call the `getDocumentInfoPath` for each document for which we wish to
 * have the path represented by a collection of directory names ending in the document display name.
 */
open class PathExtractor(private val context: Context) {
    companion object {
        private const val TAG = "PathExtractor"
        private const val NAME_COLUMN = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        private const val TITLE_COLUMN = DocumentsContract.Root.COLUMN_TITLE
    }

    /**
     * Extracts a full path for the given DocumentInfo. If the path extraction fails, this method
     * throws NoSuchElementException. It is the responsibility of the caller to correctly handle
     * both success and failure cases.
     *
     * @throws NoSuchElementException if the path extraction fails.
     */
    fun getDocumentInfoPath(docInfo: DocumentInfo): Array<String> {
        val uri = docInfo.derivedUri
        val rootTitle = getRootTitle(docInfo)
        try {
            val path = getDocumentPath(uri)
            if (path === null || path.path === null || path.path.isEmpty()) {
                throw NoSuchElementException("Failed to resolve path for ${docInfo.documentId}")
            }
            val pathIdParts =
                if (
                    docInfo.authority == Providers.AUTHORITY_DOWNLOADS ||
                        docInfo.authority == Providers.AUTHORITY_STORAGE
                ) {
                    // When resolving path for Downloads the top folder is Download. For External
                    // storage authority, the top folder is user ID. We don't want either of those,
                    // so drop them from the path.
                    path.path.subList(1, path.path.size)
                } else {
                    path.path
                }
            val displayNameList = mutableListOf<String>()
            displayNameList.add(rootTitle)
            pathIdParts.forEach { displayNameList.add(getDisplayName(docInfo.authority, it)) }
            return displayNameList.toTypedArray()
        } catch (e: IllegalArgumentException) {
            throw NoSuchElementException("Failed to resolve path for ${docInfo.documentId}: $e")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "$uri does not support path extraction: $e: ${e.stackTraceToString()}")
            return returnApproximatePath(rootTitle, docInfo)
        }
    }

    /** Fallback method if we cannot get the path for the given DocumentInfo. */
    private fun returnApproximatePath(rootTitle: String, docInfo: DocumentInfo): Array<String> {
        if (docInfo.authority == Providers.AUTHORITY_MEDIA) {
            return arrayOf(context.getString(R.string.root_recent), docInfo.displayName)
        }
        return arrayOf(rootTitle, docInfo.displayName)
    }

    /** Attempts to get the root title for the given DocumentInfo. */
    private fun getRootTitle(docInfo: DocumentInfo): String {
        var cursor: Cursor? = null
        try {
            cursor = getTitleCursor(DocumentsContract.buildRootsUri(docInfo.authority))
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(TITLE_COLUMN)
                if (index != -1) {
                    return cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
        throw NoSuchElementException("Can't find matching root for id=${docInfo.documentId}")
    }

    /** Extracts the display name of the path item with the given `itemUri`. */
    private fun getDisplayName(authority: String, pathItemId: String): String {
        var cursor: Cursor? = null
        val itemUri = DocumentsContract.buildDocumentUri(authority, pathItemId)
        try {
            cursor = getDisplayNameCursor(itemUri)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(NAME_COLUMN)
                if (index != -1) {
                    return cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
        throw NoSuchElementException("Failed to obtain cursor for $itemUri")
    }

    /** Fetches DocumentsContract.Path for the given `documentUri` */
    protected open fun getDocumentPath(documentUri: Uri) =
        DocumentsContract.findDocumentPath(context.contentResolver, documentUri)

    /** Fetches a cursor for the given Uri that provides access to the title column. */
    protected open fun getTitleCursor(rootUri: Uri) =
        context.contentResolver.query(rootUri, arrayOf(TITLE_COLUMN), null, null, null)

    /** Fetches a cursor for the given Uri that provides access to the display name column. */
    protected open fun getDisplayNameCursor(itemUri: Uri) =
        context.contentResolver.query(itemUri, arrayOf(NAME_COLUMN), null, null, null)
}
