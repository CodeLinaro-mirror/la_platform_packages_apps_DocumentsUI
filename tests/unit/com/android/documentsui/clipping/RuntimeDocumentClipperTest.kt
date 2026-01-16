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

package com.android.documentsui.clipping

import android.content.ClipDescription
import android.content.Context
import android.content.SharedPreferences
import android.os.PersistableBundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.services.FileOperations
import com.android.documentsui.testing.ClipDatas
import com.android.documentsui.testing.TestEventListener
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
@SmallTest
class RuntimeDocumentClipperTest {

    @get:Rule var folder: TemporaryFolder = TemporaryFolder()

    private lateinit var clipper: RuntimeDocumentClipper
    private lateinit var context: Context
    private lateinit var pref: SharedPreferences

    private lateinit var storage: ClipStorage

    private val callbackListener = TestEventListener<Int>()
    private val callback =
        FileOperations.Callback { status, _, _ -> callbackListener.accept(status) }

    @Before
    fun setUp() {
        pref =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getSharedPreferences("pref", 0)
        val clipDir = ClipStorage.prepareStorage(folder.getRoot())
        storage = ClipStorage(clipDir, pref)

        context = Mockito.mock(Context::class.java)
        clipper = RuntimeDocumentClipper(context, storage)
    }

    @Test
    fun testWhenOpTypeIndeterminableOperationIsRejected() {
        val stack = DocumentStack()

        val description = ClipDescription("", emptyArray<String>())
        val clipData = ClipDatas.createTestClipData(description)

        clipper.copyFromClipData(stack, clipData, callback)
        callbackListener.assertLastArgument(FileOperations.Callback.STATUS_REJECTED)
    }

    @Test
    fun testImagePastedOnClipboardWithNoOperationTypeIsRejected() {
        val stack = DocumentStack()

        val description = ClipDescription("", emptyArray<String>())

        // This extras is present on a clipboard with an image attached, however, it doesn't contain
        // the operation type that we use to determine a copy / move operation. An empty bundle is
        // fine here as the operation type will not be found and go to default and still reject.
        description.extras = PersistableBundle()
        val clipData = ClipDatas.createTestClipData(description)

        clipper.copyFromClipData(stack, clipData, callback)
        callbackListener.assertLastArgument(FileOperations.Callback.STATUS_REJECTED)
    }

    @Test
    fun testImagePastedOnClipboardWithUnknownOperationTypeIsRejected() {
        val stack = DocumentStack()

        val description = ClipDescription("", emptyArray<String>())

        // The opType is out of bounds. This will throw an exception and get caught when trying to
        // use FileOperationService.build(), which gets bubbled up to a rejected status.
        description.extras = PersistableBundle().apply { putInt("clipper:opType", 9999) }
        val clipData = ClipDatas.createTestClipData(description)

        clipper.copyFromClipData(stack, clipData, callback)
        callbackListener.assertLastArgument(FileOperations.Callback.STATUS_REJECTED)
    }
}
