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
package com.android.documentsui.peek

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.google.android.material.appbar.MaterialToolbar
import java.io.FileNotFoundException

/** Manages the Peek UI. */
class PeekFragment : Fragment() {
    companion object {
        private const val TAG = "PeekFragment"
        private const val PEEK_DOC_INFO = "PEEK_DOC_INFO"
    }

    // Interface for custom view components that are rendered based on a DocumentInfo.
    interface Display {
        fun accept(doc: DocumentInfo)

        fun clear()
    }

    // The view manager is used to handle behaviors that are not managed by the fragment itself.
    private lateinit var viewManager: PeekViewManager

    // Top bar view.
    private lateinit var toolbar: MaterialToolbar

    // Rendering view.
    private lateinit var previewFrame: RenderView

    private var docInfo: DocumentInfo? = null

    @Suppress("ktlint:standard:comment-wrapping")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.peek_layout, container, /* attachToRoot= */ false)
        toolbar = view.findViewById(R.id.peek_toolbar)
        previewFrame = view.findViewById(R.id.peek_preview)

        toolbar.setNavigationOnClickListener { clearAndHide() }
        return view
    }

    // Called after the fragment has been created and its previous view state has been restored.
    // This is where the preview is rerendered, when applicable, and additional view states can be
    // restored.
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState == null) {
            return
        }
        val doc = savedInstanceState.getParcelable(PEEK_DOC_INFO, DocumentInfo::class.java)
        if (doc != null) {
            // Update potentially stale document info. Clear and hide the Peek overlay if an
            // exception is caught during the process.
            try {
                doc.updateSelf(doc.userId.getContentResolver(context), doc.userId)
                updateView(doc)
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "Stale document info: $e")
                clearAndHide()
            }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putParcelable(PEEK_DOC_INFO, docInfo)
    }

    fun setViewManager(viewManager: PeekViewManager) {
        this.viewManager = viewManager
    }

    fun updateView(doc: DocumentInfo) {
        if (!lateinitInitialized()) {
            Log.e(TAG, "Members have not been initialized")
            return
        }
        docInfo = doc
        toolbar.title = doc.displayName
        previewFrame.accept(doc)
    }

    private fun lateinitInitialized(): Boolean {
        return ::viewManager.isInitialized &&
            ::toolbar.isInitialized &&
            ::previewFrame.isInitialized
    }

    private fun clearAndHide() {
        if (!lateinitInitialized()) {
            Log.e(TAG, "Some members have not been initialized")
            return
        }
        viewManager.setContainerVisibility(false)
        toolbar.title = ""
        previewFrame.clear()
    }
}
