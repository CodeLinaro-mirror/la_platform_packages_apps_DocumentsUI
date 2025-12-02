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

package com.android.documentsui.loaders

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.ModelId
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.testing.TestDocumentsProvider
import com.android.documentsui.testing.TestProvidersAccess
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class SummaryLoaderTest : BaseLoaderTest() {

    /** Test provider that provides summaries. */
    private lateinit var summaryProvider: TestDocumentsProvider
    private val authority = getEnvRootInfo().authority
    private val authorityUri = getEnvRootInfo().root.uri
    private val parentDoc =
        DocumentInfo().apply {
            this.authority = this@SummaryLoaderTest.authority
            documentId = "parentDocId"
        }

    override fun getEnvRootInfo(): RootInfo {
        return TestProvidersAccess.DOWNLOADS
    }

    /** Helper to create model Ids from document IDs. */
    private fun createModelId(docId: String): String {
        return ModelId.build(UserId.DEFAULT_USER, authority, docId)!!
    }

    @Before
    fun setUpTest() {
        summaryProvider = environment.mockProviders[authority]!!
        summaryProvider.lastQueryArgs = null
    }

    @Test
    fun testLoadInBackground_byFolder_fetchesCorrectSummaries() {
        val childDocNames = listOf("doc1", "doc2", "doc3")
        // Setup the docs to be returned by queryChildDocuments.
        val childDocs =
            childDocNames
                .map { name ->
                    environment.model.createFile("$name.txt").apply {
                        this.authority = this@SummaryLoaderTest.authority
                    }
                }
                .toTypedArray()
        val (doc1, _, doc3) = childDocs
        val modelIds = childDocs.map { createModelId(it.documentId) }
        val expectedSummaries =
            mapOf(doc1.documentId to "Summary for doc1.", doc3.documentId to "Summary for doc3.")

        summaryProvider.setDocumentSummaries(expectedSummaries)
        summaryProvider.setNextChildDocumentsReturns(*childDocs)

        val loader = SummaryLoader(activity, authorityUri, parentDoc, modelIds, null, null)
        val result = loader.loadInBackground()

        assertThat(result).isNotNull()
        assertThat(result).hasSize(2)
        assertThat(result).containsEntry(createModelId(doc1.documentId), "Summary for doc1.")
        assertThat(result).containsEntry(createModelId(doc3.documentId), "Summary for doc3.")
        assertThat(result).doesNotContainKey(createModelId("doc2"))

        // Check that query args were passed
        val queryArgs = summaryProvider.lastQueryArgs
        assertThat(queryArgs).isNotNull()
        val extraUri =
            queryArgs?.let { BundleCompat.getParcelable(queryArgs, EXTRA_URI, Uri::class.java) }

        assertThat(extraUri)
            .isEqualTo(DocumentsContract.buildDocumentUri(authority, parentDoc.documentId))
    }

    @Test
    fun testLoadInBackground_whenProviderThrowsException_returnsEmptyMap() {
        val modelIds = listOf(createModelId("doc1"))
        summaryProvider.setThrownRuntimeMessage("Faked failure")
        val loader = SummaryLoader(activity, authorityUri, parentDoc, modelIds, null, null)

        val result = loader.loadInBackground()
        assertThat(result).isNotNull()
        assertThat(result).isEmpty()
    }

    @Test
    fun testOnStartLoading_deliversCachedResult() {
        val docIds = listOf("doc1")
        val modelIds = docIds.map { createModelId(it) }
        val summaries = mapOf("doc1" to "Summary 1")
        val expectedSummaries = mapOf(createModelId(docIds[0]) to "Summary 1")
        summaryProvider.setDocumentSummaries(summaries)

        var latch = CountDownLatch(1)
        var deliveredSummaries: Summaries? = null
        val callback1 =
            SummaryLoader.createCallback(activity, authorityUri, null, modelIds, null, null) {
                result ->
                deliveredSummaries = result
                latch.countDown()
            }

        // Start the loader.
        activity.supportLoaderManager.initLoader(LoaderIds.SUMMARY, null, callback1).startLoading()

        // Wait for onLoadFinished.
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(deliveredSummaries).isEqualTo(expectedSummaries)

        // Restart the loader, it should deliver the cached result without querying the provider.
        latch = CountDownLatch(1)
        deliveredSummaries = null
        val callback2 =
            SummaryLoader.createCallback(activity, authorityUri, null, modelIds, null, null) {
                result ->
                deliveredSummaries = result
                latch.countDown()
            }

        activity.supportLoaderManager
            .restartLoader(LoaderIds.SUMMARY, null, callback2)
            .startLoading()
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(deliveredSummaries).isEqualTo(expectedSummaries)

        activity.supportLoaderManager.destroyLoader(LoaderIds.SUMMARY)
    }
}
