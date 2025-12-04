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

package com.android.documentsui.dirlist

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.R
import com.android.documentsui.TestSummaryProvider
import com.android.documentsui.base.RootInfo
import com.android.documentsui.flags.Flags.FLAG_USE_FILE_SUMMARY
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.prefs.LocalPreferences
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestProvidersAccess
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@EnableFlags(FLAG_USE_FILE_SUMMARY, FLAG_USE_MATERIAL3)
class SummaryProviderManagerTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var mockResources: Resources
    private var TEST_SUMMARY_PROVIDER =
        "content://${TestSummaryProvider.Companion.AUTHORITY}/root/summary-root"

    /** A custom ContextWrapper that allows us to override getResources() for testing. */
    private class TestContextWrapper(base: Context, private val mockResources: Resources) :
        ContextWrapper(base) {
        override fun getResources(): Resources {
            return mockResources
        }
    }

    /** Helper to run tests with a default timeout. */
    private fun runTestWithTimeout(testBody: suspend CoroutineScope.() -> Unit) =
        runTest(timeout = 10.seconds, testBody = testBody)

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = targetContext.contentResolver
        mockResources = Mockito.mock(Resources::class.java)

        // Use the ContextWrapper to provide the mock Resources.
        context = TestContextWrapper(targetContext, mockResources)

        setSummaryConsent(false)
    }

    /**
     * Sets the summary preference, it's local preference that tells if the summary column should be
     * visible or hidden.
     */
    private fun setSummaryConsent(enabled: Boolean) {
        LocalPreferences.setSummaryEnabled(context, enabled)
    }

    /**
     * Sends a message to the TestSummaryProvider to set it as enabled/disabled. The provider state
     * for EMPTY tell if the provider is enabled.
     */
    private fun setSummaryProviderEnabled(enabled: Boolean) {
        // NOTE: Empty is the inverse of the enabled state.
        val bundle = Bundle().apply { putBoolean("isEmpty", !enabled) }
        contentResolver.call(TestSummaryProvider.AUTHORITY, "setIsEmpty", null, bundle)
    }

    @Test
    fun testStart_withNoProvider_isDisabled() = runTestWithTimeout {
        `when`(mockResources.getString(R.string.local_summary_provider)).thenReturn("")
        val manager = SummaryProviderManager(context, this, Uri.parse(""))
        manager.start()
        assertThat(manager.state.value).isEqualTo(SummaryProviderState.ProviderUnavailable)
        manager.stop()
    }

    @Test
    fun testStart_withProviderEmpty_isDisabled() = runTestWithTimeout {
        `when`(mockResources.getString(R.string.local_summary_provider))
            .thenReturn(TEST_SUMMARY_PROVIDER)
        setSummaryProviderEnabled(enabled = false)
        val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
        manager.start()

        // Wait for the state to update.
        manager.state.first { it is SummaryProviderState.Available && !it.isUserEnabled }

        assertThat(manager.state.value)
            .isEqualTo(SummaryProviderState.Available(isUserEnabled = false))

        manager.stop()
    }

    @Test
    fun testStart_withProviderEnabled() = runTestWithTimeout {
        `when`(mockResources.getString(R.string.local_summary_provider))
            .thenReturn(TEST_SUMMARY_PROVIDER)
        setSummaryProviderEnabled(enabled = true)
        setSummaryConsent(enabled = true)
        val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
        manager.start()

        // Wait until the state becomes available.
        manager.state.first { it is SummaryProviderState.Available }
        assertThat(manager.state.value)
            .isEqualTo(SummaryProviderState.Available(isUserEnabled = true))
        manager.stop()
    }

    @Test
    fun testStateChanges_whenProviderUpdates() = runTestWithTimeout {
        `when`(mockResources.getString(R.string.local_summary_provider))
            .thenReturn(TEST_SUMMARY_PROVIDER)

        // Start with the provider being disabled.
        setSummaryProviderEnabled(enabled = false)
        setSummaryConsent(enabled = true)
        val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
        manager.start()

        // Wait for it to initialize as disabled.
        manager.state.first { it is SummaryProviderState.Available && !it.isUserEnabled }
        assertThat(manager.state.value)
            .isEqualTo(SummaryProviderState.Available(isUserEnabled = false))

        // Flip the provider to enabled, this should propagate via ContentObserver.
        setSummaryProviderEnabled(enabled = true)

        // Wait for that update to complete.
        manager.state.first { it is SummaryProviderState.Available && it.isUserEnabled }
        assertThat(manager.state.value)
            .isEqualTo(SummaryProviderState.Available(isUserEnabled = true))
        manager.stop()
    }

    @Test
    fun testDisplaySummaryForRoot() = runTestWithTimeout {
        // Start it with empty, should not display summary.
        setSummaryProviderEnabled(enabled = false)
        // But locally set as user enabled (aka summary column visible when the provider gets
        // enabled).
        setSummaryConsent(enabled = true)
        val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
        manager.start()

        // Non-local root. Summary are only available for local files.
        val testRoot =
            RootInfo().apply {
                authority = TestSummaryProvider.AUTHORITY
                rootId = "summary-root"
            }

        manager.state.first { it is SummaryProviderState.Available && !it.isUserEnabled }
        assertThat(displaySummaryForRoot(manager, TestProvidersAccess.DOWNLOADS)).isFalse()

        // When provider is enabled and user enabled, it should display summary.
        setSummaryProviderEnabled(enabled = true)
        manager.state.first { it is SummaryProviderState.Available && it.isUserEnabled }

        // Something that isn't local shouldn't display the summary.
        assertThat(displaySummaryForRoot(manager, testRoot)).isFalse()

        // Test with roots that are local. These can display the summary.
        assertThat(displaySummaryForRoot(manager, TestProvidersAccess.DOWNLOADS)).isTrue()
        assertThat(displaySummaryForRoot(manager, TestProvidersAccess.HOME)).isTrue()
        assertThat(displaySummaryForRoot(manager, TestProvidersAccess.RECENTS)).isTrue()
    }
}
