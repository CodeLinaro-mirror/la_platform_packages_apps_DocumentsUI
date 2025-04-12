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
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.FlagUtils.Companion.isUsePeekPreviewFlagEnabled

/** Manager that controls the Peek UI. */
open class PeekViewManager(private val mActivity: Activity) {
    companion object {
        private const val TAG = "PeekViewManager"
        private const val PEEK_OVERLAY_ACTIVE = "PEEK_OVERLAY_ACTIVE"
    }

    private lateinit var peekFragment: PeekFragment
    private lateinit var container: FrameLayout

    open fun initFragment(fm: FragmentManager, savedInstanceState: Bundle?) {
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

        // Initialize Peek fragment. The fragment manager automatically handles state restoration:
        // the fragment might already exist.
        val existingFragment = fm.findFragmentById(R.id.peek_overlay)
        if (existingFragment == null) {
            peekFragment = PeekFragment()
            val ft: FragmentTransaction = fm.beginTransaction()
            ft.replace(R.id.peek_overlay, peekFragment)
            ft.commitAllowingStateLoss()
        } else {
            peekFragment = existingFragment as PeekFragment
        }
        peekFragment.setViewManager(this)

        // Restore Peek overlay if necessary.
        if (savedInstanceState != null &&
            savedInstanceState.getBoolean(PEEK_OVERLAY_ACTIVE, false)) {
            setContainerVisibility(true)
        }
    }

    open fun peekDocument(doc: DocumentInfo) {
        if (!::peekFragment.isInitialized) {
            Log.e(TAG, "PeekFragment has not been initialized")
            return
        }
        peekFragment.updateView(doc)
        setContainerVisibility(true)
    }

    fun onSaveInstanceState(state: Bundle) {
        if (!::container.isInitialized) {
            Log.e(TAG, "lateinit container not initialized")
            return
        }
        state.putBoolean(PEEK_OVERLAY_ACTIVE, container.isVisible)
    }

    fun setContainerVisibility(visible: Boolean) {
        if (!::container.isInitialized) {
            Log.e(TAG, "Container has not been initialized")
            return
        }
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
