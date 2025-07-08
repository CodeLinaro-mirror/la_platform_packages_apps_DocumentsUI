/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.flags.Flags.FLAG_DESKTOP_FILE_HANDLING_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO;

import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;

import java.io.InputStream;

@LargeTest
public class ArchiveUiTest extends ActivityTestJunit4<FilesActivity> {
    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    @Test
    @RequiresFlagsDisabled({FLAG_ZIP_NG_RO})
    public void browseArchiveViaDefaultAction() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.waitForDocument("archive.zip");
        bots.directory.openDocument("archive.zip");
        bots.directory.waitForDocument("file1.txt");
        bots.directory.assertDocumentsVisible("dir1", "dir2", "file1.txt");
        bots.directory.openDocument("dir1");
        bots.directory.waitForDocument("cherries.txt");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO})
    public void extractArchiveViaDefaultAction() throws Exception {
        createArchiveInRootDir0();
        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.waitForDocument("archive.zip");
        bots.directory.openDocument("archive.zip");
        assertExtractedArchive();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO})
    public void extractArchiveViaContextMenu() throws Exception {
        createArchiveInRootDir0();
        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.waitForDocument("archive.zip");
        bots.directory.rightClickDocument("archive.zip");
        bots.menu.clickMenuItem("Extract here");
        assertExtractedArchive();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO})
    public void browseArchiveViaContextMenu() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.waitForDocument("archive.zip");
        bots.directory.rightClickDocument("archive.zip");
        bots.menu.clickMenuItem("Browse");
        bots.directory.waitForDocument("file1.txt");
        bots.directory.assertDocumentsVisible("dir1", "dir2", "file1.txt");
        bots.directory.openDocument("dir1");
        bots.directory.waitForDocument("cherries.txt");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_DESKTOP_FILE_HANDLING_RO})
    public void openArchiveViaContextMenu() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.waitForDocument("archive.zip");
        bots.directory.rightClickDocument("archive.zip");
        bots.menu.clickMenuItem("Open");
        bots.directory.waitForDocument("file1.txt");
        bots.directory.assertDocumentsVisible("dir1", "dir2", "file1.txt");
        bots.directory.openDocument("dir1");
        bots.directory.waitForDocument("cherries.txt");
    }

    @Test
    @RequiresFlagsDisabled({FLAG_ZIP_NG_RO})
    public void browseInvalidArchiveViaDefaultAction() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.waitForDocument("broken.zip");
        bots.directory.openDocument("broken.zip");
        bots.directory.waitAndAssertPlaceholderMessageText(context.getString(R.string.empty));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO})
    public void browseInvalidArchiveViaContextMenu() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.waitForDocument("broken.zip");
        bots.directory.rightClickDocument("broken.zip");
        bots.menu.clickMenuItem("Browse");
        bots.directory.waitAndAssertPlaceholderMessageText(context.getString(R.string.empty));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_DESKTOP_FILE_HANDLING_RO})
    public void openInvalidArchiveViaContextMenu() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.waitForDocument("broken.zip");
        bots.directory.rightClickDocument("broken.zip");
        bots.menu.clickMenuItem("Open");
        bots.directory.waitAndAssertPlaceholderMessageText(context.getString(R.string.empty));
    }

    /** Creates a ZIP file in the test root 0, which is a writable folder. */
    private void createArchiveInRootDir0() throws Exception {
        assert mDocsHelper != null;
        assert rootDir0 != null;
        final Uri uri = mDocsHelper.createDocument(rootDir0, "application/zip", "archive.zip");
        try (InputStream it = getInstrumentation().getContext().getAssets().open(
                "archives/zip/hello.zip")) {
            mDocsHelper.writeDocument(uri, it);
        }
    }

    private void assertExtractedArchive() throws UiObjectNotFoundException {
        bots.directory.waitForDocument("archive");
        bots.directory.openDocument("archive");
        bots.directory.waitForDocument("hello");
        bots.directory.openDocument("hello");
        bots.directory.waitForDocument("hello.txt");
        bots.directory.waitForDocument("hello2.txt");
        bots.directory.waitForDocument("inside_folder");
        bots.directory.openDocument("inside_folder");
        bots.directory.waitForDocument("hello_insside.txt");
    }
}
