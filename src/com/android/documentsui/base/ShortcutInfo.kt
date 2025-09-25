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
import android.provider.DocumentsContract
import com.android.documentsui.IconUtils
import com.android.documentsui.R
import com.android.documentsui.util.Material3Config.Companion.getRes
import java.util.Objects

class ShortcutInfo(
    val icon: Int,
    override val title: String,
    override val root: RootInfo,
    val parentDirDocumentId: String,
) : SidebarEntryItemInfo {
    override var documentId: String? = null
    override val uri: Uri?
        get() = DocumentsContract.buildDocumentUri(root.authority, documentId)

    override fun loadDrawerIcon(context: Context, maybeShowBadge: Boolean): Drawable? {
        if (icon == RootInfo.LOAD_FROM_CONTENT_RESOLVER) {
            return IconUtils.applyTintColor(
                context,
                loadMimeTypeIcon(context),
                getRes(R.color.item_root_icon),
            )
        } else if (icon != 0) {
            return IconUtils.applyTintColor(context, icon, getRes(R.color.item_root_icon))
        } else {
            return IconUtils.loadPackageIcon(
                context,
                root.userId,
                root.authority,
                root.icon,
                maybeShowBadge,
            )
        }
    }

    private fun loadMimeTypeIcon(context: Context): Drawable? {
        return when (root.derivedType) {
            RootInfo.TYPE_IMAGES -> IconUtils.loadMimeIcon(context, MimeTypes.IMAGE_MIME)
            RootInfo.TYPE_AUDIO -> IconUtils.loadMimeIcon(context, MimeTypes.AUDIO_MIME)
            RootInfo.TYPE_VIDEO -> IconUtils.loadMimeIcon(context, MimeTypes.VIDEO_MIME)
            else -> IconUtils.loadMimeIcon(context, MimeTypes.GENERIC_TYPE)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (other is ShortcutInfo) {
            val o = other

            return Objects.equals(title, o.title) &&
                Objects.equals(documentId, o.documentId) &&
                Objects.equals(root, o.root) &&
                Objects.equals(parentDirDocumentId, o.parentDirDocumentId)
        }
        return false
    }

    override fun toString(): String {
        return ("ShortcutInfo{" +
            "title=" +
            title +
            ", documentId=" +
            documentId +
            ", root=" +
            root +
            "} @ " +
            uri)
    }
}
