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
package com.android.documentsui.queries

import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import com.android.documentsui.R
import com.android.documentsui.util.FlagUtils
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.chip.Chip

/**
 * The controller for the search option dropdowns. This controller manages the UI interactions
 * and converts them to a state of the dropdowns. It must be created with the view that contains
 * the buttons that trigger showing or hiding of the dropdowns.
 */
class SearchOptionsController(private val mContainer: View?) {
    // The value of currently selected options. Initialized to sensible defaults.
    private var mLastModifiedOption: LastModifiedOption = LastModifiedOption.LAST_30_DAYS
    private var mFileTypeOption: FileTypeOption = FileTypeOption.ANY_TYPE
    private var mLocationOption: SearchLocationOption = SearchLocationOption.CURRENT_FOLDER

    /**
     * The current value of the last modified option.
     */
    val lastModifiedOption: LastModifiedOption? get() = mLastModifiedOption

    /**
     * The current value of the file type option.
     */
    val fileTypeOption: FileTypeOption? get() = mFileTypeOption

    /**
     * The current value of the location option.
     */
    val locationOption: SearchLocationOption? get() = mLocationOption

    init {
        if (FlagUtils.isUseMaterial3FlagEnabled() && mContainer != null) {
            makeTrigger(
                getRes(R.id.search_location_trigger),
                getRes(R.menu.search_location_menu),
                this::onLocationSelected
            )
            makeTrigger(
                getRes(R.id.search_last_modified_trigger),
                getRes(R.menu.search_last_modified_menu),
                this::onLastModifiedSelected
            )
            makeTrigger(
                getRes(R.id.search_file_type_trigger),
                getRes(R.menu.search_file_type_menu),
                this::onFileTypeSelected
            )
        }
    }

    /**
     * Helper function that removes repetitive setup for each of the option triggers.
     */
    private fun makeTrigger(
        @IdRes triggerId: Int,
        @MenuRes menuId: Int,
        callback: (option: Int) -> Unit
    ) {
        val trigger = mContainer?.findViewById<Chip>(triggerId)
        trigger?.setOnClickListener {
            showMenu(trigger, menuId) {
                callback(it)
            }
        }
    }

    /**
     * Updates the location option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onLocationSelected(locationId: Int): Boolean {
        val selectedOption = searchLocationOptionFor(locationId) ?: return false
        return if (selectedOption == mLocationOption) {
            false
        } else {
            mLocationOption = selectedOption
            true
        }
    }

    /**
     * Updates the file type option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onFileTypeSelected(fileTypeId: Int): Boolean {
        val selectedOption = fileTypeOptionForValue(fileTypeId) ?: return false
        return if (selectedOption == mFileTypeOption) {
            false
        } else {
            mFileTypeOption = selectedOption
            true
        }
    }

    /**
     * Updates the last modified option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onLastModifiedSelected(lastModifiedId: Int): Boolean {
        val selectedOption = lastModifiedOptionFor(lastModifiedId) ?: return false
        return if (selectedOption == mLastModifiedOption) {
            false
        } else {
            mLastModifiedOption = selectedOption
            true
        }
    }

    /**
     * Sets the visibility of the search drop down options bar.
     */
    fun setVisible(visible: Boolean) {
        mContainer?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showMenu(chip: Chip, @MenuRes menuRes: Int, callback: (option: Int) -> Unit) {
        // We prevent the chip from working as a chip. Instead it acts as a button.
        chip.isChecked = false

        // Activate the specified popup menu for the chip.
        val popup = PopupMenu(chip.context, chip)
        popup.menuInflater.inflate(menuRes, popup.menu)
        // TODO(b:391232249) Set checkmarks based on last modified, file type or location options.

        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            chip.text = menuItem.title
            callback(menuItem.itemId)
            false
        }
        popup.show()
    }
}
