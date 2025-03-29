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

package com.android.documentsui.queries

import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy

@RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
@RunWith(AndroidJUnit4::class)
@SmallTest
class SearchOptionsControllerTest {
    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    var mContext: Context? = null
    var mController: SearchOptionsController? = null
    var mContainer: LinearLayout? = null

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mContainer = spy(LinearLayout(mContext))
        mController = SearchOptionsController(mContainer)
    }

    @Test
    fun testOptionsUpdateWorks() {
        for (e in SearchLocationOption.entries) {
            mController!!.onLocationSelected(e.ordinal)
            assertEquals(mController!!.locationOption, e)
        }
        for (e in LastModifiedOption.entries) {
            mController!!.onLastModifiedSelected(e.ordinal)
            assertEquals(mController!!.lastModifiedOption, e)
        }
        for (e in FileTypeOption.entries) {
            mController!!.onFileTypeSelected(e.ordinal)
            assertEquals(mController!!.fileTypeOption, e)
        }
    }
}
