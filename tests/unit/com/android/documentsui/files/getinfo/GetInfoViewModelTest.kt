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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    // Runs the main test methods using a StandardTestDispatcher. This will allow for the usage of
    // methods like `first()` to suspend to enable the ioTestDispatcher to eagerly evaluate any
    // outstanding flows.
    private val testDispatcher = StandardTestDispatcher()

    // Use an UnconfinedTestDispatcher here to ensure the work performed in the ViewModel in the
    // background are done eagerly. This removes any guesswork on the final state, all flows emit
    // their values synchronously when evaluated.
    private val ioTestDispatcher = UnconfinedTestDispatcher(testDispatcher.scheduler)

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
        `when`(resources.getString(R.string.debug_stream_types)).thenReturn("Stream types")

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
     * Builds up an expected list based on the items of the actual list and expectations. This is
     * primarily used to ensure the error message on the equality assertion is descriptive.
     */
    private fun buildExpectedList(
        actualList: List<ListItem>,
        expectedSize: Int,
        vararg expectations: ExpectedItem,
    ): List<ListItem> {
        return List(expectedSize) { index ->
            val actualItem = actualList.getOrNull(index)
            val expectation = expectations.firstOrNull { it.order == index }

            when (expectation) {
                // No expectation on this item index, return the actual item.
                null -> actualItem

                // Exact match expectation, return the expectation.
                is ExpectedItem.Exact -> expectation.expectedItem

                // Partial match expectation, return the actualItem if the labels match, otherwise
                // return "<value ignored>" to ensure the assertion fails this row and the error
                // message is descriptive.
                is ExpectedItem.InfoLabel -> {
                    if (actualItem is ListItem.Info && actualItem.label == expectation.label) {
                        actualItem
                    } else {
                        // Create a "Target" item that will definitely cause a mismatch in the diff.
                        // We use a dummy value because we only cared about the label.
                        ListItem.Info(expectation.label, "<value ignored>")
                    }
                }
            }!!
        }
    }

    /** Wait for the expectations to be correct, otherwise fail with an assertion. */
    private suspend fun waitAndAssertOrderedItems(
        viewModel: GetInfoViewModel,
        expectedSize: Int,
        vararg expectations: ExpectedItem,
    ) {
        try {
            val matchedList =
                viewModel.items.first { list ->
                    if (list.size != expectedSize) return@first false
                    val expectedList = buildExpectedList(list, expectedSize, *expectations)
                    list == expectedList
                }

            val expectedList = buildExpectedList(matchedList, expectedSize, *expectations)
            assertEquals(expectedList, matchedList)
        } catch (e: TimeoutCancellationException) {
            // In the event the `first()` call timed out, let's rebuild the last known state so that
            // the error messages can be much more useful.
            val actualList = viewModel.items.value
            val expectedList = buildExpectedList(actualList, expectedSize, *expectations)

            assertEquals("Timed out waiting for expected state.", expectedList, actualList)
        }
    }

    @Test
    fun testStandardFile_WithDebug() =
        runTest(testDispatcher) {
            val authority = "com.example.authority"
            val documentId = "testId"
            val derivedUri = DocumentsContract.buildDocumentUri(authority, documentId)
            val doc =
                DocumentInfo().apply {
                    this.derivedUri = derivedUri
                    this.authority = authority
                    this.documentId = documentId
                    displayName = "test.pdf"
                    mimeType = "application/pdf"
                    size = 1024 * 1024 * 10
                    lastModified = 1234567890L
                    userId = UserId.DEFAULT_USER
                }

            val streamTypesProvider =
                object : MockContentProvider() {
                    override fun getStreamTypes(
                        url: Uri,
                        mimeTypeFilter: String,
                    ): Array<out String?> {
                        return arrayOf("fake/type")
                    }
                }
            contentResolver.addProvider(authority, streamTypesProvider)

            val viewModel = GetInfoViewModel(application, doc, lookup, true, ioTestDispatcher)
            waitAndAssertOrderedItems(
                viewModel,
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
                ExpectedItem.Exact(
                    5 + DEBUG_ITEM_COUNT - 1,
                    ListItem.Info("Stream types", "[fake/type]"),
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

            val viewModel = GetInfoViewModel(application, doc, lookup, false, ioTestDispatcher)
            waitAndAssertOrderedItems(
                viewModel,
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

            val viewModel = GetInfoViewModel(application, doc, lookup, false, ioTestDispatcher)
            waitAndAssertOrderedItems(
                viewModel,
                5,
                ExpectedItem.Exact(0, ListItem.Header("General info")),
                ExpectedItem.Exact(1, ListItem.Info("Name", "My Folder")),
                ExpectedItem.Exact(2, ListItem.Info("Type", "Folder")),
                ExpectedItem.InfoLabel(3, "Modified"),
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

            val viewModel = GetInfoViewModel(application, doc, lookup, false, ioTestDispatcher)
            waitAndAssertOrderedItems(
                viewModel,
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

            val viewModel = GetInfoViewModel(application, doc, lookup, false, ioTestDispatcher)
            waitAndAssertOrderedItems(viewModel, 4, ExpectedItem.InfoLabel(3, "Size"))
        }

    companion object {
        // Constant for the number of debug items added (Header + 5 infos + 17 flags + 1 async).
        const val DEBUG_ITEM_COUNT = 24
    }
}
