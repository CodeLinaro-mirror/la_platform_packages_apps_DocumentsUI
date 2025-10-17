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
package com.android.documentsui.roots

import android.app.Activity
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentResolver.wrap
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.TimeoutTask
import com.android.documentsui.base.DocumentInfo.getCursorString
import com.android.documentsui.base.ShortcutInfo
import java.util.function.Consumer

/**
 * A [CheckedTask] that takes [ShortcutInfo] and query to obtain the [Uri] of the shortcut folder if
 * it exists. If the folder does not exist yet, create the folder and return the [Uri]. Set the
 * shortcut's uri once the task is finished and then call [GetDocumentTask] to open DocumentsUI to
 * the new shortcut document.
 */
class GetShortcutUriTask(
    private val shortcut: ShortcutInfo,
    private val resolver: ContentResolver,
    activity: Activity,
    timeout: Long,
    private val callback: Consumer<Uri?>,
) : TimeoutTask<Void?, Uri?>(Check { activity.isDestroyed }, timeout) {
    public override fun run(vararg args: Void?): Uri? {
        var client: ContentProviderClient?
        var cursor: Cursor?
        val authority: String = shortcut.root.authority
        val parentDocUri =
            DocumentsContract.buildDocumentUri(authority, shortcut.parentDirDocumentId)
        val childrenUri =
            DocumentsContract.buildChildDocumentsUri(authority, shortcut.parentDirDocumentId)
        try {
            // Do a document query freshness test using information from the parent root.
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority)
            val projection =
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
            cursor = client.query(childrenUri, projection, null, null, null)
            while (cursor!!.moveToNext()) {
                val title = getCursorString(cursor, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (shortcut.title.equals(title)) {
                    val docId =
                        getCursorString(cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    return DocumentsContract.buildDocumentUri(authority, docId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query document", e)
        }

        // If the folder does not exist, try to create the folder
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority)
            val folderUri =
                DocumentsContract.createDocument(
                    wrap(client),
                    parentDocUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    shortcut.title!!,
                )
            if (folderUri != null) {
                Log.i(TAG, "Successfully created folder " + folderUri)
                return folderUri
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create folder", e)
        }
        return null
    }

    public override fun finish(uri: Uri?) {
        if (uri == null) {
            return
        }
        callback.accept(uri)
    }

    companion object {
        private const val TAG = "GetShortcutUriTask"
    }
}
