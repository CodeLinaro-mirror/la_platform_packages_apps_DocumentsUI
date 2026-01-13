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

package com.android.documentsui.dirlist;

import static com.android.documentsui.util.Material3Config.getRes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.Looper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.R;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
@SmallTest
public class DocumentHolderTest {

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();
    @Rule public final MockitoRule mMocks = MockitoJUnit.rule();

    private static final String MODEL_ID = "model_id";
    private static final UserId USER_ID = UserId.of(0);

    private final Class<? extends DocumentHolder> mHolderClass;
    private TestConfigStore mTestConfigStore = new TestConfigStore();
    private Context mContext;
    private ViewGroup mParent;
    private DocumentsAdapter.Environment mEnv;
    private DocumentInfo mDoc;

    public DocumentHolderTest(Class<? extends DocumentHolder> holderClass) {
        mHolderClass = holderClass;
    }

    @Parameterized.Parameters(name = "{index}: holderClass={0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {{GridDocumentHolder.class}, {ListDocumentHolder.class}});
    }

    @Before
    public void setUp() throws Exception {
        // Required for the progress circle animation.
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mContext.setTheme(getRes(R.style.DocumentsTheme));
        mContext.getTheme().applyStyle(getRes(R.style.DocumentsDefaultTheme), false);
        mParent = new LinearLayout(mContext);
        mEnv = new TestEnvironment(mContext, TestEnv.create(), new TestActionHandler());
        mDoc = new DocumentInfo();
        mDoc.derivedUri = DocumentsContract.buildDocumentUri("authority", "name");
    }

    private DocumentHolder createHolder() {
        if (mHolderClass == GridDocumentHolder.class) {
            return new GridDocumentHolder(
                    mContext,
                    mParent,
                    new IconHelper(
                            mContext,
                            State.MODE_GRID,
                            /* maybeShowBadge= */ false,
                            null,
                            USER_ID,
                            null,
                            mTestConfigStore),
                    mTestConfigStore,
                    mEnv);
        } else if (mHolderClass == ListDocumentHolder.class) {
            return new ListDocumentHolder(
                    mContext,
                    mParent,
                    new IconHelper(
                            mContext,
                            State.MODE_LIST,
                            /* maybeShowBadge= */ false,
                            null,
                            USER_ID,
                            null,
                            mTestConfigStore),
                    (type) -> "type",
                    mTestConfigStore,
                    mEnv);
        } else {
            throw new IllegalArgumentException("Unsupported holder class: " + mHolderClass);
        }
    }

    private View getProgressCircle(DocumentHolder holder) {
        return holder.itemView.findViewById(android.R.id.progress);
    }

    private View getSyncErrorIcon(DocumentHolder holder) {
        return holder.itemView.findViewById(R.id.sync_error_icon);
    }

    private View getUploadIcon(DocumentHolder holder) {
        return holder.itemView.findViewById(R.id.upload_icon);
    }

    private View getDownloadIcon(DocumentHolder holder) {
        return holder.itemView.findViewById(R.id.download_icon);
    }

