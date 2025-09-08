/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.ExternalStorageProviderTestFilesRule;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@LargeTest
public class RecentsViewUiTest extends ActivityTestJunit4<FilesActivity> {
    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final ExternalStorageProviderTestFilesRule mTestFilesRule =
            new ExternalStorageProviderTestFilesRule();

    /**
     * Ensure that Recents shows all file types if the flag is enabled.
     *
     * Note that this feature requires the enable_media_documents_provider_allfiles_root flag to be
     * enabled in MediaProvider. We cannot force that from these tests. Don't force our flag on (as
     * the test will fail if the MediaProvider feature is disabled), but do test the feature if the
     * flag is on (we will ramp the two flags simultaneously).
     */
    @Test
    @RequiresFlagsEnabled({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS, FLAG_USE_MATERIAL3,
            FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentsContainsAllFileTypes() throws Exception {
        bots.roots.openRoot("Recent");

        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_2, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_4, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_5, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_6, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_7, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_8, true).exists());
    }

    @Test
    @DisableFlags({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS})
    public void testRecentsDoesNotContainAllFileTypes() throws Exception {
        bots.roots.openRoot("Recent");

        // When the flag is disabled, DocumentsUI Recents should continue using the old combination
        // of multiple MediaDocumentsProvider and DownloadStorageProvider roots, which means that
        // some file types will not be available.
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_2, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_4, true).exists());
        assertFalse(bots.directory.findDocument(TestFilesRule.FILE_NAME_5, true).exists());
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_6, true).exists());
        assertFalse(bots.directory.findDocument(TestFilesRule.FILE_NAME_7, true).exists());
        assertFalse(bots.directory.findDocument(TestFilesRule.FILE_NAME_8, true).exists());
    }

    /**
     * When the feature is enabled (see comment on testRecentsContainsAllFileTypes() for detail on
     * when this is true), we should now see a "Rename" option on files in Recents.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS, FLAG_USE_MATERIAL3,
            FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentFilesShowRenameOptions() throws Exception {
        bots.roots.openRoot("Recent");
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_2, true).exists());
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_2);

        final Map<String, Boolean> menuItems = new HashMap<>();
        menuItems.put("Rename", true);

        bots.menu.assertPresentMenuItems(menuItems);
    }

    /**
     * When the feature is disabled, files from MediaDocumentsProvider in Recents should not have
     * the "Rename" option (as files from MediaDocumentsProvider's images, video and audio Roots
     * don't support renaming).
     */
    @Test
    @DisableFlags({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS})
    public void testRecentFilesDoNotShowRenameOptions() throws Exception {
        bots.roots.openRoot("Recent");

        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_2, true).exists());
        bots.directory.rightClickDocument(TestFilesRule.FILE_NAME_2);

        final Map<String, Boolean> menuItems = new HashMap<>();
        menuItems.put("Rename", false);
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_ALLFILES_ROOT_FOR_RECENTS, FLAG_USE_MATERIAL3,
            FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchInRecents() throws Exception {
        // Create a new, randomly named file so that we can ensure (as much as possible) that
        // search here is really working (as opposed to the test finding the contents of the
        // Recents view _before_ searching) by just finding this file only. This is created in
        // a temporary directory that is cleaned up by TestFilesRule. Use a MIME type that is only
        // found by the new Recents implementation.
        final String testFileName = mTestFilesRule.createRandomFile("application/octet-stream");

        bots.roots.openRoot("Recent");
        bots.search.expand();
        bots.search.setInputText(testFileName);
        bots.keyboard.pressEnter();

        bots.directory.assertDocumentsCount(1);
        bots.directory.waitForDocument(testFileName);
    }
}
