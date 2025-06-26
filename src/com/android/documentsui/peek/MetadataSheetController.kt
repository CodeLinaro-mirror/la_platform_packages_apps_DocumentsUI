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

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.android.documentsui.base.DocumentInfo
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback

class MetadataSheetController(
    context: Context,
    viewModel: PeekViewModel,
    sheetContainer: FrameLayout,
) {
    private val metadataView = MetadataView(context, viewModel)
    private val sheetBehavior = SideSheetBehavior.from(sheetContainer)

    // Listening for side sheet state updates is relevant when the metadata sheet is dragged.
    private val sheetStateListener =
        object : SideSheetCallback() {
            override fun onStateChanged(sideSheet: View, newState: Int) {
                if (newState != SideSheetBehavior.STATE_EXPANDED &&
                    newState != SideSheetBehavior.STATE_HIDDEN) {
                    return
                }
                viewModel.toggleMetadataSheet(newState == SideSheetBehavior.STATE_EXPANDED)
            }

            override fun onSlide(sideSheet: View, slideOffset: Float) {}
        }

    init {
        sheetContainer.addView(metadataView)
        sheetBehavior.addCallback(sheetStateListener)
    }

    fun accept(docInfo: DocumentInfo) {
        metadataView.accept(docInfo)
    }

    fun clear() {
        metadataView.clear()
    }

    fun show() {
        sheetBehavior.expand()
    }

    fun hide() {
        sheetBehavior.hide()
    }

    fun onDestroyView() {
        sheetBehavior.removeCallback(sheetStateListener)
    }
}
