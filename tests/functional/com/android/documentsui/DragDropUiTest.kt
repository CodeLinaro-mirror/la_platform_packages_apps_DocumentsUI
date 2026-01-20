/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.test.filters.LargeTest
import com.android.documentsui.StubProvider.ROOT_1_ID
import com.android.documentsui.bots.openRoot
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@LargeTest
class DragDropUiTest : ActivityTestJunit4<FilesActivity>() {

    @get:Rule val testFilesRule = TestFilesRule()

    @Test
    fun testDragAndDropToDifferentRoot_fileIsCopied() {
        assumeTrue("skip drag and drop test for DrawerLayout", !bots.main.inDrawerLayout())

        // Root_0 is selected by default.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)

        val src = bots.directory.findDocument(TestFilesRule.FILE_NAME_1)
        val dst = bots.roots.findRoot(ROOT_1_ID)

        bots.gesture.dragAndDrop(src, dst)

        // The drop will trigger a copy because it's to a different root so the original file still
        // exists and is selected when use_material3 flag is on.
        if (isUseMaterial3FlagEnabled()) {
            bots.directory.assertSelection(1)
        }

        // Go to the new root and assert the file was copied.
        openRoot(context!!, ROOT_1_ID, activityLayoutId)
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1)
    }

    @Test
    fun testDragAndDropToBrokenRoot_failToDrop() {
        assumeTrue("skip drag and drop test for DrawerLayout", !bots.main.inDrawerLayout())

        // Root_0 is selected by default.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)

        val src = bots.directory.findDocument(TestFilesRule.FILE_NAME_1)
        val dst = bots.roots.findRoot("Broken Root Doc")

        bots.gesture.dragAndDrop(src, dst)

        // The drop will fail because the destination root is broken but the selection remains when
        // use_material3 flag is on.
        if (isUseMaterial3FlagEnabled()) {
            bots.directory.assertSelection(1)
        }

        // Go to the new root and assert the file was not copied.
        openRoot(context!!, "Broken Root Doc", activityLayoutId)
        bots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1)
    }
}
