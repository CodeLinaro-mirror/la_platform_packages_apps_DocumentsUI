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

package com.android.documentsui

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract.Document
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.State
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestProvidersAccess
import kotlin.collections.listOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

data class ActivityConfigTestParams(
    val testName: String,
    val rootInfo: RootInfo,
    val mimeType: String,
    val docFlags: Int,
    val syncStateFlags: Int?,
    val isOnline: Boolean,
    val expectedResult: Boolean,
) {
    override fun toString(): String = testName
}

@RunWith(Enclosed::class)
@SmallTest
class ActivityConfigTest {

    @RunWith(Parameterized::class)
    class ParameterizedTests(private val testParams: ActivityConfigTestParams) {

        @get:Rule val overrideFlagsRule = OverrideFlagsRule()

        private lateinit var config: TestActivityConfig
        private lateinit var env: TestEnv
        private lateinit var state: State

        @Before
        fun setUp() {
            env = TestEnv.create()
            state = env.state
            config = TestActivityConfig()
        }

        @Test
        @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
        fun testIsDocumentEnabled() {
            state.stack.changeRoot(testParams.rootInfo)
            assertEquals(
                testParams.expectedResult,
                config.isDocumentEnabled(
                    testParams.mimeType,
                    testParams.docFlags,
                    testParams.syncStateFlags,
                    state,
                    testParams.isOnline,
                ),
            )
        }

        companion object {
            // Constants for readability.
            private const val ONLINE = true
            private const val OFFLINE = false
            private const val NO_FLAGS = 0
            private val NO_SYNC_STATE: Int? = null
            private val SYNC_AVAILABLE_LOCALLY: Int =
                ActivityConfig.SYNC_STATE_FLAG_AVAILABLE_LOCALLY
            private const val SYNC_UNAVAILABLE_LOCALLY = 0

            @JvmStatic
            @Parameters(name = "{0}")
            fun data(): Collection<ActivityConfigTestParams> {
                return listOf(
                    ActivityConfigTestParams(
                        "cloudRoot_unavailableLocally_offline",
                        TestProvidersAccess.CLOUD,
                        "image/png",
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        OFFLINE,
                        false,
                    ),
                    ActivityConfigTestParams(
                        "cloudRoot_online",
                        TestProvidersAccess.CLOUD,
                        "image/png",
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        ONLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "cloudRoot_virtualDocument",
                        TestProvidersAccess.CLOUD,
                        "image/png",
                        Document.FLAG_VIRTUAL_DOCUMENT,
                        SYNC_UNAVAILABLE_LOCALLY,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "cloudRoot_folder",
                        TestProvidersAccess.CLOUD,
                        Document.MIME_TYPE_DIR,
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "notCloudRoot",
                        TestProvidersAccess.DOWNLOADS,
                        "image/png",
                        NO_FLAGS,
                        SYNC_UNAVAILABLE_LOCALLY,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "cloudRoot_noSyncState",
                        TestProvidersAccess.CLOUD,
                        "image/png",
                        NO_FLAGS,
                        NO_SYNC_STATE,
                        OFFLINE,
                        true,
                    ),
                    ActivityConfigTestParams(
                        "cloudRoot_availableLocally",
                        TestProvidersAccess.CLOUD,
                        "image/png",
                        NO_FLAGS,
                        SYNC_AVAILABLE_LOCALLY,
                        OFFLINE,
                        true,
                    ),
                )
            }
        }
    }

    @RunWith(AndroidJUnit4::class)
    class NonParameterizedTests {
        @get:Rule val overrideFlagsRule = OverrideFlagsRule()

        private lateinit var config: TestActivityConfig
        private lateinit var env: TestEnv
        private lateinit var state: State

        @Before
        fun setUp() {
            env = TestEnv.create()
            state = env.state
            config = TestActivityConfig()
        }

        @Test
        @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
        fun testIsDocumentEnabled_featureFlagDisabled() {
            state.stack.changeRoot(TestProvidersAccess.CLOUD)
            assertTrue(config.isDocumentEnabled("image/png", 0, 0, state, false))
        }
    }

    private class TestActivityConfig : ActivityConfig()
}
