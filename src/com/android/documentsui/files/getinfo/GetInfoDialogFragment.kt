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

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.Shared
import com.android.documentsui.util.UnitUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A dialog that shows all the metadata aspects related to a file / folder. Is currently invoked
 * when the user clicks "Get info" in the 3-dot / context menus.
 */
class GetInfoDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "GetInfoDialog"

        private const val WIDTH = 396f

        /**
         * Creates the fragment and converts the supplied `doc` into an appropriate type to pass
         * down to the dialog.
         */
        @JvmStatic
        fun show(fm: FragmentManager, doc: DocumentInfo) {
            if (fm.isStateSaved) {
                Log.w(TAG, "Skip showing get info dialog because state saved")
                return
            }

            if (fm.findFragmentByTag(TAG) != null) {
                Log.w(TAG, "Skipping to get info dialog because it's already showing.")
                return
            }

            val dialog = GetInfoDialogFragment()
            val args = Bundle().apply { putParcelable(Shared.EXTRA_DOC, doc) }
            dialog.arguments = args
            dialog.show(fm, TAG)
        }

        /**
         * Generates the list of metadata related to the file, extracted from the `DocumentsInfo`
         * that is passed on when the dialog is opened.
         */
        @VisibleForTesting
        fun createDataList(
            context: Context,
            doc: DocumentInfo,
            fileTypeLookup: Lookup<String, String>,
        ): List<ListItem> {
            val dataList = mutableListOf<ListItem>()

            // Add the "General info" header title.
            dataList.add(
                ListItem.Header(context.getString(R.string.peek_metadata_general_info_title))
            )

            // Add the file display name.
            dataList.add(
                ListItem.Info(
                    context.getString(R.string.sort_dimension_name),
                    doc.displayName ?: "",
                )
            )

            // Add the file type.
            dataList.add(
                ListItem.Info(
                    context.getString(R.string.peek_metadata_type),
                    fileTypeLookup.lookup(doc.mimeType)
                        ?: context.getString(R.string.get_info_unknown_file_type),
                )
            )

            // Add the file size (if one exists and it's not a directory).
            if (doc.size >= 0 && !doc.isDirectory) {
                dataList.add(
                    ListItem.Info(
                        context.getString(R.string.peek_metadata_size),
                        DefaultInfoFormatter.formatFileSize(context, doc.size),
                    )
                )
            }

            // Add the file last modified date (if one exists).
            if (doc.lastModified > 0) {
                dataList.add(
                    ListItem.Info(
                        context.getString(R.string.peek_metadata_date_modified),
                        DefaultInfoFormatter.formatDate(context, doc.lastModified),
                    )
                )
            }

            // Add the document summary (if one exists).
            if (doc.isPartial && doc.summary != null) {
                dataList.add(
                    ListItem.Info(
                        context.getString(R.string.sort_dimension_summary),
                        doc.summary ?: "",
                    )
                )
            }
            return dataList
        }
    }

    /** Creates the dialog UI and binds the data to the layout. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val doc =
            arguments?.let {
                BundleCompat.getParcelable(it, Shared.EXTRA_DOC, DocumentInfo::class.java)
            }
                ?: savedInstanceState?.let {
                    BundleCompat.getParcelable(it, Shared.EXTRA_DOC, DocumentInfo::class.java)
                }

        if (doc == null) {
            Log.e(TAG, "Document missing. Closing dialog.")
            return super.onCreateDialog(savedInstanceState).apply { dismiss() }
        }

        val context = requireContext()
        val customView = LayoutInflater.from(context).inflate(R.layout.get_info_m3, null)
        val contentLayout: LinearLayout = customView.findViewById(R.id.content_layout)

        val fileTypeLookup: Lookup<String, String> =
            DocumentsApplication.getFileTypeLookup(getContext())

        val dataList = createDataList(context, doc, fileTypeLookup)

        dataList.forEachIndexed { position, item ->
            val view =
                when (item) {
                    is ListItem.Header -> {
                        LayoutInflater.from(context)
                            .inflate(R.layout.get_info_header_m3, contentLayout, false)
                            .apply { findViewById<TextView>(R.id.header_title).text = item.title }
                    }

                    is ListItem.Info -> {
                        LayoutInflater.from(context)
                            .inflate(R.layout.get_info_item_m3, contentLayout, false)
                            .apply {
                                findViewById<TextView>(R.id.item_label).text = item.label
                                findViewById<TextView>(R.id.item_value).text = item.value

                                // Check if the item is the first in the section.
                                val isFirstInSection =
                                    if (position == 0) {
                                        true
                                    } else {
                                        dataList[position - 1] is ListItem.Header
                                    }

                                // Check if this is the last item in the section.
                                val isLastInSection =
                                    if (position == dataList.size - 1) {
                                        true
                                    } else {
                                        dataList[position + 1] is ListItem.Header
                                    }

                                // Apply the correct background drawable based on the position of
                                // the item in the section. The border radius is different based on
                                // the position of the item.
                                val backgroundRes =
                                    when {
                                        isFirstInSection && isLastInSection ->
                                            R.drawable.get_info_item_single_m3

                                        isFirstInSection -> R.drawable.get_info_item_top_m3
                                        isLastInSection -> R.drawable.get_info_item_bottom_m3
                                        else -> R.drawable.get_info_item_middle_m3
                                    }

                                setBackgroundResource(backgroundRes)

                                // Apply the appropriate margins to the item according to the
                                // position in the list.
                                val params = layoutParams as ViewGroup.MarginLayoutParams
                                val gapBetweenItems =
                                    resources.getDimensionPixelSize(R.dimen.space_extra_small_1)
                                if (isFirstInSection && isLastInSection) {
                                    // No padding as it's the only item in the list.
                                } else if (isFirstInSection) {
                                    params.bottomMargin = gapBetweenItems
                                } else if (isLastInSection) {
                                    params.bottomMargin =
                                        resources.getDimensionPixelSize(R.dimen.space_small_1)
                                } else {
                                    params.bottomMargin = gapBetweenItems
                                }

                                layoutParams = params
                            }
                    }
                }
            contentLayout.addView(view)
        }

        val closeButton: View = customView.findViewById(R.id.close_button)
        closeButton.setOnClickListener { dismiss() }

        val builder = MaterialAlertDialogBuilder(context).setView(customView)
        return builder.create()
    }

    /** Resize the dialog to the requested size. */
    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.let { d ->
            d.window?.let { w ->
                val params = WindowManager.LayoutParams()
                params.copyFrom(w.attributes)
                val maxWidth = requireContext().resources.displayMetrics.widthPixels
                // The window size is dialog size + right & left insets.
                params.width = UnitUtils.dpToPx(requireContext(), WIDTH).coerceAtMost(maxWidth)
                w.attributes = params
            }
        }
    }
}
