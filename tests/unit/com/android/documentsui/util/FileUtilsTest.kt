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
package com.android.documentsui.util

import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.testing.TestPackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@SmallTest
@RunWith(AndroidJUnit4::class)
class FileUtilsTest {
    val testPackageManager: TestPackageManager = TestPackageManager.create()

    @Before
    fun setUp() {
        testPackageManager.queryIntentActivitiesResults.put(
            "image/png",
            listOf(ResolveInfo(), ResolveInfo()),
        )
    }

    @Test
    fun testCountOpeningApps() {
        val doc = Mockito.mock(DocumentInfo::class.java)
        doc.mimeType = "image/png"
        `when`(doc.documentUri).thenReturn(Uri.parse("content://com.example.test/test.png"))

        assertEquals(FileUtils.countOpeningApps(doc, testPackageManager), 2)
    }

    @Test
    fun testSanitizeNameCreateDirectory() {
        val name = "File.txt"
        // Expect the name to remain the same.
        assertEquals(FileUtils.sanitizeName(name), name)
    }

    @Test
    fun testSanitizeNameCreateDirectoryStripTrailingSpaces() {
        val name = "   File.txt   "
        // Expect only the trailing spaces to be trimmed.
        assertEquals(FileUtils.sanitizeName(name), "   File.txt")
    }

    @Test
    fun testSanitizeNameCreateDirectoryEmptyName() {
        val expectedError: FileUtils.InvalidNameError =
            FileUtils.InvalidNameError("Invalid name error: ''", R.string.add_folder_name_error)
        val exception =
            assertThrows(FileUtils.InvalidNameError::class.java) { FileUtils.sanitizeName("") }

        // Assert exception properties
        assertEquals(expectedError.message, exception.message)
        assertEquals(expectedError.mResource, exception.mResource)
    }

    @Test
    fun testSanitizeNameCreateDirectoryStrippedToEmptyName() {
        val name = "   "
        val expectedError: FileUtils.InvalidNameError =
            FileUtils.InvalidNameError("Invalid name error: ''", R.string.add_folder_name_error)
        val exception =
            assertThrows(FileUtils.InvalidNameError::class.java) { FileUtils.sanitizeName(name) }

        // Assert exception properties
        assertEquals(expectedError.message, exception.message)
        assertEquals(expectedError.mResource, exception.mResource)
    }
}
