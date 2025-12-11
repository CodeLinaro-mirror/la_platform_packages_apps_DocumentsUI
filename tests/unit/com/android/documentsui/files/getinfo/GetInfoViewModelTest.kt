/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.files.getinfo

import android.app.Application
import android.content.ContentProvider
import android.content.res.Configuration
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.LocaleList
import android.os.Process
import android.provider.DocumentsContract
import android.provider.Settings
import android.test.mock.MockContentProvider
import android.test.mock.MockContentResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.UserId
import com.android.documentsui.rules.MainDispatcherRule
import java.util.Locale
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.openMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@SmallTest
@RunWith(AndroidJUnit4::class)
class GetInfoViewModelTest {

    @Mock private lateinit var application: Application
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var lookup: Lookup<String, String>

    private lateinit var contentResolver: MockContentResolver

    private val settingsProvider: ContentProvider =
        object : MockContentProvider() {
            override fun call(
                authority: String,
                method: String,
                arg: String?,
                extras: Bundle?,
            ): Bundle {
                return Bundle()
            }
        }
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule private val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        openMocks(this)

        val configuration = Configuration()
        configuration.setLocales(LocaleList(Locale.US))

        `when`(application.resources).thenReturn(resources)
        `when`(application.applicationContext).thenReturn(application)

        `when`(resources.configuration).thenReturn(configuration)

        // Prevent NullPointerException when Formatter asks for internal string resources.
        `when`(resources.getString(anyInt())).thenReturn("MockString")
        `when`(resources.getString(anyInt(), any())).thenReturn("MockString")
        `when`(resources.getText(anyInt())).thenReturn("MockString")

        // Mock content resolver for DateFormat.
        contentResolver = MockContentResolver()
        contentResolver.addProvider(Settings.AUTHORITY, settingsProvider)
        `when`(application.contentResolver).thenReturn(contentResolver)
        `when`(application.userId).thenReturn(Process.myUserHandle().identifier)

        // Mock string resources to return static strings.
        `when`(resources.getString(R.string.peek_metadata_general_info_title))
            .thenReturn("General info")
        `when`(resources.getString(R.string.sort_dimension_name)).thenReturn("Name")
        `when`(resources.getString(R.string.peek_metadata_type)).thenReturn("Type")
        `when`(resources.getString(R.string.peek_metadata_size)).thenReturn("Size")
        `when`(resources.getString(R.string.peek_metadata_date_modified)).thenReturn("Modified")
        `when`(resources.getString(R.string.sort_dimension_summary)).thenReturn("Summary")
        `when`(resources.getString(R.string.directory_items)).thenReturn("Items")
        `when`(resources.getString(R.string.datetime_format_12)).thenReturn("MMM d, yyyy")
        `when`(resources.getString(R.string.datetime_format_24)).thenReturn("MMM d, yyyy")
        `when`(resources.getString(R.string.get_info_unknown_file_type)).thenReturn("Unknown")

        // Mock some debug fields to validate in test (they are all synchronous and they fall back
        // to "MockString" anyway, so let's avoid using a whole bunch of them that effectively will
        // do the same validation.
        `when`(resources.getString(R.string.inspector_debug_section)).thenReturn("Debug Info")
        `when`(resources.getString(R.string.debug_user_id)).thenReturn("User ID")

