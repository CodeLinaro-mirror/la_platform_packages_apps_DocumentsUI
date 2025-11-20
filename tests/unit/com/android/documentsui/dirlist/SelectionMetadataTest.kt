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
package com.android.documentsui.dirlist

import android.content.pm.ResolveInfo
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.ModelId
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestModelRule
import com.android.documentsui.testing.TestPackageManager
import com.android.documentsui.util.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

const val TestAuthority = "com.example.test"
const val TestUserId = 0

@SmallTest
@EnableFlags(Flags.FLAG_DESKTOP_FILE_HANDLING_RO)
@RunWith(AndroidJUnit4::class)
class SelectionMetadataTest {
    companion object {
        const val IS_UNAVAILABLE_FLAG = 13
    }

    val testPackageManager: TestPackageManager = TestPackageManager.create()

    @get:Rule val setFlags = OverrideFlagsRule()

    @get:Rule
    val testModelRule =
        TestModelRule(TestAuthority, TestUserId)
            .createFile("noOpeningApp.pdf", "application/pdf")
            .createFile("oneOpeningApp.txt", "text/plain")
            .createFile("twoOpeningApp.jpg", "image/jpg")
            .createFile("twoOpeningApp.png", "image/png")
            .createFile("unavailableDocument1.png", "image/png", IS_UNAVAILABLE_FLAG)
            .createFile("unavailableDocument2.png", "image/png", IS_UNAVAILABLE_FLAG)

    @Before
    fun setUp() {
        testPackageManager.queryIntentActivitiesResults.put("application/pdf", emptyList())
        testPackageManager.queryIntentActivitiesResults.put("text/plain", listOf(ResolveInfo()))
        testPackageManager.queryIntentActivitiesResults.put(
            "image/jpg",
            listOf(ResolveInfo(), ResolveInfo()),
        )
        testPackageManager.queryIntentActivitiesResults.put(
            "image/png",
            listOf(ResolveInfo(), ResolveInfo()),
        )
    }

    @Test
    fun testHasMultipleOpeningApps_NoSelection() {
        val sm = createSelectionMetadata()

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    fun testHasMultipleOpeningApps_OneSelection_NoOpeningApps() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged("noOpeningApp.pdf", true)

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    fun testHasMultipleOpeningApps_OneSelection_OneOpeningApps() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("oneOpeningApp.txt"), true)

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    fun testHasMultipleOpeningApps_OneSelection_TwoOpeningApps() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("noOpeningApp.pdf"), true)
        sm.onItemStateChanged(makeId("noOpeningApp.pdf"), false)

        assertEquals(sm.hasMultipleOpeningApps(), true)
    }

    @Test
    fun testHasMultipleOpeningApps_TwoSelection() {
        val sm = createSelectionMetadata()
        sm.onItemStateChanged(makeId("twoOpeningApp.jpg"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertEquals(sm.hasMultipleOpeningApps(), false)
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testContainsDocumentsWithUnavailableContent_disabledDocument_cloudFeaturesEnabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("unavailableDocument1.png"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertEquals(sm.containsDocumentsWithUnavailableContent(), true)
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testContainsDocumentsWithUnavailableContent_disabledDocuments_cloudFeaturesEnabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("unavailableDocument1.png"), true)
        sm.onItemStateChanged(makeId("unavailableDocument2.png"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertEquals(sm.containsDocumentsWithUnavailableContent(), true)
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testContainsDocumentsWithUnavailableContent_noDisabledDocuments_cloudFeaturesEnabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertEquals(sm.containsDocumentsWithUnavailableContent(), false)
    }

    @Test
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testContainsDocumentsWithUnavailableContent_cloudFeaturesDisabled() {
        val sm = createSelectionMetadata()

        sm.onItemStateChanged(makeId("unavailableDocument1.png"), true)
        sm.onItemStateChanged(makeId("twoOpeningApp.png"), true)

        assertEquals(sm.containsDocumentsWithUnavailableContent(), false)
    }

    fun makeId(docId: String): String {
        return ModelId.build(UserId.of(TestUserId), TestAuthority, docId)!!
    }

    fun createSelectionMetadata(): SelectionMetadata {
        return SelectionMetadata(
            { modelId -> testModelRule.model.getItem(modelId) },
            { modelId ->
                val doc = testModelRule.model.getDocument(modelId)
                FileUtils.countOpeningApps(doc, testPackageManager)
            },
            { _, flag, _ ->
                // Hack the `flag` to set whether the document is unavailable in this test.
                flag != IS_UNAVAILABLE_FLAG
            },
        )
    }
}
