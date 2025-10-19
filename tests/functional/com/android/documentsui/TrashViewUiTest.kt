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
package com.android.documentsui

import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API
import android.provider.MediaStore
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.android.documentsui.StubProvider.ROOT_0_ID
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.testing.TestProvidersAccess.TRASH_ROOT
import com.android.documentsui.util.VersionUtils
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for verifying the functionality of the Trash view, including moving items to trash,
 * viewing them, and using the "Empty Trash" feature.
 */
@LargeTest
@RequiresFlagsEnabled(FLAG_ENABLE_DOCUMENTS_TRASH_API)
@EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.FLAG_USE_MATERIAL3)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
class TrashViewUiTest : ActivityTestJunit4<FilesActivity>() {

    @get:Rule val setFlags = OverrideFlagsRule()

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val mTestFilesRule: TestFilesRule =
        TestFilesRule().createTestFiles { docsHelper: DocumentsProviderHelper ->
            createTempFiles(docsHelper)
        }

    @Before
    @Throws(Exception::class)
    fun setUpTest() {
        // Skip test if the platform SDK is not newer than Android Baklava (SDK 36).
        // The Trash feature under test relies on DocumentsContract APIs introduced in the
        // Android release after Baklava (SDK 36).
        // As DocumentsUI is a Mainline module, it's subject to MTS testing, which runs on
        // older Android base builds to verify backward compatibility. However, this specific
        // Trash feature lacks backward compatibility with platforms at or below Baklava.
        // This assumption prevents failures when the test runs on an older base OS
        // without the necessary APIs.
        assumeTrue(VersionUtils.isGreaterThanB())

        if (SdkLevel.isAtLeastR()) {
            MediaStore.waitForIdle(context!!.contentResolver)
        }

        setNotificationAccess(true)
    }

    /**
     * Creates a temporary directory and populates it with test files.
     *
     * @param docsHelper The helper for interacting with the document provider.
     */
    private fun createTempFiles(docsHelper: DocumentsProviderHelper) {
        val root = docsHelper.getRoot(ROOT_0_ID)
        val dirUri = docsHelper.createFolder(root.documentId, TestFilesRule.DIR_NAME_1)
        val prefix = "file-${System.nanoTime()}-"

        for (i in 0 until TEST_FILE_COUNT) {
            val fileName = "$prefix$i.txt"
            docsHelper.createDocument(dirUri, "text/plain", fileName)
        }
    }