    private View getTickIcon(DocumentHolder holder) {
        return holder.itemView.findViewById(R.id.progress_tick_icon);
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testProgressCircleShown_ForUpload() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_UPLOAD_PROGRESS;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        View progressCircle = getProgressCircle(holder);
        assertNotNull(progressCircle);
        assertEquals(View.VISIBLE, progressCircle.getVisibility());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.uploading_description_m3)),
                progressCircle.getContentDescription().toString());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.uploading_description_m3)),
                progressCircle.getTooltipText().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testProgressCircleShown_ForDownload() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        View progressCircle = getProgressCircle(holder);
        assertNotNull(progressCircle);
        assertEquals(View.VISIBLE, progressCircle.getVisibility());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.downloading_description_m3)),
                progressCircle.getContentDescription().toString());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.downloading_description_m3)),
                progressCircle.getTooltipText().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testSyncErrorIconShown() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_DOWNLOAD_ERROR;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        View syncError = getSyncErrorIcon(holder);
        assertNotNull(syncError);
        assertEquals(View.VISIBLE, syncError.getVisibility());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.sync_error_description_m3)),
                syncError.getContentDescription().toString());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.sync_error_description_m3)),
                syncError.getTooltipText().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testUploadIconShown() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_LOCAL_CHANGES;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        View uploadIcon = getUploadIcon(holder);
        assertNotNull(uploadIcon);
        assertEquals(View.VISIBLE, uploadIcon.getVisibility());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.upload_description_m3)),
                uploadIcon.getContentDescription().toString());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.upload_description_m3)),
                uploadIcon.getTooltipText().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testDownloadIconShown_forFiles() {
        mDoc.syncStateFlags = 0;
        mDoc.mimeType = "text/plain";
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        View downloadIcon = getDownloadIcon(holder);
        assertNotNull(downloadIcon);
        assertEquals(View.VISIBLE, downloadIcon.getVisibility());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.download_description_m3)),
                downloadIcon.getContentDescription().toString());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.download_description_m3)),
                downloadIcon.getTooltipText().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testDownloadIconNotShown_forFolders() {
        mDoc.syncStateFlags = 0;
        mDoc.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotNull(getDownloadIcon(holder));
        assertNotEquals(View.VISIBLE, getDownloadIcon(holder).getVisibility());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testDownloadIconNotShown_forVirtualFiles() {
        mDoc.syncStateFlags = 0;
        mDoc.mimeType = "text/plain";
        mDoc.flags = DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotNull(getDownloadIcon(holder));
        assertNotEquals(View.VISIBLE, getDownloadIcon(holder).getVisibility());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testTickIconShown_whenJustFinishedSyncAndNoOtherSyncState() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_AVAILABLE_LOCALLY;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ true);

        View tickIcon = getTickIcon(holder);
        assertNotNull(tickIcon);
        assertEquals(View.VISIBLE, tickIcon.getVisibility());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.synced_description_m3)),
                tickIcon.getContentDescription().toString());
        assertEquals(
                mEnv.getContext().getString(getRes(R.string.synced_description_m3)),
                tickIcon.getTooltipText().toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testTickIconNotShown_whenJustFinishedSyncButOtherSyncState() {
        mDoc.syncStateFlags = 0;
        mDoc.mimeType = "text/plain";
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ true);

        View tickIcon = getTickIcon(holder);
        assertNotNull(tickIcon);
        assertNotEquals(View.VISIBLE, tickIcon.getVisibility());
        // Download icon should be shown instead.
        View downloadIcon = getDownloadIcon(holder);
        assertNotNull(downloadIcon);
        assertEquals(View.VISIBLE, downloadIcon.getVisibility());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testOnlyOneSyncIconShownAtATime() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_LOCAL_CHANGES;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotNull(getProgressCircle(holder));
        assertNotNull(getSyncErrorIcon(holder));
        assertNotNull(getUploadIcon(holder));
        assertNotNull(getDownloadIcon(holder));
        assertNotNull(getTickIcon(holder));

        assertNotEquals(View.VISIBLE, getProgressCircle(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getSyncErrorIcon(holder).getVisibility());
        assertEquals(View.VISIBLE, getUploadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getDownloadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getTickIcon(holder).getVisibility());

        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_UPLOAD_ERROR;
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotEquals(View.VISIBLE, getProgressCircle(holder).getVisibility());
        assertEquals(View.VISIBLE, getSyncErrorIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getUploadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getDownloadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getTickIcon(holder).getVisibility());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testNoIconsShownWhenDisabled() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_UPLOAD_PROGRESS;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);
        holder.setEnabled(false);

        assertNotNull(getProgressCircle(holder));
        assertNotEquals(View.VISIBLE, getProgressCircle(holder).getVisibility());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testNoIconsShownWhenAvailable() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_AVAILABLE_LOCALLY;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotNull(getProgressCircle(holder));
        assertNotNull(getSyncErrorIcon(holder));
        assertNotNull(getUploadIcon(holder));
        assertNotNull(getDownloadIcon(holder));
        assertNotNull(getTickIcon(holder));

        assertNotEquals(View.VISIBLE, getProgressCircle(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getSyncErrorIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getUploadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getDownloadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getTickIcon(holder).getVisibility());
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testNoIconsShownWhenNoSyncState() {
        mDoc.syncStateFlags = null;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ true);

        assertNotNull(getProgressCircle(holder));
        assertNotNull(getSyncErrorIcon(holder));
        assertNotNull(getUploadIcon(holder));
        assertNotNull(getDownloadIcon(holder));
        assertNotNull(getTickIcon(holder));

        assertNotEquals(View.VISIBLE, getProgressCircle(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getSyncErrorIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getUploadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getDownloadIcon(holder).getVisibility());
        assertNotEquals(View.VISIBLE, getTickIcon(holder).getVisibility());
    }

    @Test
    @DisableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testIconsDoNotExistWhenFlagsNotEnabled() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_UPLOAD_PROGRESS;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNull(getProgressCircle(holder));
        assertNull(getSyncErrorIcon(holder));
        assertNull(getUploadIcon(holder));
        assertNull(getDownloadIcon(holder));
        assertNull(getTickIcon(holder));
    }

    @Test
    @EnableFlags({Flags.FLAG_CLOUD_FEATURES, Flags.FLAG_USE_MATERIAL3})
    public void testIconDisappearsWhenSyncStateDisappears() {
        mDoc.syncStateFlags = DocumentInfo.SYNC_STATE_FLAG_LOCAL_CHANGES;
        DocumentHolder holder = createHolder();
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotNull(getUploadIcon(holder));
        assertEquals(View.VISIBLE, getUploadIcon(holder).getVisibility());

        // Rebind with a null sync state.
        mDoc.syncStateFlags = null;
        holder.bind(mDoc, MODEL_ID, null, /* justFinishedSync= */ false);

        assertNotNull(getUploadIcon(holder));
        assertNotEquals(View.VISIBLE, getUploadIcon(holder).getVisibility());
    }
}
