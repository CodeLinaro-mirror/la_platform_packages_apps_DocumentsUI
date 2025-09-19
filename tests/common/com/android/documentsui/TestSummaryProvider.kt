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

package com.android.documentsui

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log

internal class TestSummaryProvider :
    TestRootProvider("Test Summary Provider", ROOT_ID, 0, ROOT_ID) {
    private var isEmpty = true

    companion object {
        const val AUTHORITY = "com.android.documentsui.summaryprovider"
        const val TAG = "TestSummaryProvider"
        private const val ROOT_ID = "summary-root"
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate(): $AUTHORITY/$ROOT_ID")
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        Log.d(TAG, "queryRoots(): $AUTHORITY/$ROOT_ID")
        val result =
            MatrixCursor(
                projection
                    ?: arrayOf(
                        DocumentsContract.Root.COLUMN_ROOT_ID,
                        DocumentsContract.Root.COLUMN_FLAGS,
                    )
            )
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            if (isEmpty) DocumentsContract.Root.FLAG_EMPTY else 0,
        )
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor? {
        Log.d(TAG, "queryDocument(): $AUTHORITY/$ROOT_ID")
        return null // Not needed for now.
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        Log.d(TAG, "queryChildDocuments(): $AUTHORITY/$ROOT_ID")
        return null // Not needed for now.
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        Log.d(TAG, "call(): $AUTHORITY/$ROOT_ID")
        if (method == "setIsEmpty") {
            isEmpty = extras?.getBoolean("isEmpty") ?: false
            Log.d(TAG, "call(): $AUTHORITY/$ROOT_ID: isEmpty: $isEmpty")
            context!!.contentResolver.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
            return null
        }
        return super.call(method, arg, extras)
    }
}
