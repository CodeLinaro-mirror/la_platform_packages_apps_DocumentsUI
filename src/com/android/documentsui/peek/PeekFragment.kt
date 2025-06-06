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
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback
import java.io.FileNotFoundException

/** Manages the Peek UI. */
class PeekFragment : Fragment() {
    companion object {
        private const val TAG = "PeekFragment"
    }

    // ViewModel holding the UI state of Peek, scoped to DocumentsUI's activity.
    private lateinit var viewModel: PeekViewModel

    // Top bar view.
    private var toolbar: MaterialToolbar? = null

    // Rendering view.
    private var previewFrame: FrameLayout? = null
    private var previewHandler: PreviewHandler? = null

    // Metadata view.
    private var metadataContainer: FrameLayout? = null
    private var metadataView: MetadataView? = null
    private var metadataSheetBehavior: SideSheetBehavior<FrameLayout>? = null

    // Listening for side sheet state updates is relevant when the metadata sheet is dragged.
    private val metadataSheetStateListener =
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

    @Suppress("ktlint:standard:comment-wrapping")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =
            inflater.inflate(getRes(R.layout.peek_layout), container, /* attachToRoot= */ false)
        viewModel = ViewModelProvider(requireActivity())[PeekViewModel::class.java]
        toolbar = view.findViewById(getRes(R.id.peek_toolbar))
        toolbar!!.setNavigationOnClickListener { clearAndHide() }
        toolbar!!.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.peek_info -> {
                    viewModel.metadataSheetExpanded.value?.let {
                        viewModel.toggleMetadataSheet(!it)
                    }
                    true
                }
                else -> false
            }
        }

        previewFrame = view.findViewById(getRes(R.id.peek_preview_frame))

        metadataContainer = view.findViewById(R.id.peek_metadata_container)
        metadataView = MetadataView(requireContext(), viewModel)
        metadataContainer!!.addView(metadataView)
        metadataSheetBehavior = SideSheetBehavior.from(metadataContainer!!)
        metadataSheetBehavior!!.addCallback(metadataSheetStateListener)

        val savedDocInfo = viewModel.docInfo.value
        if (savedDocInfo != null) {
            try {
                savedDocInfo.updateSelf(
                    savedDocInfo.userId.getContentResolver(context),
                    savedDocInfo.userId
                )
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "Stale document info: $e")
                clearAndHide()
            }
        }
        // The observer's `onChanged` method is called immediately after the observer is set, if
        // docInfo has a value. Set the observer after `viewModel.docInfo.value` is updated above.
        viewModel.docInfo.observe(requireActivity(), Observer { docInfo -> updateView(docInfo) })
        viewModel.metadataSheetExpanded.observe(
            requireActivity(),
            Observer { expanded ->
                toolbar!!.menu?.findItem(R.id.peek_info)?.let {
                    if (expanded) {
                        it.icon = getDrawable(requireContext(), R.drawable.ic_info_filled)
                        it.title = getString(R.string.a11y_peek_hide_info_button)
                        it.contentDescription = getString(R.string.a11y_peek_hide_info_button)
                    } else {
                        it.icon = getDrawable(requireContext(), R.drawable.ic_info)
                        it.title = getString(R.string.a11y_peek_show_info_button)
                        it.contentDescription = getString(R.string.a11y_peek_show_info_button)
                    }
                }
                // Only update the metadataSheetBehavior state if the overlay is shown. If not,
                // the metadata sheet is hidden, and its expanded state is restored in `updateView`
                // once the overlay gets shown.
                if (viewModel.overlayActive.value == true) {
                    // Expand the metadata sheet in a post task to ensure that if the fragment is
                    // being recreated, it triggers the expand animation (when relevant).
                    metadataContainer!!.post {
                        if (expanded) {
                            metadataSheetBehavior!!.expand()
                        } else {
                            metadataSheetBehavior!!.hide()
                        }
                    }
                }
            }
        )
        return view
    }

    override fun onDestroyView() {
        metadataSheetBehavior!!.removeCallback(metadataSheetStateListener)
        super.onDestroyView()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Hide the metadata sheet to override the Material SideSheet state restoration behavior.
        // Details:
        // After `onCreateView`, the metadataSheetBehavior restores the expanded state of the sheet
        // via its `onRestoreInstanceState` implementation. `onViewStateRestored` is subsequently
        // called, allowing us to override this state restoration with `metadataSheetBehavior.hide`.
        // `updateView` is responsible for re-expanding the sheet, by posting a
        // `metadataSheetBehavior.expand` task which executes after the fragment has been rendered
        // on screen (which triggers an expand animation, addressing b/419446581).
        metadataSheetBehavior?.hide()
    }

    private fun updateView(docInfo: DocumentInfo?) {
        if (docInfo == null) {
            return
        }
        if (toolbar == null ||
            previewFrame == null ||
            metadataView == null ||
            metadataContainer == null ||
            metadataSheetBehavior == null) {
            // `updateView` will be called again when onCreateView executes.
            return
        }
        // `Expand` needs to be called after the view is visible and fully drawn (see b/419446581).
        // With the check above, we know that onCreateView has been called.
        // The `post` task will execute after the current layout pass, ensuring that we can see the
        // "expand" animation.
        if (viewModel.overlayActive.value == true &&
            viewModel.metadataSheetExpanded.value == true) {
            metadataContainer!!.post { metadataSheetBehavior!!.expand() }
        }
        toolbar!!.title = docInfo.displayName
        previewHandler?.clear()
        previewHandler =
            when {
                docInfo.mimeType.startsWith("image/") ->
                    ImagePreviewHandler(previewFrame!!, docInfo)
                else -> DefaultPreviewHandler(previewFrame!!)
            }
    }

    private fun clearAndHide() {
        viewModel.clear()
        toolbar?.title = ""
        previewHandler?.clear()
        previewHandler = null
    }
}
