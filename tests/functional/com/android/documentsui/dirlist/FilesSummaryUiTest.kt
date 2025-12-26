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

import android.net.Uri
import android.platform.test.annotations.EnableFlags
import androidx.test.filters.LargeTest
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.BaseActivity
import com.android.documentsui.DocumentsProviderHelper
import com.android.documentsui.TestSummaryProvider
import com.android.documentsui.base.Providers.ROOT_ID_DEVICE
import com.android.documentsui.base.UserId
import com.android.documentsui.bots.openRoot
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_USE_FILE_SUMMARY
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@LargeTest
@EnableFlags(FLAG_USE_FILE_SUMMARY, FLAG_USE_MATERIAL3)
class FilesSummaryUiTest : ActivityTestJunit4<FilesActivity>() {
    @get:Rule val overrideFlagsRule: OverrideFlagsRule = OverrideFlagsRule()

    private var summaryHelper: DocumentsProviderHelper? = null

    @Test
    @Throws(Exception::class)
    fun testSummaryInGridAndListModes() {
        val summaryProviderUri = "content://${TestSummaryProvider.AUTHORITY}/root/summary-root"

        // Initialize helper for the summary provider.
        summaryHelper =
            DocumentsProviderHelper(
                UserId.DEFAULT_USER,
                TestSummaryProvider.AUTHORITY,
                context,
                TestSummaryProvider.AUTHORITY,
            )
        checkNotNull(summaryHelper)

        // Helper to interact with the local storage.
        val localStorageHelper = DocumentsProviderHelper.setupStorageAuthorityDocsHelper(context)
        val primaryRoot = localStorageHelper.getRoot(ROOT_ID_DEVICE)
        // Find the Download folder.
        val download = localStorageHelper.findFile(primaryRoot.documentId, "Download")
        assertNotNull(download)

        val fileName = "recent_file.txt"
        val summary = "This is a summary for $fileName"
        var file: Uri? = null
        try {
            // Prepare a file in the Download folder.
            file = localStorageHelper.createDocument(download?.documentId, "text/plain", fileName)
            val fileInfo = localStorageHelper.findDocument(download?.documentId, fileName)
            localStorageHelper.writeDocument(file, "ham and cheese".toByteArray())

            // Prepare the summary for the file.
            summaryHelper?.setSummaryProviderIsEmpty(false) // Enable the provider.
            val summaries: MutableMap<String?, String?> = HashMap()
            summaries[fileInfo?.documentId] = summary
            summaryHelper?.setProviderSummaries(summaries)

            mActivityScenario!!.onActivity { activity ->
                (activity as BaseActivity).setLocalSummaryProvider(Uri.parse(summaryProviderUri))
            }

            // Navigate to the Download folder in the primary root.
            openRoot(context!!, primaryRoot.title, activityLayoutId)
            bots.directory.openDocument("Download")

            // Grid view.
            bots.main.switchToGridMode()
            device!!.waitForIdle()
            bots.directory.assertDocumentSummary(fileName, summary)

            // List view.
            bots.main.switchToListMode()
            device!!.waitForIdle()
            bots.directory.assertDocumentSummary(fileName, summary)
        } finally {
            if (file != null) {
                localStorageHelper.deleteDocument(file)
            }
            summaryHelper?.clearDocumentSummaries()
            localStorageHelper.cleanUp()
            summaryHelper?.cleanUp()
        }
    }
}
