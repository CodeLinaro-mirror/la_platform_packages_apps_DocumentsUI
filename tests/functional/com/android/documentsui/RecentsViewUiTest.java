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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.documentsui.flags.Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.UiObject;

import com.android.documentsui.bots.DirectoryListBot;
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

    @Test
    @DisableFlags({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testRecentsDoesNotContainEntriesFromAllFilesRootWithSearchV1() throws Exception {
        bots.roots.openRoot("Recent");

        // When SearchV2 is disabled, the old loaders are used: check that they're not picking up
        // anything from the new "all files" root if it's enabled in MediaProvider. If they were,
        // we could see two copies of each file.
        onView(withId(R.id.dir_list))
                .check(
                        matches(
                                DirectoryListBot.withDisplayedFilenameCount(
                                        TestFilesRule.FILE_NAME_2, 1)));
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
        bots.search.doSearch(testFileName);

        bots.directory.waitForDocument(testFileName);
        onView(withId(R.id.dir_list))
                .check(matches(DirectoryListBot.withDisplayedFilenameCount(testFileName, 1)));
    }

    @Test
    @DisableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchInRecentsDoesNotContainEntriesFromAllFilesRootWithSearchV1()
            throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        bots.roots.openRoot("Recent");

        // Even pre-M3, DocsUI can run in large screen or small layout, and the way to activate
        // Search in Recent view differs between the two.
        if (bots.search.getSearchIcon() != null) {
            bots.search.expand();
        } else {
            onView(allOf(withId(R.id.searchbar_title), isDisplayed())).perform(click());
        }

        bots.search.setInputText(testFileName);
        bots.keyboard.pressEnter();

        // When SearchV2 is disabled, the old loaders are used: check that they're not picking up
        // anything from the new "all files" root if it's enabled in MediaProvider. If they were,
        // we could see two copies of the file.
        bots.directory.waitForDocument(testFileName);
        onView(withId(R.id.dir_list))
                .check(matches(DirectoryListBot.withDisplayedFilenameCount(testFileName, 1)));
    }

    /** When using the new Search stack, files in Recents are deletable. */
    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testDeleteFromRecentsWithSearchV2() throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        // Check: the random test file is visible in Recents.
        bots.roots.openRoot("Recent");
        UiObject fileInRecents = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInRecents.exists());

        // Check: the file can be successfully deleted.
        bots.directory.selectDocument(testFileName, 1);
        device.waitForIdle();
        bots.main.clickDelete();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();
        fileInRecents = bots.directory.findDocument(testFileName, true);
        assertFalse(fileInRecents.exists());
    }

    /** When using the new Search stack, files in Recents are movable. */
    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testMoveToInRecentsWithSearchV2() throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        // Check: the random test file is visible in Recents.
        bots.roots.openRoot("Recent");
        UiObject fileInRecents = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInRecents.exists());

        // Check: the random test file is not yet in Downloads.
        bots.roots.openRoot("Downloads");
        UiObject fileInDownloads = bots.directory.findDocument(testFileName, true);
        assertFalse(fileInDownloads.exists());

        // Move the file to Downloads.
        bots.roots.openRoot("Recent");
        bots.directory.selectDocument(testFileName, 1);
        device.waitForIdle();
        bots.main.clickActionbarOverflowItem(context.getResources().getString(R.string.menu_move));
        device.waitForIdle();
        bots.roots.openRoot("Downloads");
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        // Check: the random test file is now in Downloads.
        bots.roots.openRoot("Downloads");
        fileInDownloads = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInDownloads.exists());
    }

    /**
     * When using the old search stack, files in Recents are not movable, because
     * MultiRootDocumentsLoader applies NotMovableMaskCursor to all queries, which dynamically
     * rewrites the Document flags to remove FLAG_SUPPORTS_DELETE, FLAG_SUPPORTS_REMOVE etc.
     */
    @Test
    @DisableFlags({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testMoveToInRecentsWithSearchV1() throws Exception {
        final String testFileNamePrefix = mTestFilesRule.createRandomFile("image/jpeg", "Pictures");
        final String testFileName = testFileNamePrefix.concat(".jpg");

        // Check: the random test file is visible in Recents.
        bots.roots.openRoot("Recent");
        UiObject fileInRecents = bots.directory.findDocument(testFileName, true);
        assertTrue(fileInRecents.exists());

        // Check: the "Move to" item is not visible in the context menu.
        bots.roots.openRoot("Recent");
        bots.directory.selectDocument(testFileName, 1);
        device.waitForIdle();
        bots.main.openOverflowMenu();

        final Map<String, Boolean> menuItems = new HashMap<>();
        menuItems.put(context.getResources().getString(R.string.menu_move), false);
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testPathOfSearchResultInRecents() throws Exception {
        bots.roots.openRoot("Recent");
        device.waitForIdle();

        bots.directory.selectFirstDocument();

        // Check that the breadcrumb path starts with "Recent". We don't know more about the
        // selected file, to perform a more exact comparison.
        onView(withId(R.id.breadcrumb_path_holder)).check(bots.breadcrumb.pathStartsWith("Recent"));
    }
}
