/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.documentsui.loaders

import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import junit.framework.Assert.assertEquals
import org.junit.Test

@SmallTest
class FolderLoaderTest : BaseLoaderTest() {
    @Test
    fun testLoadInBackground() {
        val mockProvider = mEnv.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
        val docs = createDocuments(5)
        mockProvider!!.setNextChildDocumentsReturns(*docs)
        val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
        val queryOptions = QueryOptions(10, null, null, true, arrayOf<String>("*/*"))
        val contentLock = ContentLock()
        // TODO(majewski): Is there a better way to create Downloads root folder DocumentInfo?
        val rootFolderInfo = DocumentInfo()
        rootFolderInfo.authority = TestProvidersAccess.DOWNLOADS.authority
        rootFolderInfo.userId = userIds[0]

        val loader =
            FolderLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                contentLock,
                TestProvidersAccess.DOWNLOADS,
                rootFolderInfo,
                queryOptions,
                mEnv.state.sortModel
            )
        val directoryResult = loader.loadInBackground()
        assertEquals(docs.size, getFileCount(directoryResult))
    }
}
