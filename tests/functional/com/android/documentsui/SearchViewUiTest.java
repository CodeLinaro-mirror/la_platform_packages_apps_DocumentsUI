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

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static org.junit.Assert.assertFalse;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class SearchViewUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
      super.setUp();
      initTestFiles();
      // Drawer interferes with a lot of search action; going to try to close any opened ones
      bots.roots.closeDrawer();

      // wait for a file to be present in default dir.
      bots.directory.waitForDocument(fileName1);
    }

    @After
    public void tearDown() throws Exception {
        // manually close activity to avoid SearchFragment show when Activity close. ref b/142840883
        device.waitForIdle();
        device.pressBack();
        device.pressBack();
        device.pressBack();
        super.tearDown();
    }

    private void assertDefaultContentOfTestDir0() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(fileName1);
        bots.directory.waitForDocument(fileName2);
        bots.directory.waitForDocument(dirName1);
        bots.directory.waitForDocument(fileNameNoRename);
        bots.directory.assertDocumentsCount(4);
    }

    private void assertDefaultContentOfTestDir1() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(fileName3);
        bots.directory.waitForDocument(fileName4);
        bots.directory.assertDocumentsCount(2);
    }

    @Test
    public void testSearchIconVisible() throws Exception {
        // The default root (root 0) supports search
        bots.search.assertInputExists(false);
        bots.search.assertIconVisible(true);
    }

    @Test
    @HugeLongTest
    public void testSearchIconHidden() throws Exception {
        bots.roots.openRoot(ROOT_1_ID);  // root 1 doesn't support search

        bots.search.assertIconVisible(false);
        bots.search.assertInputExists(false);
    }

    @Test
    public void testSearchView_ExpandsOnClick() throws Exception {
        bots.search.clickIcon();
        device.waitForIdle();

        bots.search.assertInputExists(true);
        bots.search.assertInputFocused(true);

        // FIXME: Matchers fail the not-present check if we've ever clicked this.
        // bots.search.assertIconVisible(false);
    }

    @Test
    @RequiresFlagsDisabled({FLAG_USE_MATERIAL3}) // Enable when b/397315793 is fixed.
    public void testSearchView_ShouldHideOptionMenuOnExpanding() throws Exception {
        bots.search.clickIcon();
        device.waitForIdle();

        bots.search.assertInputExists(true);
        bots.search.assertInputFocused(true);
        device.waitForIdle();

        assertFalse(bots.menu.hasMenuItem("Grid view"));
        assertFalse(bots.menu.hasMenuItem("List view"));
        assertFalse(bots.menu.hasMenuItemByDesc("More options"));
    }

    @Test
    public void testSearchView_CollapsesOnBack() throws Exception {
        bots.search.clickIcon();
        device.pressBack();

        bots.search.assertIconVisible(true);
        bots.search.assertInputExists(false);
    }

    @Test
    public void testSearchFragment_DismissedOnCloseAfterCancel() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("query text");

        // Cancel search
        device.pressBack();
        device.waitForIdle();

        // Close search
        device.pressBack();
        device.waitForIdle();

        bots.search.assertIconVisible(true);
        bots.search.assertInputExists(false);
        bots.search.assertSearchHistoryVisible(false);
    }

    @Test
    public void testSearchView_ClearsTextOnBack() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("file2");

        device.pressBack();
        device.pressBack();

        // Wait for a file in the default directory to be listed.
        bots.directory.waitForDocument(dirName1);

        bots.search.assertIconVisible(true);
        bots.search.assertInputExists(false);
    }

    @Test
    public void testSearchView_ClearsSearchOnBack() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("file1");
        bots.keyboard.pressEnter();
        device.waitForIdle();

        device.pressBack();

        bots.search.assertIconVisible(true);
        bots.search.assertInputExists(false);
    }

    @Test
    public void testSearchView_ClearsAutoSearchOnBack() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("chocolate");
        //Wait for auto search result, it should be no results and show holder message.
        bots.directory.waitForHolderMessage();

        device.pressBack();
        device.pressBack();

        bots.search.assertIconVisible(true);
        bots.search.assertInputExists(false);
    }

    @Test
    public void testSearchView_StateAfterSearch() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("file1");
        bots.keyboard.pressEnter();
        device.waitForIdle();

        bots.search.assertInputEquals("file1");
    }

    @Test
    public void testSearch_ResultsFound() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("file1");
        bots.keyboard.pressEnter();

        bots.directory.assertDocumentsCountOnList(true, 2);
        bots.directory.assertDocumentsPresent(fileName1, fileName2);
    }

    @Test
    public void testSearch_NoResults() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("chocolate");

        bots.keyboard.pressEnter();

        String msg = String.valueOf(context.getString(R.string.no_results));
        bots.directory.assertPlaceholderMessageText(String.format(msg, "TEST_ROOT_0"));
    }

    @Suppress
    public void testSearchResultsFound_ClearsOnBack() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText(fileName1);

        bots.keyboard.pressEnter();
        device.pressBack();
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testSearchNoResults_ClearsOnBack() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("chocolate bunny");

        bots.keyboard.pressEnter();
        device.pressBack();
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testSearchResultsFound_ClearsOnDirectoryChange() throws Exception {
        // Skipping this test for phones since currently there's no way to open the drawer on
        // phones after doing a search (it's a back button instead of a hamburger button)
        if (!bots.main.inFixedLayout()) {
            return;
        }

        bots.search.clickIcon();

        bots.search.setInputText(fileName1);

        bots.keyboard.pressEnter();

        bots.roots.openRoot(ROOT_1_ID);
        device.waitForIdle();
        assertDefaultContentOfTestDir1();

        bots.roots.openRoot(ROOT_0_ID);
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Test
    public void testSearchHistory_showAfterSearchViewClear() throws Exception {
        bots.search.clickIcon();
        bots.search.setInputText("chocolate");

        bots.keyboard.pressEnter();
        device.waitForIdle();

        bots.search.clickSearchViewClearButton();
        device.waitForIdle();

        bots.search.assertInputExists(true);
        bots.search.assertInputFocused(true);
        bots.search.assertSearchHistoryVisible(true);
    }

    @Test
    public void testSearchView_focusClearedAfterSelectingSearchHistory() throws Exception {
        String queryText = "history";
        bots.search.clickIcon();
        bots.search.setInputText(queryText);
        bots.keyboard.pressEnter();
        device.waitForIdle();

        bots.search.clickSearchViewClearButton();
        device.waitForIdle();
        bots.search.assertInputFocused(true);
        bots.search.assertSearchHistoryVisible(true);

        bots.search.clickSearchHistory(queryText);
        bots.search.assertInputFocused(false);
        bots.search.assertSearchHistoryVisible(false);
    }
}
