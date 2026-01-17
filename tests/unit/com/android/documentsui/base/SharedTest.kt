/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.documentsui.base

import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.files.TestActivity
import com.android.documentsui.flags.Flags.FLAG_HOME_SCREEN_FILES_RO
import com.android.documentsui.flags.Flags.FLAG_USE_ALLFILES_ROOT_FOR_RECENTS
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestEnv
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@SmallTest
@RunWith(Enclosed::class)
class SharedTest {
    /*
     * Use distinct classes for grouping related tests to prevent requiring
     * a single data class that is shared between all tests.
     */
    @RunWith(Parameterized::class)
    class CompareToIgnoreCaseNullableTest(private val testParams: CompareTestCase) {
        @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

        data class CompareTestCase(val lhs: String, val rhs: String, val expected: Int) {
            override fun toString(): String = "lhs: $lhs, rhs: $rhs"
        }

        @Test
        @EnableFlags(FLAG_USE_MATERIAL3)
        fun testCompareToIgnoreCaseNullable() {
            assertThat(Shared.compareToIgnoreCaseNullable(testParams.lhs, testParams.rhs))
                .isEqualTo(testParams.expected)
        }

        companion object {
            @JvmStatic
            @Parameters(name = "{0}")
            fun data(): Collection<CompareTestCase> {
                return listOf(
                    CompareTestCase("", "", 0),
                    CompareTestCase("", "abc", -1),
                    CompareTestCase("abc", "", 1),
                    CompareTestCase("Chapter (10)", "Chapter (9)", 1),
                    CompareTestCase("Chapter (4)", "Chapter (11)", -1),
                    CompareTestCase("3", "99", -1),
                    CompareTestCase("foo", "bar", 1),
                    CompareTestCase("&3#2kasfdj", "*)(jdh;a", 1),
                )
            }
        }
    }

    @RunWith(AndroidJUnit4::class)
    class GetDefaultUriTest {
        @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

        @Test
        @EnableFlags(
            FLAG_HOME_SCREEN_FILES_RO,
            FLAG_USE_MATERIAL3,
            FLAG_USE_ALLFILES_ROOT_FOR_RECENTS,
            FLAG_USE_SEARCH_V2_READ_ONLY,
        )
        fun testFallbackLoadHomeDirCheckWindowAndBreadcrumb() {
            val testEnv: TestEnv = TestEnv.create()
            val testActivity = TestActivity.create(testEnv)
            val downloadsShortcutUri =
                DocumentsContract.buildDocumentUri(
                    Providers.AUTHORITY_STORAGE,
                    Providers.DOWNLOAD_DOCUMENT_ID,
                )
            assertEquals(
                Shared.getDefaultRootUri(testActivity, State.ACTION_CREATE),
                downloadsShortcutUri,
            )
        }
    }
}
