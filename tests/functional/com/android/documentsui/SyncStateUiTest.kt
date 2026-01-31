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

import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract.Document
import android.provider.Flags.FLAG_ENABLE_SYNC_STATE
import androidx.test.filters.SdkSuppress
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.filters.HugeLongTest
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.google.common.collect.Lists
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
@RequiresFlagsEnabled(FLAG_ENABLE_SYNC_STATE)
@EnableFlags(Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3)
@HugeLongTest
class SyncStateUiTest : ActivityTestJunit4<FilesActivity>() {
    @JvmField @Parameterized.Parameter(0) var isListView: Boolean = false
    @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()
    private lateinit var cloudProviderDocsHelper: DocumentsProviderHelper
    private val LONG_TICK_VISIBLE_DURATION_MS = 10000
    private val SHORT_TICK_VISIBLE_DURATION_MS = 100

    override fun setupTestingRoots() {
        super.setupTestingRoots()
        cloudProviderDocsHelper =
            DocumentsProviderHelper(
                userId,
                TestCloudProvider.AUTHORITY,
                context,
                TestCloudProvider.AUTHORITY,
            )
        val cloudRoot = cloudProviderDocsHelper.getRoot(TestCloudProvider.ROOT_ID)
        this.initialRoot = cloudRoot
    }

    override fun setUp() {
        super.setUp()
        if (isListView) {
            bots.main.switchToListMode()
        } else {
            bots.main.switchToGridMode()
        }
    }

    // ----- Banner tests -----

    @Test
    fun testBanner_isShownWhenOffline() {
        setIsOnline(true)

        bots.main.assertOfflineBannerDoesNotExist()

        setIsOnline(false)

        bots.main.assertOfflineBannerIsVisible()
    }

    // ----- Inline sync state icon tests -----

    @Test
    fun testNullSyncState_noIcons() {
        setIsOnline(true)

        cloudProviderDocsHelper?.nullifySyncState(TestCloudProvider.DOC_ID_0)

        // No icons should be shown online.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)

        setIsOnline(false)

        // No icons should be shown offline and the document should remain enabled.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)
    }

    @Test
    fun testAvailableLocallyState_noIcons() {
        setIsOnline(true)

        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // No icons should be shown online.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)

        setIsOnline(false)

        // No icons should be shown offline and the document should remain enabled.
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentEnabled(TestCloudProvider.DISPLAY_NAME_0)
    }

    @Test
    fun testNoAvailableLocallyState_downloadIconOnline_disabledOffline() {
        setIsOnline(true)

        // No sync state flags are set.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // A download icon should be shown when online.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.download_icon,
        )

        setIsOnline(false)

        // The document should be disabled when offline.
        bots.directory.assertDocumentDisabled(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)

