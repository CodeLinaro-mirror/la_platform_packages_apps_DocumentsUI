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

package com.android.documentsui.approveddochandlers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.rules.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class ApprovedDocHandlersTest {

    @Mock private lateinit var applicationContext: Context
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var selectionDetails: MenuManager.SelectionDetails
    @Mock private lateinit var icon: Drawable
    @Mock private lateinit var injector: Injector<*>

    private lateinit var approvedDocHandlers: ApprovedDocHandlers
    private val testPackage = "com.test.package"
    private val testClass = "com.test.package.TestClass"
    private val testComponent = ComponentName(testPackage, testClass)
    private val activityInfo =
        spy(ActivityInfo()).apply {
            packageName = testPackage
            name = testClass
        }
    private val resolveInfo = ResolveInfo()

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val testCoroutineRule = MainDispatcherRule(testDispatcher)

    @Before
    fun setUp() {
        `when`(applicationContext.packageManager).thenReturn(packageManager)
        `when`(applicationContext.resources).thenReturn(resources)
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        doReturn(icon).`when`(activityInfo).loadIcon(packageManager)
        doReturn("Test App").`when`(activityInfo).loadLabel(packageManager)
        approvedDocHandlers = ApprovedDocHandlers(applicationContext, injector, testDispatcher)
        resolveInfo.activityInfo = activityInfo
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
    }

    private fun getApprovedDocHandlers(
        selectionDetails: MenuManager.SelectionDetails
    ): List<ApprovedDocHandler> {
        approvedDocHandlers.getApprovedDocHandlers(selectionDetails)
        testScheduler.advanceUntilIdle()
        // Call twice to get the result from the cache.
        return approvedDocHandlers.getApprovedDocHandlers(selectionDetails)
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_returnsHandler() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)
        assertThat(handlers[0].label).isEqualTo("Test App")
        assertThat(handlers[0].icon).isNull()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.categories)
            .contains(ApprovedDocHandlers.APPROVED_HANDLER_CATEGORY)
    }

    @Test
    fun testGetApprovedDocHandlers_multipleSelection_returnsHandler() = runTest {
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png", "image/jpeg"))
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.categories)
            .contains(ApprovedDocHandlers.APPROVED_HANDLER_CATEGORY)
    }

    @Test
    fun testGetApprovedDocHandlers_handlerNotInstalled_returnsEmptyList() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(emptyList())

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).isEmpty()
    }

    @Test
    fun testGetApprovedDocHandlers_withAsButtonTrueInMetadata() = runTest {
        val metaData =
            Bundle().apply { putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, true) }
        activityInfo.metaData = metaData
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].isButton).isEqualTo(true)
        assertThat(handlers[0].icon).isEqualTo(icon)
    }

    @Test
    fun testGetApprovedDocHandlers_withAsButtonFalseInMetadata() = runTest {
        val metaData =
            Bundle().apply { putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, false) }
        activityInfo.metaData = metaData
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].isButton).isEqualTo(false)
        assertThat(handlers[0].icon).isNull()
    }

    @Test
    fun testGetApprovedDocHandlers_unapprovedHandler_isSkipped() = runTest {
        val approvedResolveInfo = ResolveInfo()
        approvedResolveInfo.activityInfo = activityInfo

        val unapprovedActivityInfo =
            spy(ActivityInfo()).apply {
                packageName = "unapproved.package"
                name = "TestClass"
            }
        val unapprovedResolveInfo = ResolveInfo()
        unapprovedResolveInfo.activityInfo = unapprovedActivityInfo
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(approvedResolveInfo, unapprovedResolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_nullMimeTypes_usesWildcard() = runTest {
        // Verifies that for a single selection with null mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(null)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND)
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_emptyMimeTypes_usesWildcard() = runTest {
        // Verifies that for a single selection with empty mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(emptySet())
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND)
    }

    @Test
    fun testGetApprovedDocHandlers_multipleSelection_nullMimeTypes_usesWildcard() = runTest {
        // Verifies that for multiple selections with null mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(null)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
    }

    @Test
    fun testGetApprovedDocHandlers_multipleSelection_emptyMimeTypes_usesWildcard() = runTest {
        // Verifies that for multiple selections with empty mime types, the intent type is set to
        // "*/*".
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(emptySet())
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that the handler is still found
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Assert that the intent passed to queryIntentActivities has the correct wildcard type
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.type).isEqualTo("*/*")
        assertThat(intentCaptor.value.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
    }

    @Test
    fun testGetApprovedDocHandlers_handlerWithNoLabel_isSkipped() = runTest {
        // Verifies that a handler with no label is skipped.
        doReturn(null).`when`(activityInfo).loadLabel(packageManager)
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = getApprovedDocHandlers(selectionDetails)

        // Assert that no handlers are returned because the only available one has no label
        assertThat(handlers).isEmpty()
    }

    @Test
    fun testGetApprovedDocHandlers_usesCache() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        approvedDocHandlers.getApprovedDocHandlers(selectionDetails)
        testScheduler.advanceUntilIdle()
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        approvedDocHandlers.getApprovedDocHandlers(selectionDetails)
        testScheduler.advanceUntilIdle()
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageChange() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to populate cache
        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture receiver
        val receiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver::class.java)
        verify(applicationContext)
            .registerReceiver(receiverCaptor.capture(), any(IntentFilter::class.java))
        val receiver = receiverCaptor.value

        // Trigger package change
        val intent =
            Intent(Intent.ACTION_PACKAGE_CHANGED).apply { data = Uri.parse("package:$testPackage") }
        receiver.onReceive(applicationContext, intent)
        testScheduler.advanceUntilIdle() // Process flow emission

        // Call again
        getApprovedDocHandlers(selectionDetails)

        // Verify PM queried again
        verify(packageManager, times(2)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageRemoved() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to populate cache
        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture receiver
        val receiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver::class.java)
        verify(applicationContext)
            .registerReceiver(receiverCaptor.capture(), any(IntentFilter::class.java))
        val receiver = receiverCaptor.value

        // Trigger package removed
        val intent =
            Intent(Intent.ACTION_PACKAGE_REMOVED).apply { data = Uri.parse("package:$testPackage") }
        receiver.onReceive(applicationContext, intent)
        testScheduler.advanceUntilIdle()

        // Call again
        val handlers = getApprovedDocHandlers(selectionDetails)

        // Verify result is empty
        assertThat(handlers).isEmpty()

        // Verify PM NOT queried again (cache updated directly)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageAdded() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(emptyList())

        // First call to populate cache
        var handlers = getApprovedDocHandlers(selectionDetails)
        assertThat(handlers).isEmpty()
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture receiver
        val receiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver::class.java)
        verify(applicationContext)
            .registerReceiver(receiverCaptor.capture(), any(IntentFilter::class.java))
        val receiver = receiverCaptor.value

        // Now the package is found
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // Trigger package added
        val intent =
            Intent(Intent.ACTION_PACKAGE_ADDED).apply { data = Uri.parse("package:$testPackage") }
        receiver.onReceive(applicationContext, intent)
        testScheduler.advanceUntilIdle()

        // Call again
        handlers = getApprovedDocHandlers(selectionDetails)

        // Verify result contains the handler
        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        // Verify PM queried again
        verify(packageManager, times(2)).queryIntentActivities(any(Intent::class.java), anyInt())
    }

    @Test
    fun testGetApprovedDocHandlers_refreshesOnPackageReplaced() = runTest {
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        // First call to populate cache
        getApprovedDocHandlers(selectionDetails)
        verify(packageManager, times(1)).queryIntentActivities(any(Intent::class.java), anyInt())

        // Capture receiver
        val receiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver::class.java)
        verify(applicationContext)
            .registerReceiver(receiverCaptor.capture(), any(IntentFilter::class.java))
        val receiver = receiverCaptor.value

        // Trigger package replaced
        val intent =
            Intent(Intent.ACTION_PACKAGE_REPLACED).apply {
                data = Uri.parse("package:$testPackage")
            }
        receiver.onReceive(applicationContext, intent)
        testScheduler.advanceUntilIdle()

        // Call again
        getApprovedDocHandlers(selectionDetails)

        // Verify PM queried again
        verify(packageManager, times(2)).queryIntentActivities(any(Intent::class.java), anyInt())
    }
}
