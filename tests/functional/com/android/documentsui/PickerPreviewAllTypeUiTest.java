/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.content.Intent;
import android.provider.DocumentsContract;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;

import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class PickerPreviewAllTypeUiTest extends ActivityTestJunit4<PickActivity> {
    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createFolderInRoot(ROOT_0_ID, TestFilesRule.DIR_NAME_1)
                    .createFolderWithParent(TestFilesRule.DIR_NAME_1, TestFilesRule.CHILD_DIR_1)
                    .createFileInRoot(ROOT_0_ID, "file0.log", "text/plain")
                    .createFileInRoot(ROOT_0_ID, "file1.png", "image/png");

    @Override
    protected void launchActivity() {
        final Intent intent = new Intent(context, PickActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        if (getInitialRoot() != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getInitialRoot().getUri());
        }
        intent.setType("*/*");
        mActivityScenario = ActivityScenario.launch(intent);
    }

    @Test
    public void testPreviewInvisible_directory_gridMode() throws Exception {
        bots.main.switchToGridMode();
        assertTrue(bots.directory.findDocument(TestFilesRule.DIR_NAME_1).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(TestFilesRule.DIR_NAME_1));
    }

    @Test
    public void testPreviewInvisible_directory_listMode() throws Exception {
        bots.main.switchToListMode();
        assertTrue(bots.directory.findDocument(TestFilesRule.DIR_NAME_1).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(TestFilesRule.DIR_NAME_1));
    }

    @Test
    public void testPreviewVisible_allType_girdMode() throws Exception {
        bots.main.switchToGridMode();
        assertTrue(bots.directory.findDocument("file0.log").isEnabled());
        assertTrue(bots.directory.hasDocumentPreview("file0.log"));
        assertTrue(bots.directory.findDocument("file1.png").isEnabled());
        assertTrue(bots.directory.hasDocumentPreview("file1.png"));
    }

    @Test
    public void testPreviewVisible_allType_listMode() throws Exception {
        bots.main.switchToListMode();
        assertTrue(bots.directory.findDocument("file0.log").isEnabled());
        assertTrue(bots.directory.hasDocumentPreview("file0.log"));
        assertTrue(bots.directory.findDocument("file1.png").isEnabled());
        assertTrue(bots.directory.hasDocumentPreview("file1.png"));
    }
}
