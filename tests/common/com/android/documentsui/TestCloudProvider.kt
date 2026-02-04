/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.documentsui

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.util.Log

/**
 * Test "cloud" provider that sets the FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE root flag and the
 * COLUMN_CONTENT_SYNC_STATE_FLAGS for documents.
 */
internal class TestCloudProvider :
    TestRootProvider(
        NAME,
        ROOT_ID,
        // Do not guard with isSyncStateEnabled() as the FlagUtils lib is not available for some
        // reason and calling this will lead to a NoClassDefFoundError. Accessing this static
        // constant without the check is safe because it will be available during compilation and
        // inserted inline.
        Root.FLAG_LIMITED_FUNCTIONALITY_WHEN_OFFLINE,
        ROOT_ID,
    ) {
    companion object {
        const val ROOT_ID = "cloud-root"
        const val AUTHORITY = "com.android.documentsui.cloudprovider"
        const val NAME = "Test Cloud Provider"
        const val DOC_ID_0 = "docId0"
        const val DISPLAY_NAME_0 = "displayName0"
        const val DOC_ID_1 = "docId1"
        const val DISPLAY_NAME_1 = "displayName1"
        const val VIRTUAL_ID = "virtualId"
        const val VIRTUAL_DISPLAY_NAME = "virtual"
        const val DIR_ID = "dirDocId"
        const val DIR_DISPLAY_NAME = "dirDisplayName"
        const val SET_SYNC_STATE = "setSyncState"
        const val NULLIFY_SYNC_STATE = "nullifySyncState"
        const val METHOD_DOC_ID_EXTRA = "documentId"
        const val METHOD_STATE_EXTRA = "syncState"
        private const val TAG = "TestCloudProvider"
        private val NOTIFY_URI = DocumentsContract.buildRootsUri(AUTHORITY)
    }

    data class Doc(
        val displayName: String,
        var syncState: Int?,
        var flags: Int,
        val mimeType: String,
    )

    // documentId -> File
    private val files =
        mapOf<String, Doc>(
            DOC_ID_0 to Doc(DISPLAY_NAME_0, 0, 0, "text/plain"),
            DOC_ID_1 to Doc(DISPLAY_NAME_1, 0, 0, "text/plain"),
            VIRTUAL_ID to
                Doc(VIRTUAL_DISPLAY_NAME, 0, Document.FLAG_VIRTUAL_DOCUMENT, "text/plain"),
            DIR_ID to Doc(DIR_DISPLAY_NAME, 0, 0, Document.MIME_TYPE_DIR),
        )

    private fun setSyncState(documentId: String?, syncState: Int?) {
        if (documentId == null) {
            Log.d(TAG, "documentId is null")
            return
        }
        if (!files.containsKey(documentId)) {
            Log.d(TAG, "$documentId doesn't exist in provider")
            return
        }
        files[documentId]!!.syncState = syncState
        context?.contentResolver?.notifyChange(NOTIFY_URI, null)
        Log.d(TAG, "Updated sync state of $documentId to $syncState")
    }

    override fun call(authority: String, method: String, arg: String?, extras: Bundle?): Bundle? {
        val result = super.call(method, arg, extras)
        if (result != null) {
            return result
        }
        when (method) {
            SET_SYNC_STATE -> {
                setSyncState(
                    extras!!.getString(METHOD_DOC_ID_EXTRA),
                    extras.getInt(METHOD_STATE_EXTRA, 0),
                )
                return null
            }
            NULLIFY_SYNC_STATE -> {
                setSyncState(extras!!.getString(METHOD_DOC_ID_EXTRA), null)
                return null
            }
        }
        return null
    }

    override fun queryDocument(documentId: String?, projection: Array<out String?>?): Cursor? {
        val c = createDocCursor(projection)
        // Return a folder for the case when the root is queried. The cases for when a file is
        // queried are not covered.
        addFolder(c, documentId)
        return c
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String?>?,
        sortOrder: String?,
    ): Cursor? {
        // Do not guard with isSyncStateEnabled() as the FlagUtils lib is not available for some
        // reason and calling this will lead to a NoClassDefFoundError. Accessing this static
        // constant without the check is safe because it will be available during compilation and
        // inserted inline.
        val cursor =
            MatrixCursor(
                projection
                    ?: arrayOf(
                        Document.COLUMN_DOCUMENT_ID,
                        Document.COLUMN_DISPLAY_NAME,
                        Document.COLUMN_CONTENT_SYNC_STATE_FLAGS,
                        Document.COLUMN_FLAGS,
                        Document.COLUMN_MIME_TYPE,
                    )
            )
        for ((documentId, doc) in files) {
            val row = cursor.newRow()
            row.add(Document.COLUMN_DOCUMENT_ID, documentId)
            row.add(Document.COLUMN_DISPLAY_NAME, doc.displayName)
            row.add(Document.COLUMN_CONTENT_SYNC_STATE_FLAGS, doc.syncState)
            row.add(Document.COLUMN_FLAGS, doc.flags)
            row.add(Document.COLUMN_MIME_TYPE, doc.mimeType)
            Log.d(TAG, "Add sync state for $documentId: ${doc.syncState}")
        }
        // Set the notificationUri so that the notifyChange calls trigger observers of this cursor.
        cursor.setNotificationUri(context?.contentResolver, NOTIFY_URI)
        return cursor
    }
}
