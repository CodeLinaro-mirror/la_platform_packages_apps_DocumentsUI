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
package com.android.documentsui.peek

import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.StubProvider
import com.android.documentsui.bots.PeekBot
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import com.android.documentsui.rules.TestFilesRule
import junit.framework.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_PEEK_PREVIEW_RO)
class PeekUiTest : ActivityTestJunit4<FilesActivity?>() {
    @get:Rule val checkFlags = CheckAndForceMaterial3Flag()

    @get:Rule
    val testFilesRule: TestFilesRule =
        TestFilesRule()
            .createFileInRoot(StubProvider.ROOT_0_ID, "image.png", "image/png")
            .createFileInRoot(StubProvider.ROOT_0_ID, "file0.log", "text/plain")

    private lateinit var peekBot: PeekBot

    @Before
    fun setUpTest() {
        peekBot = PeekBot(device!!, context!!, TIMEOUT)
    }

    fun validatePeekContents(fileName: String) {
        peekBot.assertPeekActive()
        peekBot.assertHasTitle(fileName)
    }

    @Test
    @Throws(Exception::class)
    fun testSequentialFilePreview() {
        peekBot.assertPeekHidden()
        bots.directory.selectDocument("image.png", 1)
        bots.main.clickActionItem("Get info")
        validatePeekContents("image.png")
        peekBot.hide()

        bots.directory.selectDocument("file0.log", 1)
        bots.main.clickActionItem("Get info")
        validatePeekContents("file0.log")
        peekBot.hide()
    }

    @Test
    @Throws(Exception::class)
    fun testFileCantBeSelectedDuringFilePreview() {
        peekBot.assertPeekHidden()
        // Selecting a document should show the "1 selected" label.
        bots.directory.selectDocument("image.png", 1)
        bots.main.clickActionItem("Get info")
        validatePeekContents("image.png")
        // The selection should not be possible, the "1 selected" label shouldn't show.
        val selectionHotspot: UiObject2 = bots.directory.findSelectionHotspot("image.png")
        Assert.assertNull(selectionHotspot)
        val assertSelectionText = "1 selected"
        val timeout: Long = 1000
        val selectionText: UiObject2? =
            device!!.wait(Until.findObject(By.text(assertSelectionText)), timeout)
        Assert.assertNull(selectionText)
    }

    @Test
    @Throws(Exception::class)
    fun testPeekRestorationOnConfigurationChange() {
        bots.directory.selectDocument("image.png", 1)
        bots.main.clickActionItem("Get info")
        validatePeekContents("image.png")

        // Recreate the activity to simulate a configuration change (window resize, for example),
        // and ensure that the preview is restored.
        mActivityScenario!!.recreate()
        validatePeekContents("image.png")

        peekBot.hide()
        // Ensure that the Peek overlay isn't showing when the activity gets recreated after the
        // overlay has been hidden.
        mActivityScenario!!.recreate()
        peekBot.assertPeekHidden()

        bots.directory.selectDocument("file0.log", 1)
        bots.main.clickActionItem("Get info")
        validatePeekContents("file0.log")
        // Check Peek's contents when restoring a different preview.
        mActivityScenario!!.recreate()
        validatePeekContents("file0.log")
    }

    @Test
    @Throws(Exception::class)
    fun testNoPreview() {
        bots.directory.selectDocument("file0.log", 1)
        bots.main.clickActionItem("Get info")
        validatePeekContents("file0.log")

        // Use the "No preview available" content description to ensure that the "No preview" shape
        // is showing.
        onView(withContentDescription("No preview available")).check(matches(isDisplayed()))
    }

    @Test
    @Throws(Exception::class)
    fun testMetadataSheet() {
        bots.directory.selectDocument("file0.log", 1)
        bots.main.clickActionItem("Get info")

        // Check the metadata sheet state before and after recreating the activity.
        peekBot.validateMetadataSheetState(true)
        mActivityScenario!!.recreate()
        peekBot.validateMetadataSheetState(true)
    }
}
