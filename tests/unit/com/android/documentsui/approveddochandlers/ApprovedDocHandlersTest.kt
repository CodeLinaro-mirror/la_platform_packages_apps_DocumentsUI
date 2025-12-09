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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.base.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class ApprovedDocHandlersTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var context: Context
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var resources: Resources
    @Mock private lateinit var selectionDetails: MenuManager.SelectionDetails
    @Mock private lateinit var icon: Drawable
    @Mock private lateinit var injector: Injector<*>

    private lateinit var approvedDocHandlers: ApprovedDocHandlers
    private var userId: UserId = UserId.of(10)
    private val testPackage = "com.test.package"
    private val testClass = "com.test.package.TestClass"
    private val testComponent = ComponentName(testPackage, testClass)
    private val activityInfo =
        spy(ActivityInfo()).apply {
            packageName = testPackage
            name = testClass
        }

    @Before
    fun setUp() {
        `when`(context.createPackageContextAsUser(eq("android"), anyInt(), eq(userId.userHandle)))
            .thenReturn(context)
        `when`(context.resources).thenReturn(resources)
        doReturn(packageManager).`when`(context).getPackageManager()
        doReturn(icon).`when`(activityInfo).loadIcon(packageManager)
        doReturn("Test App").`when`(activityInfo).loadLabel(packageManager)
        approvedDocHandlers = ApprovedDocHandlers(context, userId, injector)
    }

    @Test
    fun testGetApprovedDocHandlers_singleSelection_returnsHandler() {
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = approvedDocHandlers.getApprovedDocHandlers(selectionDetails)

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
    fun testGetApprovedDocHandlers_multipleSelection_returnsHandler() {
        `when`(selectionDetails.size()).thenReturn(2)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png", "image/jpeg"))
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = approvedDocHandlers.getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(packageManager).queryIntentActivities(intentCaptor.capture(), anyInt())
        assertThat(intentCaptor.value.categories)
            .contains(ApprovedDocHandlers.APPROVED_HANDLER_CATEGORY)
    }

    @Test
    fun testGetApprovedDocHandlers_handlerNotInstalled_returnsEmptyList() {
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(emptyList())

        val handlers = approvedDocHandlers.getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).isEmpty()
    }

    @Test
    fun testGetApprovedDocHandlers_withAsButtonTrueInMetadata() {
        val metaData =
            Bundle().apply { putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, true) }
        activityInfo.metaData = metaData
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = approvedDocHandlers.getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].isButton).isEqualTo(true)
        assertThat(handlers[0].icon).isEqualTo(icon)
    }

    @Test
    fun testGetApprovedDocHandlers_withAsButtonFalseInMetadata() {
        val metaData =
            Bundle().apply { putBoolean(ApprovedDocHandlers.AS_BUTTON_METADATA_KEY, false) }
        activityInfo.metaData = metaData
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = activityInfo
        `when`(packageManager.queryIntentActivities(any(Intent::class.java), anyInt()))
            .thenReturn(listOf(resolveInfo))

        val handlers = approvedDocHandlers.getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].isButton).isEqualTo(false)
        assertThat(handlers[0].icon).isNull()
    }

    @Test
    fun testGetApprovedDocHandlers_unapprovedHandler_isSkipped() {
        `when`(selectionDetails.size()).thenReturn(1)
        `when`(selectionDetails.mimeTypes()).thenReturn(setOf("image/png"))
        `when`(resources.getStringArray(R.array.approved_document_handlers))
            .thenReturn(arrayOf(testPackage))
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

        val handlers = approvedDocHandlers.getApprovedDocHandlers(selectionDetails)

        assertThat(handlers).hasSize(1)
        assertThat(handlers[0].componentName).isEqualTo(testComponent)
    }
}
