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

/**
 * The interface containing common attributes and methods between a shortcut and a root that is
 * required to visually display the sidebar entry items.
 */
interface SidebarEntryItemInfo {
    val root: RootInfo
    val documentId: String?
    val uri: Uri?
    val title: String?

    // For sidebar item appearance/view
    fun loadDrawerIcon(context: Context, maybeShowBadge: Boolean): Drawable?
}
