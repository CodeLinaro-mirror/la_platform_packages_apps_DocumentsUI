/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.files.getinfo

import android.app.Application
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Lookup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ViewModel that backs the "Get info" dialog. There are a number of items in the list that are
 * fetched asynchronously. So to avoid blocking the UI thread, build and maintain the list here,
 * ensuring that updates are posted back so the UI thread can update the dialog appropriately.
 */
class GetInfoViewModel(
    application: Application,
    private val doc: DocumentInfo,
    private val fileTypeLookup: Lookup<String, String>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    /** The list of items to be shown in the GetInfoDialog. */
    private val _items = MutableStateFlow<List<ListItem>>(emptyList<ListItem>())
    val items: StateFlow<List<ListItem>> = _items.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val context = getApplication<Application>()

        val baseList = buildList {
            // Add the "General info" header.
            add(ListItem.Header(context.getString(R.string.peek_metadata_general_info_title)))

            // Add the "Name" field along with the value.
            add(
                ListItem.Info(
                    context.getString(R.string.sort_dimension_name),
                    doc.displayName ?: "",
                )
            )

            // Add the "Type" field along with the value, "Unknown" if not available.
            add(
                ListItem.Info(
                    context.getString(R.string.peek_metadata_type),
                    fileTypeLookup.lookup(doc.mimeType)
                        ?: context.getString(R.string.get_info_unknown_file_type),
                )
            )

            // If the size is known format and display it.
            if (doc.size >= 0 && !doc.isDirectory) {
                add(
                    ListItem.Info(
                        context.getString(R.string.peek_metadata_size),
                        DefaultInfoFormatter.formatFileSize(context, doc.size),
                    )
                )
            }

            // If the last modified date is known, format and display it.
            if (doc.lastModified > 0) {
                add(
                    ListItem.Info(
                        context.getString(R.string.peek_metadata_date_modified),
                        DefaultInfoFormatter.formatDate(context, doc.lastModified),
                    )
                )
            }

            // If the summary is available on partial documents, show that as well.
            if (doc.isPartial && doc.summary != null) {
                add(
                    ListItem.Info(
                        context.getString(R.string.sort_dimension_summary),
                        doc.summary ?: "",
                    )
                )
            }
        }

        // Emit the initial synchronous data immediately.
        _items.value = baseList

        // If it's a directory, kick off the async calculation of the children items.
        if (doc.isDirectory) {
            fetchDirectoryCount()
        }
    }

    private fun fetchDirectoryCount() {
        viewModelScope.launch(ioDispatcher) {
            val count = getDirectoryChildCount(doc)

            val context = getApplication<Application>()
            val countItem =
                ListItem.Info(context.getString(R.string.directory_items), count.toString())

            // Don't update the old list, create a new one and emit again. The data shown in the
            // "Get info" dialog is small enough that there is not a large performance cost here.
            _items.update { oldList -> oldList + countItem }
        }
    }

    private fun getDirectoryChildCount(doc: DocumentInfo): Int {
        val childrenUri = DocumentsContract.buildChildDocumentsUri(doc.authority, doc.documentId)
        val resolver = doc.userId.getContentResolver(getApplication())

        return try {
            resolver
                .query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null,
                    null,
                    null,
                )
                ?.use { cursor -> cursor.count } ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load file count for ${doc.derivedUri}", e)
            0
        }
    }

    class Factory(
        private val application: Application,
        private val doc: DocumentInfo,
        private val fileTypeLookup: Lookup<String, String>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GetInfoViewModel(application, doc, fileTypeLookup) as T
        }
    }

    companion object {
        private const val TAG = "GetInfoViewModel"
    }
}
