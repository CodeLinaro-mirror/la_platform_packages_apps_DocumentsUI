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

package com.android.documentsui.base

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.IntDef

/**
 * The interface containing common attributes and methods between a shortcut and a root that is
 * required to visually display the sidebar entry items.
 */
interface SidebarEntryItemInfo {
    val root: RootInfo
    val documentId: String?
    val uri: Uri?
    val title: String?

    // The values of these constants determine the sort order of various roots in the RootsFragment.
    companion object {
        const val TYPE_RECENTS: Int = 1
        const val TYPE_IMAGES: Int = 2
        const val TYPE_VIDEO: Int = 3
        const val TYPE_AUDIO: Int = 4
        const val TYPE_DOCUMENTS: Int = 5
        const val TYPE_DOWNLOADS: Int = 6
        const val TYPE_LOCAL: Int = 7
        const val TYPE_MTP: Int = 8
        const val TYPE_SD: Int = 9
        const val TYPE_USB: Int = 10
        const val TYPE_TRASH: Int = 11
        const val TYPE_FILES: Int = 12
        const val TYPE_OTHER: Int = 13
    }
    @IntDef(
        TYPE_RECENTS,
        TYPE_IMAGES,
        TYPE_VIDEO,
        TYPE_AUDIO,
        TYPE_DOCUMENTS,
        TYPE_DOWNLOADS,
        TYPE_LOCAL,
        TYPE_MTP,
        TYPE_SD,
        TYPE_USB,
        TYPE_TRASH,
        TYPE_FILES,
        TYPE_OTHER,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SidebarEntryItemType

    // For sidebar item appearance/view
    fun loadDrawerIcon(context: Context, maybeShowBadge: Boolean): Drawable?
}
