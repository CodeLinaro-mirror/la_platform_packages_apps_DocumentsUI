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

package com.android.documentsui.files

import android.app.Dialog
import android.icu.text.MessageFormat
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.android.documentsui.Injector
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import kotlin.math.roundToInt

/** Dialog shown to users when opening files from the trash. */
class FileOpenFromTrashDialogFragment : DialogFragment() {

    var mDocuments: List<DocumentInfo> = emptyList()

    companion object {
        private const val TAG = "FileOpenFromTrash"

        private const val INSET = 32f
        private const val WIDTH = 320f

        /**
         * Create and show the dialog UI.
         *
         * @param fm the fragment manager
         */
        @JvmStatic
        fun show(fm: FragmentManager, docs: List<DocumentInfo>) {
            if (fm.isStateSaved) {
                Log.w(TAG, "Skip showing empty trash dialog because state saved")
                return
            }

            if (fm.findFragmentByTag(TAG) != null) {
                Log.w(TAG, "Skipping to show empty trash dialog because it's already showing.")
                return
            }

            val dialog = FileOpenFromTrashDialogFragment()
            dialog.mDocuments = docs
            dialog.show(fm, TAG)
        }
    }

    /**
     * Creates the dialog UI.
     *
     * @param savedInstanceState bundle with required arg: selectedDoc.
     * @return an AlertDialog instance.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val injector: Injector<*> = (getActivity() as FilesActivity).getInjector()
        val formatArgs = mapOf("count" to mDocuments.size)
        val title =
            MessageFormat(getString(R.string.file_open_in_trash_dialog_title), Locale.getDefault())
                .format(formatArgs)
        val message =
            MessageFormat(
                    getString(R.string.file_open_in_trash_dialog_message),
                    Locale.getDefault(),
                )
                .format(formatArgs)

        val builder =
            MaterialAlertDialogBuilder(requireContext())
                // We're setting the inset size explicitly so changes to the default inset size in
                // the future don't change our dialog size (the inset size affect the dialog size
                // because we're overriding the window size to get our desired dialog size).
                .setBackgroundInsetStart(dpToPx(INSET))
                .setBackgroundInsetEnd(dpToPx(INSET))
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                    getString(R.string.file_open_in_trash_dialog_restore_action_button)
                ) { dialog, which ->
                    injector.actions.restoreSelectedDocumentsFromTrash(mDocuments)
                }
                .setNegativeButton(getString(android.R.string.cancel), null)

        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.let { d ->
            d.window?.let { w ->
                val params = WindowManager.LayoutParams()
                params.copyFrom(w.attributes)
                val maxWidth = requireContext().resources.displayMetrics.widthPixels
                // The window size is dialog size + right & left insets.
                params.width = dpToPx(WIDTH + (2 * INSET)).coerceAtMost(maxWidth)
                w.attributes = params
            }
        }
    }

    fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                requireContext().resources.displayMetrics,
            )
            .roundToInt()
    }
}
