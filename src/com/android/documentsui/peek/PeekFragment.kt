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

class PeekFragment : Fragment() {
    companion object {
        const val TAG = "PeekFragment"
    }

    private lateinit var viewManager: PeekViewManager
    private lateinit var toolbar: MaterialToolbar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.peek_layout, container, /* attachToRoot= */ false)
        toolbar = view.findViewById(R.id.peek_toolbar)
        toolbar.setNavigationOnClickListener {
            if (!::viewManager.isInitialized) {
                Log.e(TAG, "PeekViewManager has not been initialized")
            } else {
                viewManager.setContainerVisibility(false)
            }
        }
        return view
    }

    fun setViewManager(viewManager: PeekViewManager) {
        this.viewManager = viewManager
    }

    fun updateView(doc: DocumentInfo) {
        if (!::toolbar.isInitialized) {
            Log.e(TAG, "Toolbar has not been initialized")
            return
        }
        toolbar.title = doc.displayName
    }
}
