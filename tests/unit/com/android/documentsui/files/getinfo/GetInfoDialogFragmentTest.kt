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

import android.content.ContentProvider
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
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
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
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
class GetInfoDialogFragmentTest {

    @Mock private lateinit var context: Context
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var lookup: Lookup<String, String>

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
    private lateinit var contentResolver: MockContentResolver

    @Before
    fun setUp() {
        openMocks(this)

        // Use a real Configuration object because it is final and cannot be mocked.
        val configuration = Configuration()
        configuration.setLocales(LocaleList(Locale.US))

        // Mock resources and configuration for Formatter.
        `when`(context.resources).thenReturn(resources)
        `when`(resources.configuration).thenReturn(configuration)

        // Prevent NullPointerException when Formatter asks for internal string resources.
        `when`(resources.getString(anyInt())).thenReturn("MockString")
        `when`(resources.getString(anyInt(), any())).thenReturn("MockString")
        `when`(resources.getText(anyInt())).thenReturn("MockString")

        // Mock content resolver for DateFormat.
        contentResolver = MockContentResolver()
        contentResolver.addProvider(Settings.AUTHORITY, settingsProvider)
        `when`(context.contentResolver).thenReturn(contentResolver)
        `when`(context.userId).thenReturn(Process.myUserHandle().identifier)

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

        // Mock lookup to return folder type and a default for the remaining types.
        `when`(lookup.lookup(any())).thenReturn("File Type")
        `when`(lookup.lookup(eq(DocumentsContract.Document.MIME_TYPE_DIR))).thenReturn("Folder")
    }

    @Test
    fun testCreateDataList_StandardFile() {
        val doc =
            DocumentInfo().apply {
                displayName = "test.pdf"
                mimeType = "application/pdf"
                size = 1024 * 1024 * 10
                lastModified = 1234567890L
            }

        val list = GetInfoDialogFragment.createDataList(context, doc, lookup)

        // Should have Header, Name, TYpe, Size, Modified.
        assertEquals(5, list.size)
        assertEquals(ListItem.Header("General info"), list[0])
        assertEquals(ListItem.Info("Name", "test.pdf"), list[1])
        assertEquals(ListItem.Info("Type", "File Type"), list[2])
        assertEquals("Size", (list[3] as ListItem.Info).label)
        assertEquals("Modified", (list[4] as ListItem.Info).label)
    }

    @Test
    fun testCreateDataList_Directory() {
        val doc =
            DocumentInfo().apply {
                displayName = "My Folder"
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR
                size = 0
                lastModified = 1234567890L
            }

        val list = GetInfoDialogFragment.createDataList(context, doc, lookup)

        // Should have Header, Name, Type, Modified. No Size.
        assertEquals(4, list.size)
        assertEquals(ListItem.Header("General info"), list[0])
        assertEquals(ListItem.Info("Name", "My Folder"), list[1])
        assertEquals(ListItem.Info("Type", "Folder"), list[2])
        assertEquals("Modified", (list[3] as ListItem.Info).label)
    }

    @Test
    fun testCreateDataList_PartialFile() {
        val doc =
            DocumentInfo().apply {
                displayName = "downloading.tmp"
                mimeType = "application/octet-stream"
                size = 500
                lastModified = 1234567890L
                flags = DocumentsContract.Document.FLAG_PARTIAL
                summary = "OriginalFilename.pdf"
            }

        val list = GetInfoDialogFragment.createDataList(context, doc, lookup)

        // Header, Name, Type, Size, Modified, Summary
        assertEquals(6, list.size)
        assertEquals(ListItem.Info("Summary", "OriginalFilename.pdf"), list[5])
    }

    @Test
    fun testCreateDataList_NoLastModified() {
        val doc =
            DocumentInfo().apply {
                displayName = "test.pdf"
                mimeType = "application/pdf"
                size = 100
                lastModified = -1
            }

        val list = GetInfoDialogFragment.createDataList(context, doc, lookup)

        // Header, Name, Type, Size. No Modified.
        assertEquals(4, list.size)
        assertEquals("Size", (list[3] as ListItem.Info).label)
    }
}
