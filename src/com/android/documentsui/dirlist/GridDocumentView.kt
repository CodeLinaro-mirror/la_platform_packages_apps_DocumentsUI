/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.android.documentsui.R
import com.android.documentsui.util.Material3Config.Companion.getRes

class GridDocumentView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    RelativeLayout(context, attrs, defStyleAttr) {

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 1)
        if (isInTouchMode) {
            mergeDrawableStates(drawableState, STATE_TOUCH_MODE)
        }
        return drawableState
    }

    companion object {
        private val STATE_TOUCH_MODE = intArrayOf(getRes(R.attr.state_touch_mode))
    }
}
