/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.picker

import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.State
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestProvidersAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ConfigTest {

    companion object {
        private const val FLAG_READ_ONLY = 0
        private const val OFFLINE = false
    }

    @get:Rule val overrideFlagsRule = OverrideFlagsRule()

    private lateinit var config: Config
    private lateinit var env: TestEnv
    private lateinit var state: State

    @Before
    fun setUp() {
        env = TestEnv.create()
        state = env.state
        config = Config()
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testIsDocumentEnabled_superReturnsTrue_doesNotAffectResult() {
        state.action = State.ACTION_CREATE
        // The super method, ActivityConfig.isDocumentEnabled, will return true because the
        // Downloads root doesn't have limited functionality when offline. However the Config method
        // should still return false because read-only files are disabled when creating.
        state.stack.changeRoot(TestProvidersAccess.DOWNLOADS)
        var doc = DocumentInfo()
        doc.mimeType = "image/png"
        doc.flags = FLAG_READ_ONLY
        doc.syncStateFlags = 0
        assertFalse(config.isDocumentEnabled(doc, state, OFFLINE))
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    fun testIsDocumentEnabled_superReturnsFalse_returnsFalse() {
        state.action = State.ACTION_CREATE
        state.acceptMimes = arrayOf("image/png")
        // The super method, ActivityConfig.isDocumentEnabled, will return true because the
        // Downloads root doesn't have limited functionality when offline. The Config method should
        // also return true because the "image/png" mime type is accepted.
        state.stack.changeRoot(TestProvidersAccess.DOWNLOADS)
        var doc = DocumentInfo()
        doc.mimeType = "image/png"
        doc.flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        doc.syncStateFlags = 0
        assertTrue(config.isDocumentEnabled(doc, state, OFFLINE))

        // The super method, ActivityConfig.isDocumentEnabled, will now return false because the
        // Cloud root has limited functionality when offline. This should cause the Config method
        // to return false.
        state.stack.changeRoot(TestProvidersAccess.CLOUD)
        assertFalse(config.isDocumentEnabled(doc, state, OFFLINE))
    }
}
