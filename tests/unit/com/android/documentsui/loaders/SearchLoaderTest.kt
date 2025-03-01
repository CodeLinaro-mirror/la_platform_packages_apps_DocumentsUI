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
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import junit.framework.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val TOTAL_FILE_COUNT = 8

@RunWith(Parameterized::class)
@SmallTest
class SearchLoaderTest(private val testParams: LoaderTestParams) : BaseLoaderTest() {
    lateinit var mExecutor: ExecutorService
    val mContentLock = ContentLock()
    val mContentObserver = LockingContentObserver(mContentLock) {}

    companion object {
        @JvmStatic
        @Parameters(name = "with parameters {0}")
        fun data() = listOf(
            LoaderTestParams("sample", null, TOTAL_FILE_COUNT),
            LoaderTestParams("txt", null, 2),
            LoaderTestParams("foozig", null, 0),
            // The first file is at NOW, the second at NOW - 1h; expect 2.
            LoaderTestParams("sample", Duration.ofMinutes(60 + 1), 2),
            // TODO(b:378590632): Add test for recents.
        )
    }

    @Before
    override fun setUp() {
        super.setUp()
        mExecutor = Executors.newSingleThreadExecutor()
    }

    @Test
    fun testLoadInBackground() {
        val mockProvider = mEnv.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
        val docs = createDocuments(TOTAL_FILE_COUNT)
        mockProvider!!.setNextChildDocumentsReturns(*docs)
        val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
        val queryOptions =
            QueryOptions(
                TOTAL_FILE_COUNT + 1,
                testParams.lastModifiedDelta,
                null,
                true,
                arrayOf("*/*")
            )
        val rootIds = listOf(TestProvidersAccess.DOWNLOADS)

        // TODO(majewski): Is there a better way to create Downloads root folder DocumentInfo?
        val rootFolderInfo = DocumentInfo()
        rootFolderInfo.authority = TestProvidersAccess.DOWNLOADS.authority
        rootFolderInfo.userId = userIds[0]

        val loader =
            SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                rootIds,
                testParams.query,
                queryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
        val directoryResult = loader.loadInBackground()
        assertEquals(testParams.expectedCount, getFileCount(directoryResult))
    }

    @Test
    fun testBlankQueryAndRecency() {
        val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
        val rootIds = listOf(TestProvidersAccess.DOWNLOADS)
        val noLastModifiedQueryOptions = QueryOptions(10, null, null, true, arrayOf("*/*"))

        // Blank query and no last modified duration is invalid.
        assertThrows(IllegalArgumentException::class.java) {
            SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                rootIds,
                "",
                noLastModifiedQueryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
        }

        // Null query and no last modified duration is invalid.
        assertThrows(IllegalArgumentException::class.java) {
            SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                rootIds,
                null,
                noLastModifiedQueryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
        }
    }
}
