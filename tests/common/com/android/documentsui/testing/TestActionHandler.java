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

package com.android.documentsui.testing;

import static com.android.documentsui.util.FlagUtils.isUsePeekPreviewFlagEnabled;

import static org.mockito.Mockito.mock;

import android.content.Intent;
import android.content.ComponentName;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.TestActionModeAddons;
import com.android.documentsui.TestActivity;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;

import java.util.function.Consumer;

public class TestActionHandler extends AbstractActionHandler<TestActivity> {

    private final TestEnv mEnv;

    public final TestEventHandler<ItemDetails<String>> open = new TestEventHandler<>();
    public boolean mDeleteHappened;
    public boolean mRequestDisablingQuietModeHappened;

    public DocumentInfo nextRootDocument;

    public TestActionHandler() {
        this(TestEnv.create());
    }

    public TestActionHandler(TestEnv env) {
        super(
                TestActivity.create(env),
                env.state,
                env.providers,
                env.docs,
                env.searchViewManager,
                (String authority) -> null,
                env.injector,
                isUsePeekPreviewFlagEnabled() ? new TestPeekViewManager() : null,
                new TestActionModeAddons(),
                mock(Runnable.class),
                null);

        mEnv = env;
    }

    @Override
    public boolean openItem(ItemDetails<String> doc, @ViewType int type, @ViewType int fallback) {
        return open.accept(doc);
    }

    @Override
    public void showDeleteDialog() {
        mDeleteHappened = true;
    }

    @Override
    public void requestQuietModeDisabled(RootInfo info, UserId userId) {
        mRequestDisablingQuietModeHappened = true;
    }

    @Override
    public void openRoot(RootInfo root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initLocation(Intent intent) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void launchToDefaultLocation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getDocument(String authority, String documentId, UserId userId, int timeout,
            Consumer<DocumentInfo> callback) {
        mEnv.mExecutor.submit(() -> {
            callback.accept(nextRootDocument);
        });
    }

    @Override
    public Intent createApprovedHandlerIntent(ComponentName handler) {
        return new Intent();
    }
}
