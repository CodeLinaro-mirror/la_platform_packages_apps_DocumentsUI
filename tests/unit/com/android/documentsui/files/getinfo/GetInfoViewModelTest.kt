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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule private val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    /**
     * A helper class that matches either a ListItem exactly or just the label of an ListItem.Info.
     */
    sealed class ExpectedItem {
        abstract val order: Int

        abstract fun matches(actual: ListItem): Boolean

        abstract fun contentToString(): String

        /** Strict match for label and value. */
        data class Exact(override val order: Int, val expectedItem: ListItem) : ExpectedItem() {
            override fun matches(actual: ListItem): Boolean = actual == expectedItem

            override fun contentToString() = expectedItem.toString()
        }

        /** Partial match of just the label for a ListItem.Info. */
        data class InfoLabel(override val order: Int, val label: String) : ExpectedItem() {
            override fun matches(actual: ListItem): Boolean {
                return actual is ListItem.Info && actual.label == label
            }

            override fun contentToString() = "ListItem(label=$label, value=any())"
        }
    }

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

    /**
     * The list of items is quite large so we don't always test the whole list. This is a helper
     * function to ensure the various expectations are matched (either exactly or partially) and
     * that their order is correct.
     */
    private fun assertOrderedItems(
        actualList: List<ListItem>,
        expectedSize: Int,
        vararg expected: ExpectedItem,
    ) {
        assertEquals(expectedSize, actualList.size)
        expected.forEach { expectation ->
            val index = expectation.order
            val actualItem = actualList.getOrNull(index)
            assertNotNull("Item at $index expected, but is missing", actualItem)
            assertTrue(
                "Expected ${expectation.contentToString()} doesn't match $actualItem",
                expectation.matches(actualItem!!),
            )
        }
    }

    /**
     * A class that wraps the test Channel with a take() method that abstracts the semantics into a
     * helpful function.
     */
    class GetInfoTestEmitter(
        private val testEmitter: Channel<List<ListItem>>,
        private val scheduler: TestCoroutineScheduler,
    ) {
        /** Takes the oldest value (FIFO order) from the channel. */
        suspend fun take(): List<ListItem> {
            // This runs the supplied scheduler to the next suspension point. This allows for the
            // flows to run until an emission is made and that can be taken from the Channel.
            scheduler.runCurrent()
            return testEmitter.receive()
        }
    }

    /**
     * The ViewModel emits values as they are asynchronously retrieved. To ensure we retain an
     * ordered list of events to assert on, construct a channel that buffers the events so we can
     * inspect them one by one.
     */
    private fun TestScope.setupTestEmitter(viewModel: GetInfoViewModel): GetInfoTestEmitter {
        val emissions = Channel<List<ListItem>>(Channel.UNLIMITED)

        // To ensure the flows are started, it requires a subscription (i.e. collect must have been
        // called) so we fake that here.
        backgroundScope.launch(testDispatcher) {
            viewModel.items.collect { list -> emissions.send(list) }
        }

        return GetInfoTestEmitter(emissions, testScheduler)
    }

    @Test
    fun testStandardFile_WithDebug() =
        runTest(testDispatcher) {
            val doc =
                DocumentInfo().apply {
                    documentId = "testId"
                    displayName = "test.pdf"
                    mimeType = "application/pdf"
                    size = 1024 * 1024 * 10
                    lastModified = 1234567890L
                    userId = UserId.DEFAULT_USER
                }

            val viewModel = GetInfoViewModel(application, doc, lookup, true, testDispatcher)
            val testEmitter = setupTestEmitter(viewModel)

            val initialList = testEmitter.take()
            assertOrderedItems(
                initialList,
                5 + DEBUG_ITEM_COUNT,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "test.pdf")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "File Type")),
                ExpectedItem.InfoLabel(3, "Size"),
                ExpectedItem.InfoLabel(4, "Modified"),
                ExpectedItem.Exact(5, ListItem.Header("Debug Info")),
                ExpectedItem.Exact(
                    6,
                    ListItem.Info("User ID", UserId.CURRENT_USER.identifier.toString()),
                ),
            )
        }

    @Test
    fun testStandardFile_NoDebug() =
        runTest(testDispatcher) {
            val doc =
                DocumentInfo().apply {
                    documentId = "testId"
                    displayName = "test.pdf"
                    mimeType = "application/pdf"
                    size = 1024 * 1024 * 10
                    lastModified = 1234567890L
                    userId = UserId.DEFAULT_USER
                }

            val viewModel = GetInfoViewModel(application, doc, lookup, false, testDispatcher)
            val testEmitter = setupTestEmitter(viewModel)

            val initialList = testEmitter.take()
            assertOrderedItems(
                initialList,
                5,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "test.pdf")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "File Type")),
                ExpectedItem.InfoLabel(3, "Size"),
                ExpectedItem.InfoLabel(4, "Modified"),
            )
        }

    @Test
    fun testDirectory() =
        runTest(testDispatcher) {
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

            // Setup a StandardTestDispatcher for the flows in the ViewModel. The test scope uses an
            // `UnconfinedTestDispatcher` to allow for all the flows to run eagerly. For the
            // ViewModel we can't follow this pattern as eager execution means the intermediate
            // steps don't get emitted (the flow is ran immediately).
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = GetInfoViewModel(application, doc, lookup, false, ioDispatcher)
            val testEmitter = setupTestEmitter(viewModel)

            // The first list has no "Items" row as it sends it off asynchronously to calculate.
            val initialList = testEmitter.take()
            assertOrderedItems(
                initialList,
                4,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "My Folder")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "Folder")),
                ExpectedItem.InfoLabel(3, "Modified"),
            )

            // The second list now has an "Items" row with just the placeholder text.
            val placeholderList = testEmitter.take()
            assertOrderedItems(
                placeholderList,
                5,
                ExpectedItem.Exact(4, ListItem.Info("Items", "--")),
            )

            // The third list has the "Items" row visible and populated.
            val itemsCountList = testEmitter.take()
            assertOrderedItems(
                itemsCountList,
                5,
                ExpectedItem.Exact(4, ListItem.Info("Items", "3")),
            )
        }

    @Test
    fun testPartialFile() =
        runTest(testDispatcher) {
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

            val viewModel = GetInfoViewModel(application, doc, lookup, false, testDispatcher)
            val testEmitter = setupTestEmitter(viewModel)

            val initialList = testEmitter.take()
            assertOrderedItems(
                initialList,
                6,
                ExpectedItem.Exact(5, ListItem.Info("Summary", "OriginalFilename.pdf")),
            )
        }

    @Test
    fun testNoLastModified() =
        runTest(testDispatcher) {
            val doc =
                DocumentInfo().apply {
                    displayName = "test.pdf"
                    mimeType = "application/pdf"
                    size = 100
                    lastModified = -1
                    userId = UserId.DEFAULT_USER
                }

            val viewModel = GetInfoViewModel(application, doc, lookup, false, testDispatcher)
            val testEmitter = setupTestEmitter(viewModel)

            val initialList = testEmitter.take()
            assertOrderedItems(initialList, 4, ExpectedItem.InfoLabel(3, "Size"))
        }

    companion object {
        // Constant for the number of debug items added (Header + 5 infos + 17 flags).
        const val DEBUG_ITEM_COUNT = 23
    }
}
