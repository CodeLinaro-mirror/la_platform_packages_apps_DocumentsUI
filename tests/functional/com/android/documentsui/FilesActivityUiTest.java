/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;
import static com.android.documentsui.base.Providers.ROOT_ID_DEVICE;
import static com.android.documentsui.flags.Flags.FLAG_HIDE_ROOTS_ON_DESKTOP_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static com.google.common.truth.TruthJUnit.assume;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FilesActivityUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createFolderInRoot(ROOT_0_ID, TestFilesRule.DIR_NAME_1)
                    .createFolderWithParent(TestFilesRule.DIR_NAME_1, "ChildDir1")
                    .createFileInRoot(ROOT_0_ID, "file0.log", "text/plain")
                    .createFileInRoot(ROOT_0_ID, "file1.png", "image/png")
                    .createFileInRoot(ROOT_0_ID, "file2.csv", "text/csv")
                    .createFileInRoot(ROOT_0_ID, "anotherFile0.log", "text/plain")
                    .createFileInRoot(ROOT_0_ID, "poodles.text", "text/plain");

    // Recents is a strange meta root that gathers entries from other providers.
    // It is special cased in a variety of ways, which is why we just want
    // to be able to click on it.
    @Test
    public void testClickRecent() throws Exception {
        bots.roots.openRoot("Recent");

        boolean showSearchBar =
                context.getResources().getBoolean(R.bool.show_search_bar);
        if (showSearchBar) {
            bots.main.assertSearchBarShow();
        } else {
            bots.main.assertWindowTitle("Recent");
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_MATERIAL3)
    public void testRecentsSelectionClearsSearchBar() throws Exception {
        assume().that(context.getResources().getBoolean(R.bool.show_search_bar)).isTrue();

        // Create DocumentsProviderHelper to create files in Internal storage.
        DocumentsProviderHelper storageDocsHelper =
                new DocumentsProviderHelper(
                        UserId.DEFAULT_USER, AUTHORITY_STORAGE, context, AUTHORITY_STORAGE);

        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);

        // Create Download folder if it doesn't exist.
        DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");

        if (info == null) {
            ContentResolver cr = context.getContentResolver();
            Uri uri = storageDocsHelper.createFolder(primaryRoot.documentId, "Download");
            info = DocumentInfo.fromUri(cr, uri, UserId.DEFAULT_USER);
        }

        assertTrue(info != null && info.isDirectory());

        // Open up Recents and create a file that should appear.
        bots.roots.openRoot("Recent");
        final String fileName = "Recent.txt";
        storageDocsHelper.createDocument(info.documentId, "text/plain", fileName);
        try {
            bots.directory.waitForDocument(fileName);
            bots.directory.selectDocument(fileName, 1);
        } finally {
            // Unselect the file and remove it.
            bots.directory.selectDocument(fileName);
            bots.roots.openRoot(primaryRoot.title);
            bots.directory.openDocument("Download");

            bots.directory.waitForDocument(fileName);
            bots.directory.selectDocument(fileName, 1);

            bots.main.clickToolbarItem(R.id.action_menu_delete);
            bots.main.clickNonTextDialogOkButton();
            device.waitForIdle();

            bots.directory.findDocument(fileName).waitUntilGone(5000);
            assertFalse(bots.directory.hasDocuments(fileName));
        }
    }

    @Test
    @RequiresFlagsDisabled(FLAG_HIDE_ROOTS_ON_DESKTOP_RO)
    public void testRootClick_SetsWindowTitle() throws Exception {
        bots.roots.openRoot("Images");
        bots.main.assertWindowTitle("Images");
    }

    private void filesListed() throws Exception {
        bots.directory.assertDocumentsPresent("file0.log", "file1.png", "file2.csv");
    }

    @Test
    @RequiresFlagsDisabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testFilesListed() throws Exception {
        filesListed();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testFilesListed_searchV2() throws Exception {
        filesListed();
    }

    private void filesListed_LiveUpdates() throws Exception {
        mTestFilesRule.createFileInRoot(ROOT_0_ID, "yummers/sandwich", "Ham & Cheese.sandwich");

        bots.directory.waitForDocument("Ham & Cheese.sandwich");
        bots.directory.assertDocumentsPresent(
                "file0.log", "file1.png", "file2.csv", "Ham & Cheese.sandwich");
    }

    @Test
    @RequiresFlagsDisabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testFilesList_LiveUpdate() throws Exception {
        filesListed_LiveUpdates();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testFilesList_LiveUpdate_searchV2() throws Exception {
        filesListed_LiveUpdates();
    }

    @Test
    public void testNavigate_byBreadcrumb() throws Exception {
        bots.directory.openDocument(dirName1);
        bots.directory.waitForDocument(childDir1);  // wait for known content
        bots.directory.assertDocumentsPresent(childDir1);

        device.waitForIdle();
        bots.breadcrumb.assertItemsPresent(dirName1, "TEST_ROOT_0");

        bots.breadcrumb.clickItem("TEST_ROOT_0");
        bots.directory.waitForDocument(dirName1);
    }

    @Test
    public void testNavigate_inFixedLayout_whileHasSelection() throws Exception {
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot(mTestFilesRule.getRoot(ROOT_0_ID).title);
            device.waitForIdle();
            bots.directory.selectDocument("file0.log", 1);

            // ensure no exception is thrown while navigating to a different root
            bots.roots.openRoot(mTestFilesRule.getRoot(ROOT_1_ID).title);
        }
    }

    @Test
    public void testNavigationToInspector() throws Exception {
        if(!features.isInspectorEnabled()) {
            return;
        }
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                InspectorActivity.class.getName(), null, false);
        bots.directory.selectDocument("file0.log");
        bots.main.clickActionItem("Get info");
        monitor.waitForActivityWithTimeout(TIMEOUT);
    }

    @Test
    @HugeLongTest
    @RequiresFlagsDisabled(FLAG_HIDE_ROOTS_ON_DESKTOP_RO)
    public void testRootChange_UpdatesSortHeader() throws Exception {

        // switch to separate display modes for two separate roots. Each
        // mode has its own distinct sort header. This should be remembered
        // by files app.
        bots.roots.openRoot("Images");
        bots.main.switchToGridMode();
        bots.roots.openRoot("Videos");
        bots.main.switchToListMode();

        // Now switch back and assert the correct mode sort header mode
        // is restored when we load the root with that display mode.
        bots.roots.openRoot("Images");
        bots.sort.assertHeaderHide();
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot("Videos");
            bots.sort.assertHeaderShow();
        } else {
            bots.roots.openRoot("Videos");
            bots.sort.assertHeaderHide();
        }
    }
}