        // Set a sync state flag that is not "available locally".
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES,
        )

        // The document should still be disabled and no icons should be shown.
        bots.directory.assertDocumentDisabled(TestCloudProvider.DISPLAY_NAME_0)
        bots.directory.assertDocumentSyncIconsNotVisible(TestCloudProvider.DISPLAY_NAME_0)
    }

    @Test
    fun testNoAvailableLocallyState_butDirectory_downloadIconNotShownOnline_enabledOffline() {
        setIsOnline(true)

        // The "available locally" sync state flag is not set.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DIR_ID, 0)

        // Directories don't display the download icon when online.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DIR_DISPLAY_NAME,
            R.id.download_icon,
        )

        setIsOnline(false)

        // The document should be enabled when offline.
        bots.directory.assertDocumentEnabled(TestCloudProvider.DIR_DISPLAY_NAME)
    }

    @Test
    fun testNoAvailableLocallyState_butVirtual_downloadIconNotShownOnline_enabledOffline() {
        setIsOnline(true)

        // The "available locally" sync state flag is not set.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.VIRTUAL_ID, 0)

        // Virtual files don't display the download icon when online.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.VIRTUAL_DISPLAY_NAME,
            R.id.download_icon,
        )

        setIsOnline(false)

        // The document should be enabled when offline.
        bots.directory.assertDocumentEnabled(TestCloudProvider.VIRTUAL_DISPLAY_NAME)
    }

    @Test
    fun testLocalChangesState_uploadIcon() {
        setIsOnline(true)

        // Set the "local changes" state as well as the "available locally" state to prevent the
        // file becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )
    }

    @Test
    fun testUploadErrorState_errorIcon() {
        setIsOnline(true)

        // Set the "upload error" state as well as the "available locally" state to prevent the file
        // becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_ERROR or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )
    }

    @Test
    fun testDownloadErrorState_errorIcon() {
        setIsOnline(true)

        // Set the "download error" state as well as the "available locally" state to prevent the
        // file becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_ERROR or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.sync_error_icon,
        )
    }

    @Test
    fun testUploadInProgressState_progressIcon() {
        setIsOnline(true)

        // Set the "upload progress" state as well as the "available locally" state to prevent the
        // file becoming disabled offline.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_PROGRESS or Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        setIsOnline(false)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
    }

    @Test
    fun testUploadEndsWithAvailableLocally_progressIcon_thenTickIcon() {
        // Set a long tick time out to ensure that the test sees the tick.
        setTickVisibleDuration(LONG_TICK_VISIBLE_DURATION_MS)
        setIsOnline(true)

        // Set the "upload in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_UPLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End upload with available locally state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // The progress icon should be replaced with a tick.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDownloadEndsWithAvailableLocally_progressIcon_thenTickIcon() {
        // Set a long tick time out to ensure that the test sees the tick.
        setTickVisibleDuration(LONG_TICK_VISIBLE_DURATION_MS)
        setIsOnline(true)

        // Set the "download in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End download with available locally state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // The progress icon should be replaced with a tick.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDownloadEndsWithAvailableLocally_tickIconDisappears() {
        // Set a short tick time out to ensure that the test sees the tick disappear.
        setTickVisibleDuration(SHORT_TICK_VISIBLE_DURATION_MS)
        setIsOnline(true)

        // Set the "download in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End download with available locally state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // The tick should disappear after a short time.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDownloadEndsWithNoFlags_progressIcon_thenDownloadIcon() {
        setIsOnline(true)

        // Set the "download in progress" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )

        // End download in "needs download" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // The progress icon should be replaced with a download icon, rather than a tick.
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.download_icon,
        )
    }

    @Test
    fun testDownloadEndsWithNoFlags_iconsNotShownOnUnrelatedDocuments() {
        setIsOnline(true)

        // Set the "download in progress" state for doc0.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )

        // A progress icon should appear only on doc0.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            android.R.id.progress,
        )

        // End download.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // A tick should only be shown on doc0.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.progress_tick_icon,
        )
        bots.directory.assertObjectsEventuallyHiddenOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            R.id.progress_tick_icon,
        )
    }

    @Test
    fun testDifferentDocumentsCanShowDifferentIcons() {
        setIsOnline(true)

        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_1,
            Document.SYNC_STATE_FLAG_UPLOAD_PROGRESS,
        )
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.VIRTUAL_ID,
            Document.SYNC_STATE_FLAG_DOWNLOAD_ERROR,
        )
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DIR_ID,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES,
        )

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.download_icon,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.VIRTUAL_DISPLAY_NAME,
            R.id.sync_error_icon,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DIR_DISPLAY_NAME,
            R.id.upload_icon,
        )

        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS,
        )
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_1, 0)

        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            android.R.id.progress,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_1,
            R.id.download_icon,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.VIRTUAL_DISPLAY_NAME,
            R.id.sync_error_icon,
        )
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DIR_DISPLAY_NAME,
            R.id.upload_icon,
        )
    }

    @Test
    fun testIconStillShownWhenNavigateOutAndBackIntoRoot() {
        setIsOnline(true)

        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_LOCAL_CHANGES,
        )

        // The upload icon should appear.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )

        // Navigate out of TestCloudProvider and back in.
        bots.roots.openRoot(StubProvider.ROOT_1_ID)
        bots.roots.openRoot(TestCloudProvider.NAME)

        // The upload icon should still be present.
        bots.directory.assertObjectsEventuallyAppearOnDocument(
            TestCloudProvider.DISPLAY_NAME_0,
            R.id.upload_icon,
        )
    }

    // ----- Menu tests -----
    @Test
    fun testCxtMenu_availableLocallyState_contentRequiredActionsEnabledOffline() {
        setIsOnline(true)

        // Set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the items that require content to be available are visible and enabled.
        val contentRequiredContextMenuItems =
            intArrayOf(
                R.string.menu_open_with,
                R.string.menu_share,
                R.string.menu_copy_to_clipboard,
            )
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredContextMenuItems)

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the menu items are still visible and enabled offline.
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredContextMenuItems)
    }

    @Test
    fun testActionMenu_availableLocallyState_contentRequiredActionsEnabledOffline() {
        setIsOnline(true)

        // Set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(
            TestCloudProvider.DOC_ID_0,
            Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY,
        )

        // Open the action menu. Selecting the item shows the toolbar action menu items. Opening the
        // overflow menu shows the non-toolbar menu items. Check the toolbar action menu items
        // before opening the overflow menu as they are not queryable when the menu is opened in
        // front.
        bots.directory.selectDocument(TestCloudProvider.DISPLAY_NAME_0, 1)
        // Check that the toolbar action menu items that require content to be available are visible
        // and enabled.
        val contentRequiredToolbarActionMenuItems = intArrayOf(R.id.action_menu_share)
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(*contentRequiredToolbarActionMenuItems)

        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        val contentRequiredListActionMenuItems =
            if (bots.main.isUseCopyCutFlow) {
                intArrayOf(
                    R.string.menu_open_with,
                    R.string.menu_inspect,
                    R.string.menu_copy_to_clipboard,
                )
            } else {
                intArrayOf(R.string.menu_open_with, R.string.menu_inspect, R.string.menu_copy)
            }
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredListActionMenuItems)

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the action menu and check that the menu items are still visible and enabled
        // offline.
        bots.directory.selectDocument(TestCloudProvider.DISPLAY_NAME_0, 1)
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(*contentRequiredToolbarActionMenuItems)

        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredListActionMenuItems)
    }

    @Test
    fun testCxtMenu_noAvailableLocallyState_contentRequiredActionsDisabledOffline() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the items that require content to be available are visible and enabled.
        val contentRequiredContextMenuItems =
            intArrayOf(
                R.string.menu_open_with,
                R.string.menu_share,
                R.string.menu_copy_to_clipboard,
            )
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredContextMenuItems)

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DISPLAY_NAME_0)

        // Check that the menu items are still visible but those that require content required are
        // disabled offline.
        bots.menu.assertListMenuItemsVisibleAndDisabled(*contentRequiredContextMenuItems)
    }

    @Test
    fun testActionMenu_noAvailableLocallyState_contentRequiredActionsDisabledOffline() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DOC_ID_0, 0)

        // Open the action menu. Selecting the item shows the toolbar action menu items. Opening the
        // overflow menu shows the non-toolbar menu items. Check the toolbar action menu items
        // before opening the overflow menu as they are not queryable when the menu is opened in
        // front.
        bots.directory.selectDocument(TestCloudProvider.DISPLAY_NAME_0, 1)
        // Check that the toolbar action menu items that require content to be available are visible
        // and enabled.
        val contentRequiredToolbarActionMenuItems = intArrayOf(R.id.action_menu_share)
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(*contentRequiredToolbarActionMenuItems)

        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        val contentRequiredListActionMenuItems =
            if (bots.main.isUseCopyCutFlow) {
                intArrayOf(R.string.menu_open_with, R.string.menu_copy_to_clipboard)
            } else {
                intArrayOf(R.string.menu_open_with, R.string.menu_copy)
            }
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredListActionMenuItems)

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the action menu and check that the menu items are still visible but those that
        // require content required are disabled offline.
        bots.directory.selectDocument(TestCloudProvider.DISPLAY_NAME_0, 1)
        bots.menu.assertToolbarMenuItemsVisibleAndDisabled(*contentRequiredToolbarActionMenuItems)

        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndDisabled(*contentRequiredListActionMenuItems)
    }

    @Test
    fun testCxtMenu_noAvailableLocallyState_andFolder_contentRequiredActionsDisabled_openEnabled() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DIR_ID, 0)

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DIR_DISPLAY_NAME)

        // Check that the items that require content to be available are visible and enabled.
        val openContextMenuItems = intArrayOf(R.string.menu_open_in_new_window)
        val contentRequiredContextMenuItemsExcludingOpen =
            intArrayOf(R.string.menu_copy_to_clipboard)
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *openContextMenuItems,
            *contentRequiredContextMenuItemsExcludingOpen,
        )

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.DIR_DISPLAY_NAME)

        // Check that the menu items are still visible but those that require content required are
        // disabled offline. However, open menu items are an exception for folders and should remain
        // enabled.
        bots.menu.assertListMenuItemsVisibleAndEnabled(*openContextMenuItems)
        bots.menu.assertListMenuItemsVisibleAndDisabled(
            *contentRequiredContextMenuItemsExcludingOpen
        )
    }

    @Test
    fun testActionMenu_noAvailableLocallyState_folder_contentRequiredActionsEnabled_openEnabled() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.DIR_ID, 0)

        // Open the action menu.
        bots.directory.selectDocument(TestCloudProvider.DIR_DISPLAY_NAME, 1)
        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        val openListActionMenuItems = intArrayOf(R.string.menu_open_in_new_window)
        val contentRequiredListActionMenuItems =
            if (bots.main.isUseCopyCutFlow) {
                intArrayOf(R.string.menu_copy_to_clipboard)
            } else {
                intArrayOf(R.string.menu_copy)
            }
        bots.menu.assertListMenuItemsVisibleAndEnabled(
            *openListActionMenuItems,
            *contentRequiredListActionMenuItems,
        )

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the action menu and check that the menu items are still visible but those that
        // require content required are disabled offline. However, open menu items are an exception
        // for folders and should remain enabled.
        bots.directory.selectDocument(TestCloudProvider.DIR_DISPLAY_NAME, 1)
        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndEnabled(*openListActionMenuItems)
        bots.menu.assertListMenuItemsVisibleAndDisabled(*contentRequiredListActionMenuItems)
    }

    @Test
    fun testCxtMenu_noAvailableLocallyState_butVirtual_contentRequiredActionsEnabledOffline() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.VIRTUAL_ID, 0)

        // Open the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME)

        // Check that the items that require content to be available are visible and enabled.
        val contentRequiredContextMenuItems =
            intArrayOf(
                R.string.menu_open_with,
                R.string.menu_share,
                R.string.menu_copy_to_clipboard,
            )
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredContextMenuItems)

        // Dismiss the context menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the context menu.
        bots.directory.rightClickDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME)

        // Check that the menu items are still visible and enabled offline.
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredContextMenuItems)
    }

    @Test
    fun testActionMenu_noAvailableLocallyState_butVirtual_contentRequiredActionsEnabledOffline() {
        setIsOnline(true)

        // Do not set the "available locally" state.
        cloudProviderDocsHelper?.setSyncState(TestCloudProvider.VIRTUAL_ID, 0)

        // Open the action menu. Selecting the item shows the toolbar action menu items. Opening the
        // overflow menu shows the non-toolbar menu items. Check the toolbar action menu items
        // before opening the overflow menu as they are not queryable when the menu is opened in
        // front.
        bots.directory.selectDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME, 1)
        // Check that the toolbar action menu items that require content to be available are visible
        // and enabled.
        val contentRequiredToolbarActionMenuItems = intArrayOf(R.id.action_menu_share)
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(*contentRequiredToolbarActionMenuItems)

        bots.main.openOverflowMenu()
        // Check that the overflow menu items that require content to be available are visible and
        // enabled.
        val contentRequiredListActionMenuItems =
            if (bots.main.isUseCopyCutFlow) {
                intArrayOf(R.string.menu_open_with, R.string.menu_copy_to_clipboard)
            } else {
                intArrayOf(R.string.menu_open_with, R.string.menu_copy)
            }
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredListActionMenuItems)

        // Dismiss the action menu.
        device!!.pressBack()

        setIsOnline(false)

        // Reopen the action menu and check that the menu items are still visible and enabled
        // offline.
        bots.directory.selectDocument(TestCloudProvider.VIRTUAL_DISPLAY_NAME, 1)
        bots.menu.assertToolbarMenuItemsVisibleAndEnabled(*contentRequiredToolbarActionMenuItems)

        bots.main.openOverflowMenu()
        bots.menu.assertListMenuItemsVisibleAndEnabled(*contentRequiredListActionMenuItems)
    }

    companion object {
        /**
         * Provides the test parameters for the parameterized test. This allows each test method to
         * be run with two configurations: one with list view and the other with grid view.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "isListView={0}")
        fun data(): Iterable<*> {
            return Lists.newArrayList<Boolean?>(true, false)
        }
    }
}
