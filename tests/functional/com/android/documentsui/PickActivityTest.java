/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUsePeekPreviewFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.annotations.DesktopTest;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;

import androidx.annotation.StringRes;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.bots.Bots;
import com.android.documentsui.bots.EspressoBotsKt;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.ui.TestDialogController;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@LargeTest
@RunWith(Parameterized.class)
public class PickActivityTest {

    private static final String RESULT_EXTRA = "test_result_extra";
    private static final String RESULT_DATA = "123321";

    private UiDevice mDevice;
    private Context mTargetContext;
    private Intent mIntentGetContent;
    private TestDialogController mTestDialogs;
    private TestConfigStore mTestConfigStore;

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                docsHelper.createDocument(
                                        root, "text/plain", TestFilesRule.FILE_NAME_1);
                                docsHelper.createDocument(
                                        root, "image/png", TestFilesRule.FILE_NAME_2);
                            });

    @Rule
    public final ActivityTestRule<PickActivity> mRule =
            new ActivityTestRule<>(PickActivity.class, false, false);

    private Bots mBots = null;

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mIntentGetContent = new Intent(Intent.ACTION_GET_CONTENT);
        mIntentGetContent.addCategory(Intent.CATEGORY_OPENABLE);
        mIntentGetContent.setType("*/*");
        Uri hintUri = DocumentsContract.buildRootUri(AUTHORITY_STORAGE, "primary");
        mIntentGetContent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, hintUri);

        mTestDialogs = new TestDialogController();
        mTestConfigStore = new TestConfigStore();

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;

        mBots =
                new Bots(
                        mDevice,
                        instrumentation.getUiAutomation(),
                        mTargetContext,
                        5000,
                        getRes(R.layout.documents_activity));
    }

    @Test
    public void testOnDocumentPicked() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        pickActivity.mState.configStore = mTestConfigStore;
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            pickActivity.mState.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
        } else {
            pickActivity.mState.canShareAcrossProfile = true;
        }
        pickActivity.onDocumentPicked(doc);
        SystemClock.sleep(3000);

        Instrumentation.ActivityResult result = mRule.getActivityResult();
        assertThat(pickActivity.isFinishing()).isTrue();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        assertThat(result.getResultData().getData()).isEqualTo(doc.getDocumentUri());
    }

    @Test
    public void testOnDocumentPicked_otherUser() {
        if (VersionUtils.isAtLeastR()) {
            DocumentInfo doc = new DocumentInfo();
            doc.userId = TestProvidersAccess.OtherUser.USER_ID;
            doc.authority = "authority";
            doc.documentId = "documentId";

            PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
            pickActivity.mState.configStore = mTestConfigStore;
            if (isPrivateSpaceEnabled) {
                mTestConfigStore.enablePrivateSpaceInPhotoPicker();
                pickActivity.mState.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
                pickActivity.mState.canForwardToProfileIdMap.put(
                        TestProvidersAccess.OtherUser.USER_ID, true);
            } else {
                pickActivity.mState.canShareAcrossProfile = true;
            }
            pickActivity.onDocumentPicked(doc);
            SystemClock.sleep(3000);

            Instrumentation.ActivityResult result = mRule.getActivityResult();
            assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
            assertThat(result.getResultData().getData()).isEqualTo(doc.getDocumentUri());
        }
    }

    @Test
    public void testOnDocumentPicked_otherUserDoesNotReturn() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.OtherUser.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        pickActivity.mState.configStore = mTestConfigStore;
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            pickActivity.mState.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
        } else {
            pickActivity.mState.canShareAcrossProfile = false;
        }
        pickActivity.getInjector().dialogs = mTestDialogs;
        pickActivity.onDocumentPicked(doc);
        SystemClock.sleep(3000);

        assertThat(pickActivity.isFinishing()).isFalse();
        mTestDialogs.assertActionNotAllowedShown();
    }

    @Test
    public void testOptionMenuWorksWhileOptionSelected() throws UiObjectNotFoundException {
        // Launch the PickActivity using `GET_CONTENT` action, and navigate to test root.
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // Switch to list mode and select the test document.
        mBots.main.switchToListMode();
        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Open the overflow menu and assert that the Sort by menu option is there.
        mBots.main.openOverflowMenu();
        mBots.menu.hasMenuItem("Sort by...");
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testOptionMenuWorksWhileOptionSelected_M3Enabled()
            throws UiObjectNotFoundException {
        // Launch the PickActivity using `GET_CONTENT` action, and navigate to test root.
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // Switch to list mode and select the test document.
        mBots.main.switchToListMode();
        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Open the overflow menu and assert that the expected menu options are there.
        mBots.main.openOverflowMenu();
        mBots.menu.hasMenuItem(mTargetContext.getString(R.string.menu_rename));
        mBots.menu.hasMenuItem(mTargetContext.getString(R.string.menu_inspect));
        final @StringRes int zipMenuId =
                isZipNgFlagEnabled() ? R.string.menu_zip : R.string.menu_compress;
        mBots.menu.hasMenuItem(mTargetContext.getString(zipMenuId));
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_rename() throws UiObjectNotFoundException {
        // Launch the PickActivity using `GET_CONTENT` action, and navigate to test root.
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // Right click FILE_NAME_1 to trigger rename.
        mBots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        mBots.menu.clickMenuItem(mTargetContext.getString(R.string.menu_rename));
        mDevice.waitForIdle();

        // Input a new name.
        final String newName = "renamed inside picker";
        mBots.main.setDialogText(newName);
        mBots.keyboard.pressEnter();

        // Assert the old file is gone, the new file appears.
        mBots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
        mBots.directory.assertDocumentsPresent(newName);
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_getInfo() throws UiObjectNotFoundException {
        // Launch the PickActivity using `GET_CONTENT` action, and navigate to test root.
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // Right click FILE_NAME_1 to trigger get info.
        mBots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        mBots.menu.clickMenuItem(mTargetContext.getString(R.string.menu_inspect));
        mDevice.waitForIdle();

        // Assert inspector activity is shown.
        if (!isUsePeekPreviewFlagEnabled()) {
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(
                            InspectorActivity.class.getName(), null, false);
            monitor.waitForActivityWithTimeout(5000);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_USE_MATERIAL3)
    public void testContextMenu_delete() throws UiObjectNotFoundException {
        // Launch the PickActivity using `GET_CONTENT` action, and navigate to test root.
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // Right click FILE_NAME_1 to trigger delete.
        mBots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        mBots.menu.clickMenuItem(mTargetContext.getString(R.string.menu_permanently_delete));
        mBots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        mDevice.waitForIdle();

        // Assert the file is gone.
        mBots.directory.assertDocumentsAbsent(TestFilesRule.FILE_NAME_1);
    }

    @Test
    public void testContextMenu_zip() throws UiObjectNotFoundException {
        // We can't use EnableFlags here because: @EnableFlags(FLAG_USE_MATERIAL3) will override
        // the M3 flag to true thus the isZipNgFlagEnabled() is also true, but the override doesn't
        // affect the CompressJob service, which internally use the flag name to control the zipped
        // file name, which eventually results in: the ZipNg flag is ON in this test context, but
        // it's OFF in the CompressJob service.
        assumeTrue(
                "Skipping test: the use_material3 flag is OFF on the test device.",
                isUseMaterial3FlagEnabled());

        // Launch the PickActivity using `GET_CONTENT` action, and navigate to test root.
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // Right click FILE_NAME_1 to trigger zip.
        mBots.directory.rightClickDocument(TestFilesRule.FILE_NAME_1);
        mBots.menu.clickMenuItem(mTargetContext.getString(R.string.menu_zip));
        mDevice.waitForIdle();

        // Assert a zip file is created.
        mBots.directory.assertDocumentsPresent(TestFilesRule.FILE_NAME_1);
        final String zipFileName = isZipNgFlagEnabled() ? "file1.zip" : "file1.log.zip";
        mBots.directory.assertDocumentsPresent(zipFileName);
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_ActionOpenDocument_SingleFile()
            throws UiObjectNotFoundException {

        Intent intentOpenDocument = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intentOpenDocument.addCategory(Intent.CATEGORY_OPENABLE);
        intentOpenDocument.setType("*/*");
        PickActivity pickActivity = mRule.launchActivity(intentOpenDocument);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // There should be a Cancel (button2) and Select (button1) button.
        boolean showPickerCancelButton =
                mTargetContext.getResources().getBoolean(R.bool.show_picker_cancel_button);
        if (showPickerCancelButton) {
            mBots.picker.checkCancelButtonDisplayed();
            mBots.picker.checkCancelButtonEnabled();
        }
        mBots.picker.checkPickButtonDisplayed();
        // The Select button should be disabled since there are no selected files.

        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // The Select button should be enabled since there is a selected file.
        mBots.picker.checkPickButtonEnabled();

        // Click the Select button to pick the selected file.
        mBots.picker.clickPickButton();
        SystemClock.sleep(3000);

        // Check that the file was picked.
        Instrumentation.ActivityResult result = mRule.getActivityResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        assertThat(result.getResultData().getData()).isEqualTo(
                mTestFilesRule.getUriInRoot(ROOT_0_ID, TestFilesRule.FILE_NAME_1));

        // Check that the activity is finishing.
        assertThat(pickActivity.isFinishing()).isTrue();
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_ActionGetContent_MultiFiles() throws Exception {
        // Allow multiple files to be selected.
        mIntentGetContent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);

        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // There should be a Cancel (button2) and Select (button1) button.
        boolean showPickerCancelButton =
                mTargetContext.getResources().getBoolean(R.bool.show_picker_cancel_button);
        if (showPickerCancelButton) {
            mBots.picker.checkCancelButtonDisplayed();
            mBots.picker.checkCancelButtonEnabled();
        }
        mBots.picker.checkPickButtonDisplayed();
        // The Select button should be disabled since there are no selected files.
        mBots.picker.checkPickButtonDisabled();

        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 2);

        // The Select button should be enabled since there are selected files.
        mBots.picker.checkPickButtonEnabled();

        mBots.directory.clearSelection();

        // The Select button should be disabled since there are no selected files.
        mBots.picker.checkPickButtonDisabled();

        // Select the files again and click the Select button to pick the selected files.
        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_2, 2);
        mBots.picker.checkPickButtonEnabled();
        mBots.picker.clickPickButton();
        SystemClock.sleep(3000);

        // Check that the files were picked.
        Instrumentation.ActivityResult result = mRule.getActivityResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        ClipData clipData = result.getResultData().getClipData();
        assertNotNull(clipData);
        assertEquals(clipData.getItemCount(), 2);
        assertEquals(mTestFilesRule.getUriInRoot(ROOT_0_ID, TestFilesRule.FILE_NAME_1),
                clipData.getItemAt(0).getUri());
        assertEquals(mTestFilesRule.getUriInRoot(ROOT_0_ID, TestFilesRule.FILE_NAME_2),
                clipData.getItemAt(1).getUri());

        // Check that the activity is finishing.
        assertThat(pickActivity.isFinishing()).isTrue();
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @EnableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_ClickCancel() throws UiObjectNotFoundException {
        assume().that(mTargetContext.getResources().getBoolean(R.bool.show_picker_cancel_button))
                .isTrue();

        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);

        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        // There should be a Cancel (button2) and Select (button1) button.
        mBots.picker.checkCancelButtonDisplayed();
        mBots.picker.checkCancelButtonEnabled();
        mBots.picker.checkPickButtonDisplayed();

        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Click Cancel.
        mBots.picker.clickCancelButton();

        // Check that the files weren't picked.
        Instrumentation.ActivityResult result = mRule.getActivityResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_CANCELED);

        // Check that the activity is finishing.
        assertThat(pickActivity.isFinishing()).isTrue();
    }

    @DesktopTest(cujs = {"b/434068578"})
    @Test
    @DisableFlags({Flags.FLAG_USE_MATERIAL3})
    public void testPickFilesFragment_FlagDisabled() throws UiObjectNotFoundException {
        PickActivity pickActivity = mRule.launchActivity(mIntentGetContent);
        EspressoBotsKt.openRoot(mTargetContext, ROOT_0_ID, pickActivity.getLayoutId());

        mBots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // The Cancel (button2) and Select (button1) buttons should not exist.
        mBots.picker.checkCancelButtonDoesNotExist();
        mBots.picker.checkPickButtonDoesNotExist();
    }
}