    /**
     * Verifies that when files are moved to the trash, they correctly appear in the Trash view and
     * the "Empty Trash" banner is displayed.
     */
    @Test
    fun testMoveToTrashDisplaysItemsInTrashView() {
        val trashedFileNames = moveFilesToTrash()

        bots.roots.openRoot(TRASH_ROOT.title)

        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())
    }

    /**
     * Verifies that clicking the "Empty trash now" button and confirming the dialog permanently
     * deletes all items from the trash.
     */
    @Test
    fun testEmptyTrashPermanentlyDeletesAllItems() {
        val trashedFileNames = moveFilesToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed files are visible in the UI.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Then, ensure the "Empty Trash" banner is visible.
        bots.main.assertEmptyTrashBannerIsVisible()

        // Trigger the "Empty Trash" flow and confirm.
        bots.main.clickEmptyTrashNowButton()
        device!!.waitForIdle()
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the previously trashed files are now gone.
        bots.directory.assertDocumentsAbsent(*trashedFileNames.toTypedArray())
    }

    /** Tests that permanently deleting selected items from the Trash view works correctly. */
    @Test
    fun testTrashPermanentlyDeleteItem() {
        val trashedFileNames = moveFilesToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed files are visible in the UI.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Select the first two files to permanently delete.
        val filesToPermanentlyDelete = trashedFileNames.take(2)
        filesToPermanentlyDelete.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        // Click the permanent delete button in the toolbar.
        bots.main.clickDelete()
        device!!.waitForIdle()

        // Confirm the permanent delete dialog.
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the selected files are now gone.
        bots.directory.assertDocumentsAbsent(*filesToPermanentlyDelete.toTypedArray())

        // Verify that the remaining files are still in the trash.
        val remainingFiles = trashedFileNames.drop(2)
        bots.directory.assertDocumentsPresent(*remainingFiles.toTypedArray())
    }

    /** Tests permanently deleting items from within a trashed folder. */
    @Test
    fun testPermanentlyDeleteItemsFromTrashedFolder() {
        val trashedFolderName = moveFolderToTrash()
        bots.roots.openRoot(TRASH_ROOT.title)

        // First, check that the trashed folder is visible in the UI.
        bots.directory.assertDocumentsPresent(trashedFolderName)

        // Open the trashed folder.
        bots.directory.openDocument(trashedFolderName)
        device!!.waitForIdle()

        val dirDocumentId =
            mDocsHelper!!
                .getAllTrashItems()
                .first { it.displayName == trashedFolderName }
                .documentId
        val documents = mDocsHelper!!.listChildren(dirDocumentId)
        val trashedFileNames = documents.map { it.displayName }
        assert(trashedFileNames.size == TEST_FILE_COUNT) {
            "Expected $TEST_FILE_COUNT files in the folder, but found ${trashedFileNames.size}"
        }

        // Check that the files are visible inside the folder.
        bots.directory.assertDocumentsPresent(*trashedFileNames.toTypedArray())

        // Select the first two files to permanently delete.
        val filesToPermanentlyDelete = trashedFileNames.take(2)
        filesToPermanentlyDelete.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        // Click the permanent delete button in the toolbar.
        bots.main.clickDelete()
        device!!.waitForIdle()

        // Confirm the permanent delete dialog.
        bots.main.clickDialogOkButton(false)
        device!!.waitForIdle()

        // Verify that the selected files are now gone from the folder.
        bots.directory.assertDocumentsAbsent(*filesToPermanentlyDelete.toTypedArray())

        // Verify that the remaining files are still in the folder.
        val remainingFiles = trashedFileNames.drop(2)
        bots.directory.assertDocumentsPresent(*remainingFiles.toTypedArray())

        // Go back to the trash root and verify the folder is still there.
        device!!.pressBack()
        device!!.waitForIdle()
        bots.directory.assertDocumentsPresent(trashedFolderName)
    }

    /**
     * Navigates into the test directory, selects a subset of files, and moves them to the trash.
     *
     * @return A list of filenames that were moved to the trash.
     */
    @Throws(Exception::class)
    private fun moveFilesToTrash(): List<String> {
        bots.roots.openRoot(StubProvider.ROOT_0_ID)
        device!!.waitForIdle()
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)

        val rootInfo = mDocsHelper!!.getRoot(ROOT_0_ID)
        val dirDocumentId =
            mDocsHelper!!.findDocument(rootInfo.documentId, TestFilesRule.DIR_NAME_1)!!.documentId
        val documents = mDocsHelper!!.listChildren(dirDocumentId)
        assert(documents.size == TEST_FILE_COUNT) { "Documents size should be $TEST_FILE_COUNT" }

        val filesToTrash = documents.take(3).map { it.displayName }

        bots.directory.assertDocumentsPresent(*filesToTrash.toTypedArray())

        filesToTrash.forEachIndexed { index, fileName ->
            bots.directory.selectDocument(fileName, index + 1)
        }

        bots.main.clickToolbarItem(R.id.action_menu_move_to_trash)
        device!!.waitForIdle()

        return filesToTrash
    }

    /**
     * Moves the test directory and all its contents to the trash.
     *
     * @return The name of the folder moved to the trash.
     */
    @Throws(Exception::class)
    private fun moveFolderToTrash(): String {
        bots.roots.openRoot(ROOT_0_ID)
        device!!.waitForIdle()

        // Select and move the entire directory to the trash.
        bots.directory.selectDocument(TestFilesRule.DIR_NAME_1, 1)
        bots.main.clickToolbarItem(R.id.action_menu_move_to_trash)

        bots.directory.assertDocumentsAbsent(TestFilesRule.DIR_NAME_1)

        device!!.waitForIdle()

        return TestFilesRule.DIR_NAME_1
    }

    companion object {
        private const val TEST_FILE_COUNT = 10
    }
}
