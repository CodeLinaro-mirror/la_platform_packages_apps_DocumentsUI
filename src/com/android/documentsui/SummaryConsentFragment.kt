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
package com.android.documentsui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** A dialog fragment that asks the user for consent to enable the summary column. */
class SummaryConsentFragment(
    private val onPositiveButtonClick: () -> Unit = {},
    private val onNegativeButtonClick: () -> Unit = {},
) : DocumentsUIDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val title = args.getString(EXTRA_TITLE)
        val message = args.getString(EXTRA_MESSAGE)

        val positiveButton = context?.getString(R.string.menu_open)
        val negativeButton = context?.getString(R.string.button_back)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton) { _, _ -> onPositiveButtonClick() }
            .setNegativeButton(negativeButton) { _, _ -> onNegativeButtonClick() }
            .create()
    }

    companion object {
        const val TAG = "SummaryConsentFragment"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"

        @JvmStatic
        fun show(
            fm: FragmentManager,
            context: Context,
            title: String,
            message: String,
            onPositiveButtonClick: () -> Unit,
            onNegativeButtonClick: () -> Unit,
        ) {
            val dialog =
                newInstance(context, title, message, onPositiveButtonClick, onNegativeButtonClick)
            dialog.show(fm, TAG)
        }

        @JvmStatic
        private fun newInstance(
            context: Context,
            title: String,
            message: String,
            onPositiveButtonClick: () -> Unit,
            onNegativeButtonClick: () -> Unit,
        ): SummaryConsentFragment {
            val dialog = SummaryConsentFragment(onPositiveButtonClick, onNegativeButtonClick)
            val args = Bundle()
            args.putString(EXTRA_TITLE, title)
            args.putString(EXTRA_MESSAGE, message)
            dialog.arguments = args
            return dialog
        }
    }
}
