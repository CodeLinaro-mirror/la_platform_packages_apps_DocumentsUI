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

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import com.android.documentsui.R
import androidx.fragment.app.FragmentTransaction
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.FlagUtils.Companion.isUsePeekPreviewFlagEnabled

/**
 * Manager that controls the Peek UI.
 */
open class PeekViewManager(
    private val mActivity: Activity
) {
    companion object {
        const val TAG = "PeekViewManager"
    }

    private lateinit var peekFragment: PeekFragment
    private lateinit var container: FrameLayout

    open fun initFragment(
        fm: FragmentManager
    ) {
        if (!isUsePeekPreviewFlagEnabled()) {
            Log.e(TAG, "Attempting to create PeekViewManager while Peek disabled")
            return
        }

        val container: FrameLayout? = mActivity.findViewById(R.id.peek_overlay)
        if (container == null) {
            Log.e(TAG, "Unable to find Peek container")
            return
        }
        this.container = container

        peekFragment = PeekFragment()
        peekFragment.setViewManager(this)
        val ft: FragmentTransaction = fm.beginTransaction()
        ft.replace(R.id.peek_overlay, peekFragment)
        ft.commitAllowingStateLoss()
    }

    open fun peekDocument(doc: DocumentInfo) {
        if (!::peekFragment.isInitialized) {
            Log.e(TAG, "PeekFragment has not been initialized")
            return
        }
        peekFragment.updateView(doc)
        setContainerVisibility(true)
    }

    fun setContainerVisibility(visible: Boolean) {
        if (!::container.isInitialized) {
            Log.e(TAG, "Container has not been initialized")
            return
        }
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }
}