        // Mock lookup to return folder type and a default for the remaining types.
        `when`(lookup.lookup(any())).thenReturn("File Type")
        `when`(lookup.lookup(eq(DocumentsContract.Document.MIME_TYPE_DIR))).thenReturn("Folder")
    }

    @Test
    fun testStandardFile_WithDebug() {
        val doc =
            DocumentInfo().apply {
                documentId = "testId"
                displayName = "test.pdf"
                mimeType = "application/pdf"
                size = 1024 * 1024 * 10
                lastModified = 1234567890L
                userId = UserId.DEFAULT_USER
            }

        val viewModel = GetInfoViewModel(application, doc, lookup, true)
        val list = viewModel.items.value

        // Should have Header, Name, TYpe, Size, Modified. Header, Doc ID.
        assertEquals(5 + DEBUG_ITEM_COUNT, list.size)
        assertEquals(ListItem.Header("General info"), list[0])
        assertEquals(ListItem.Info("Name", "test.pdf"), list[1])
        assertEquals(ListItem.Info("Type", "File Type"), list[2])
        assertEquals("Size", (list[3] as ListItem.Info).label)
        assertEquals("Modified", (list[4] as ListItem.Info).label)

        assertEquals(ListItem.Header("Debug Info"), list[5])
        assertEquals(ListItem.Info("User ID", UserId.CURRENT_USER.identifier.toString()), list[6])
    }

    @Test
    fun testStandardFile_NoDebug() {
        val doc =
            DocumentInfo().apply {
                documentId = "testId"
                displayName = "test.pdf"
                mimeType = "application/pdf"
                size = 1024 * 1024 * 10
                lastModified = 1234567890L
                userId = UserId.DEFAULT_USER
            }

        val viewModel = GetInfoViewModel(application, doc, lookup, false)
        val list = viewModel.items.value

        // Should have Header, Name, TYpe, Size, Modified. Header, Doc ID.
        assertEquals(5, list.size)
        assertEquals(ListItem.Header("General info"), list[0])
        assertEquals(ListItem.Info("Name", "test.pdf"), list[1])
        assertEquals(ListItem.Info("Type", "File Type"), list[2])
        assertEquals("Size", (list[3] as ListItem.Info).label)
        assertEquals("Modified", (list[4] as ListItem.Info).label)
    }

    @Test
    fun testDirectory() = runTest {
        val authority = "com.example.authority"
        val doc =
            DocumentInfo().apply {
                documentId = "testDirectoryId"
                displayName = "My Folder"
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR
                size = 0
                lastModified = 1234567890L
                userId = UserId.DEFAULT_USER
                this.authority = authority
                documentId = "myFolder"
            }

        val childrenProvider =
            object : MockContentProvider() {
                override fun query(
                    uri: Uri,
                    projection: Array<out String>?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                    sortOrder: String?,
                ): Cursor {
                    val cursor =
                        MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    cursor.addRow(arrayOf("child1"))
                    cursor.addRow(arrayOf("child2"))
                    cursor.addRow(arrayOf("child3"))
                    return cursor
                }
            }
        contentResolver.addProvider(authority, childrenProvider)

        val viewModel = GetInfoViewModel(application, doc, lookup, true, testDispatcher)
        val initialList: List<ListItem> = viewModel.items.value

        // Should have Header, Name, Type, Modified, Items. No size.
        // The asynchronous data hasn't been populated yet but there is a placeholder there.
        assertEquals(5 + DEBUG_ITEM_COUNT, initialList.size)
        assertEquals(ListItem.Header("General info"), initialList[0])
        assertEquals(ListItem.Info("Name", "My Folder"), initialList[1])
        assertEquals(ListItem.Info("Type", "Folder"), initialList[2])

        // The "Modified" row has a timestamp that is ever changing, for now we just assert that the
        // row is there and has the label "Modified". Given this data is static, this should be a
        // sufficient assertion.
        val modifiedRow = initialList[3] as ListItem.Info
        assertEquals("Modified", modifiedRow.label)

        // Items exists but only with a placeholder.
        assertEquals(ListItem.Info("Items", "--"), initialList[4])

        // Debug info is synchronously added so just compare 1 of the items.
        assertEquals(ListItem.Header("Debug Info"), initialList[5])
        assertEquals(
            ListItem.Info("User ID", UserId.CURRENT_USER.identifier.toString()),
            initialList[6],
        )

        testDispatcher.scheduler.advanceUntilIdle()
        val updatedList = viewModel.items.value

        // Should have Header, Name, Type, Modified, Items.
        assertEquals(5 + DEBUG_ITEM_COUNT, updatedList.size)

        // Items should not have changed position from the initial list (i.e. the Debug info should
        // have been pushed down the list.
        assertEquals(ListItem.Info("Items", "3"), updatedList[4])
    }

    @Test
    fun testPartialFile() {
        val doc =
            DocumentInfo().apply {
                documentId = ""
                displayName = "downloading.tmp"
                mimeType = "application/octet-stream"
                size = 500
                lastModified = 1234567890L
                flags = DocumentsContract.Document.FLAG_PARTIAL
                summary = "OriginalFilename.pdf"
                userId = UserId.DEFAULT_USER
            }

        val viewModel = GetInfoViewModel(application, doc, lookup, true)
        val list = viewModel.items.value

        // Header, Name, Type, Size, Modified, Summary
        assertEquals(6 + DEBUG_ITEM_COUNT, list.size)
        assertEquals(ListItem.Info("Summary", "OriginalFilename.pdf"), list[5])
    }

    @Test
    fun testNoLastModified() {
        val doc =
            DocumentInfo().apply {
                displayName = "test.pdf"
                mimeType = "application/pdf"
                size = 100
                lastModified = -1
                userId = UserId.DEFAULT_USER
            }

        val viewModel = GetInfoViewModel(application, doc, lookup, true)
        val list = viewModel.items.value

        // Header, Name, Type, Size. No Modified.
        assertEquals(4 + DEBUG_ITEM_COUNT, list.size)
        assertEquals("Size", (list[3] as ListItem.Info).label)
    }

    companion object {
        // Constant for the number of debug items added (Header + 5 infos + 17 flags).
        const val DEBUG_ITEM_COUNT = 23
    }
}
