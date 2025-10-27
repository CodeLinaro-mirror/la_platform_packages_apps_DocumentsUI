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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.R
import com.android.documentsui.TestSummaryProvider
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryProviderManagerTest {
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

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        contentResolver = targetContext.contentResolver
        mockResources = Mockito.mock(Resources::class.java)

        // Use the ContextWrapper to provide the mock Resources.
        context = TestContextWrapper(targetContext, mockResources)
    }

    /** Sends a message to the TestSummaryProvider to set its empty state. */
    private fun setIsEmpty(isEmpty: Boolean) {
        val bundle = Bundle().apply { putBoolean("isEmpty", isEmpty) }
        contentResolver.call(TestSummaryProvider.Companion.AUTHORITY, "setIsEmpty", null, bundle)
    }

    @Test
    fun testStart_withNoProvider_isDisabled() = runTest {
        `when`(mockResources.getString(R.string.local_summary_provider)).thenReturn("")
        val manager = SummaryProviderManager(context, this, Uri.parse(""))
        manager.start()

        assertThat(manager.state.value).isEqualTo(SummaryState.DISABLED)
        manager.stop()
    }

    @Test
    fun testStart_withProviderEmpty_isDisabled() = runTest {
        `when`(mockResources.getString(R.string.local_summary_provider))
            .thenReturn(TEST_SUMMARY_PROVIDER)
        setIsEmpty(true)
        val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
        manager.start()

        // Wait for the state update to complete.
        manager.state.first { it == SummaryState.DISABLED }
        assertThat(manager.state.value).isEqualTo(SummaryState.DISABLED)

        manager.stop()
    }

    @Test
    fun testStart_withProviderNotEmpty_isEnabled() = runTest {
        `when`(mockResources.getString(R.string.local_summary_provider))
            .thenReturn(TEST_SUMMARY_PROVIDER)
        setIsEmpty(false)
        val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
        manager.start()

        // Suspend and wait until the state becomes ENABLED.
        manager.state.first { it == SummaryState.ENABLED }
        assertThat(manager.state.value).isEqualTo(SummaryState.ENABLED)
        manager.stop()
    }

    @Test
    fun testStateChanges_whenProviderUpdates() =
        runTest(timeout = 10.seconds) {
            `when`(mockResources.getString(R.string.local_summary_provider))
                .thenReturn(TEST_SUMMARY_PROVIDER)

            // Emulate the provider being disabled.
            setIsEmpty(true)
            val manager = SummaryProviderManager(context, this, Uri.parse(TEST_SUMMARY_PROVIDER))
            manager.start()

            // Wait for it to initialize as disabled.
            manager.state.first { it == SummaryState.DISABLED }
            assertThat(manager.state.value).isEqualTo(SummaryState.DISABLED)

            // Emulate the provider enabling, this should propagate via ContentObserver.
            setIsEmpty(false)

            // We wait for that update to complete.
            manager.state.first { it == SummaryState.ENABLED }
            assertThat(manager.state.value).isEqualTo(SummaryState.ENABLED)
            manager.stop()
        }
}
