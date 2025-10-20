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
package com.android.documentsui.breadcrumbs

import android.platform.test.annotations.EnableFlags
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.RootInfo
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.InstantTaskExecutorRule
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
@RunWith(AndroidJUnit4::class)
@SmallTest
class BreadcrumbControllerTest {

    // Rule to ensure LiveData operations run synchronously on the test thread
    @get:Rule(order = 0) val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val setFlags = OverrideFlagsRule()

    private lateinit var controller: BreadcrumbController
    private lateinit var view: BreadcrumbView
    private val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.STARTED)

    @Before
    fun setUpTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        view = BreadcrumbView(context)
        val model = BreadcrumbModel()
        controller = BreadcrumbController(lifecycleOwner, model, view)
    }

    @Test
    fun testSetVisible() {
        assertEquals(view.visibility, View.GONE)
        controller.setVisible(true)
        assertEquals(view.visibility, View.VISIBLE)
        controller.setVisible(false)
        assertEquals(view.visibility, View.GONE)
    }

    @Test
    fun testEvents() {
        var folderIndex = -1
        controller.setVisible(true)
        controller.setClickConsumer { value -> folderIndex = value }
        val root = RootInfo().apply { title = "root" }
        val stack = DocumentStack()
        stack.changeRoot(root)
        controller.getModel().setFromStack(stack)
        assertTrue(view.performPathItemClick(0))
        assertEquals(0, folderIndex)

        controller.setClickConsumer(null)
        folderIndex = -1
        // This event should not be propagated.
        assertTrue(view.performPathItemClick(0))
        assertEquals(-1, folderIndex)
    }

    @Test
    fun testClearOnHide() {
        controller.setVisible(true)
        controller.getModel().setPath(arrayOf("Foo", "Bar", "baz.txt"))
        assertEquals(3, view.getPathLength())
        controller.setVisible(false)
        controller.setVisible(true)
        assertEquals(0, view.getPathLength())
    }
}
