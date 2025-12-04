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
import android.database.MatrixCursor
import android.net.Uri
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags
import junit.framework.AssertionFailedError
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private val displayNameColumn = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
private val titleColumn = arrayOf(DocumentsContract.Root.COLUMN_TITLE)

private fun createDisplayNameCursor(pathItemName: String) =
    MatrixCursor(displayNameColumn, 1).apply { addRow(arrayOf(pathItemName)) }

@EnableFlags(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.FLAG_USE_MATERIAL3)
@RunWith(AndroidJUnit4::class)
@SmallTest
class PathExtractorTest {

    /** Testable variant of PathExtractor with methods that use content resolver stubbed out. */
    inner class TestablePathExtractor(context: Context) : PathExtractor(context) {
        var documentPath: DocumentsContract.Path? =
            DocumentsContract.Path("root.id", listOf("Root"))
        var titleCursor = MatrixCursor(titleColumn)
        var displayNameCursorMap = mutableMapOf<String, MatrixCursor>()
        var exception: Exception? = null

        /** Fetches DocumentsContract.Path for the given `documentUri` */
        override fun getDocumentPath(documentUri: Uri) = documentPath

        /** Fetches a cursor for the given Uri that provides access to the title column. */
        override fun getTitleCursor(rootUri: Uri) = titleCursor

        /** Fetches a cursor for the given Uri that provides access to the display name column. */
        override fun getDisplayNameCursor(itemUri: Uri): Cursor {
            if (exception != null) {
                throw exception!!
            }
            for (item in displayNameCursorMap) {
                if (itemUri.toString().contains(item.key)) {
                    return item.value
                }
            }
            throw AssertionFailedError("Display name cursor request for unknown $itemUri")
        }
    }

    private lateinit var pathExtractor: TestablePathExtractor

    @Before
    fun setUp() {
        pathExtractor =
            TestablePathExtractor(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun testSuccessfullyGetPath() {
        // Setup.
        val docInfo =
            DocumentInfo().apply {
                userId = UserId.DEFAULT_USER
                authority = Providers.AUTHORITY_DOWNLOADS
                documentId = "file.txt.id"
                displayName = "file.txt"
                mimeType = "text/plain"
                deriveFields()
            }
        val pathIds = listOf("download.id", "foo.id", docInfo.documentId)
        val rootId = "downloads-root.id"
        val idToNameMap =
            mapOf(
                rootId to "Downloads",
                pathIds[0] to "Download",
                pathIds[1] to "Foo",
                pathIds[2] to "file.txt",
            )

        // When resolving root title.
        pathExtractor.titleCursor.addRow(arrayOf("Downloads"))
        // When fetching DocumentsContract.Path.
        pathExtractor.documentPath = DocumentsContract.Path(rootId, pathIds)
        // Map from path ID to cursor with the display name.
        for (item in idToNameMap) {
            pathExtractor.displayNameCursorMap.put(item.key, createDisplayNameCursor(item.value))
        }

        // The actual test.
        val path = pathExtractor.getDocumentInfoPath(docInfo)
        Assert.assertEquals("Downloads/Foo/file.txt", path.joinToString("/"))
    }

    // TODO(b/444316005): Special case, where Recent view uses Media document provider. Remove.
    @Test
    fun testApproximatePathForMediaFile() {
        val docInfo =
            DocumentInfo().apply {
                userId = UserId.DEFAULT_USER
                authority = Providers.AUTHORITY_MEDIA
                documentId = "file.mp3.id"
                displayName = "file.mp3"
                mimeType = "audio/mp3"
                deriveFields()
            }
        pathExtractor.titleCursor.addRow(arrayOf("Media"))
        pathExtractor.documentPath =
            DocumentsContract.Path("recents.id", listOf("recents.id", docInfo.documentId))
        pathExtractor.exception = UnsupportedOperationException("no-supported-for-media")

        val path = pathExtractor.getDocumentInfoPath(docInfo)
        Assert.assertEquals("Recent/file.mp3", path.joinToString("/"))
    }
}
