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

package com.android.documentsui.sidebar

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.files.TestActivity
import com.android.documentsui.testing.TestActionHandler
import com.android.documentsui.testing.TestDragAndDropManager
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.Views
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class DragHostTest {

    private lateinit var actionHandler: TestActionHandler
    private lateinit var activity: TestActivity
    private lateinit var dragAndDropManager: TestDragAndDropManager
    private lateinit var dragHost: DragHost
    private lateinit var nextItem: Item

    @Before
    fun setUp() {
        actionHandler = TestActionHandler()
        activity = TestActivity.create(TestEnv.create())
        dragAndDropManager = TestDragAndDropManager()
        dragHost = DragHost(activity, dragAndDropManager, { v: View -> nextItem }, actionHandler)
    }

    @Test
    fun testCanHandleDragEvent() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val rootItemView = RootItemView(context, null)
        val view = Views.createTestView()

        dragAndDropManager.isDragFromSameAppHandler.nextReturn(true)
        assertTrue(dragHost.canHandleDragEvent(rootItemView))
        assertFalse(dragHost.canHandleDragEvent(view))

        dragAndDropManager.isDragFromSameAppHandler.nextReturn(false)
        assertFalse(dragHost.canHandleDragEvent(rootItemView))
        assertFalse(dragHost.canHandleDragEvent(view))
    }
}
