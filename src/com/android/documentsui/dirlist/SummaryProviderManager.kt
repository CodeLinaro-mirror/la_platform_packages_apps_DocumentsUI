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

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import android.util.Log
import com.android.documentsui.R
import com.android.documentsui.base.SharedMinimal.DEBUG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SummaryProviderManager"

/**
 * Represents the state of the local summary provider. See the updateState() method below for the
 * conditions where it gets disabled.
 */
enum class SummaryState {
    // The INITIALIZING state allow the test to wait for the start() method to complete.
    INITIALIZING,
    ENABLED,
    DISABLED,
}

/**
 * Manages the state of the local summary provider.
 *
 * This class is responsible for determining if the summary provider is enabled and notifying
 * listeners of any state changes.
 */
class SummaryProviderManager(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SummaryState.INITIALIZING)
    val state: StateFlow<SummaryState> = _state

    val authorityUri: Uri? = Uri.parse(context.getString(R.string.local_summary_provider))
    val authority: String? = authorityUri?.authority

    private var contentObserver: ContentObserver? = null
    private val contentResolver = context.contentResolver

    /** Starts monitoring the summary provider's state. */
    fun start() {
        if (authority.isNullOrEmpty() || authorityUri == Uri.EMPTY) {
            _state.value = SummaryState.DISABLED
            return
        }

        startContentObserver()

        // Fetch the initial state.
        scope.launch { updateState() }
    }

    /**
     * The ContentObserver listens for changes to the provider's roots URI. The summary provider
     * (Context Engine) should notify this URI when its state changes (e.g., when the user grants or
     * revokes consent).
     */
    private fun startContentObserver() {
        try {
            val rootsUri = DocumentsContract.buildRootsUri(authority!!)
            val contentObserver =
                object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        scope.launch {
                            if (DEBUG) Log.d(TAG, "ContentObserver.onChange: uri=$uri")
                            updateState()
                        }
                    }
                }
            this.contentObserver = contentObserver
            contentResolver.registerContentObserver(rootsUri, true, contentObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start observer for summary provider: $authority", e)
        }
    }

    /** Stops monitoring the summary provider's state. */
    fun stop() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
    }

    /**
     * Checks the current state of the summary provider and updates the state flow. This involves
     * querying the provider's roots and checking its flags.
     */
    private suspend fun updateState() {
        if (authority.isNullOrEmpty()) {
            _state.value = SummaryState.DISABLED
            return
        }

        withContext(Dispatchers.IO) {
            val rootsUri = DocumentsContract.buildRootsUri(authority)
            val projection = arrayOf(Root.COLUMN_FLAGS, Root.COLUMN_ROOT_ID)

            try {
                val rootId = DocumentsContract.getRootId(authorityUri)
                var foundRoot = false

                val cursor = contentResolver.query(rootsUri, projection, null, null, null)
                if (cursor == null) {
                    Log.w(TAG, "Summary provider $authority returned null, assuming disabled")
                    _state.value = SummaryState.DISABLED
                    return@withContext
                }
                cursor.use {
                    while (it.moveToNext()) {
                        val currentRootId =
                            it.getString(it.getColumnIndexOrThrow(Root.COLUMN_ROOT_ID))
                        if (currentRootId != rootId) {
                            continue
                        }
                        foundRoot = true
                        val flags = it.getInt(it.getColumnIndexOrThrow(Root.COLUMN_FLAGS))
                        // The FLAG_EMPTY is used by the provider to signal that it's
                        // disabled, for example, when the user has not given consent.
                        if ((flags and Root.FLAG_EMPTY) != 0) {
                            _state.value = SummaryState.DISABLED
                        } else {
                            _state.value = SummaryState.ENABLED
                        }
                    }
                    if (!foundRoot) {
                        Log.w(TAG, "Root $rootId not found in $authority, assuming disabled")
                        _state.value = SummaryState.DISABLED
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query summary provider: $authority, assuming disabled", e)
                _state.value = SummaryState.DISABLED
            }
        }
    }

    /**
     * Returns true if the summary provider is currently enabled. This is a convenience method for
     * one-time checks. For UI components that need to react to state changes, it's better to
     * collect the `state` flow.
     */
    fun isEnabled(): Boolean {
        return state.value == SummaryState.ENABLED
    }
}
