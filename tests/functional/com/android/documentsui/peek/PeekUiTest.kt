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
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.StubProvider
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.TestFilesRule
import junit.framework.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_PEEK_PREVIEW_RO)
class PeekUiTest : ActivityTestJunit4<FilesActivity?>() {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val testFilesRule: TestFilesRule =
        TestFilesRule()
            .createFileInRoot(StubProvider.ROOT_0_ID, "image.png", "image/png")
            .createFileInRoot(StubProvider.ROOT_0_ID, "file0.log", "text/plain")

    fun validatePeekContents(fileName: String) {
        bots!!.peek.waitForPeekActive()
        bots!!.peek.assertHasTitle(fileName)
    }

    @Test
    @Throws(Exception::class)
    fun testSequentialFilePreview() {
        bots!!.peek.assertPeekHidden()
        bots!!.directory.selectDocument("image.png")
        bots!!.main.clickActionItem("Get info")
        bots!!.peek.waitForPeekActive()
        bots!!.peek.assertHasTitle("image.png")
        bots!!.peek.hide()

        bots!!.directory.selectDocument("file0.log")
        bots!!.main.clickActionItem("Get info")
        bots!!.peek.waitForPeekActive()
        bots!!.peek.assertHasTitle("file0.log")
        bots!!.peek.hide()
    }

    @Test
    @Throws(Exception::class)
    fun testFileCantBeSelectedDuringFilePreview() {
        bots!!.peek.assertPeekHidden()
        // Selecting a document should show the "1 selected" label.
        bots!!.directory.selectDocument("image.png", 1)
        bots!!.main.clickActionItem("Get info")
        bots!!.peek.waitForPeekActive()
        bots!!.peek.assertHasTitle("image.png")
        // The selection should not be possible, the "1 selected" label shouldn't show.
        bots!!.directory.selectDocument("image.png")
        val assertSelectionText = "1 selected"
        val timeout: Long = 1000
        val selectionText: UiObject2? = device!!.wait(
            Until.findObject(By.text(assertSelectionText)),
            timeout
        )
        Assert.assertNull(selectionText)
    }

    @Test
    @Throws(Exception::class)
    fun testRestorePeekActiveState() {
        bots!!.directory.selectDocument("image.png")
        bots!!.main.clickActionItem("Get info")
        validatePeekContents("image.png")

        // Recreate the activity (happens on window resize, for example), and ensure that the
        // preview overlay is still showing.
        mActivityScenario!!.recreate()
        validatePeekContents("image.png")

        bots!!.peek.hide()
        mActivityScenario!!.recreate()
        bots!!.peek.assertPeekHidden()

        bots!!.directory.selectDocument("file0.log")
        bots!!.main.clickActionItem("Get info")
        validatePeekContents("file0.log")
        mActivityScenario!!.recreate()
        validatePeekContents("file0.log")
    }
}
