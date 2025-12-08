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

package com.android.documentsui.approveddochandlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.base.MimeTypes
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.base.UserId

/**
 * Data class representing an approved document handler app.
 *
 * @property componentName The component name of the handler activity.
 * @property label The user-visible label of the handler.
 * @property isButton Whether the handler registered to be displayed as a button.
 * @property icon The drawable icon for the handler.
 */
data class ApprovedDocHandler(
    val componentName: ComponentName,
    val label: String,
    val isButton: Boolean,
    val icon: Drawable?,
)

/**
 * A class for discovering approved document handlers.
 *
 * This class queries the system for activities within the approved document handler apps, that can
 * handle the current selection.
 *
 * This class and all methods in this class will only be called when the flag
 * `isUseApprovedDocumentHandlerEnabled` is true.
 */
class ApprovedDocHandlers(
    private val context: Context,
    private val userId: UserId,
    private val injector: Injector<*>,
) {
    private val packageManager: PackageManager = userId.getPackageManager(context)

    companion object {
        private const val TAG = "ApprovedDocHandlers"
        public const val AS_BUTTON_METADATA_KEY = "android.approvedtarget.as_button"
        // TODO(b/464388012): Reference actual intent category when it's available.
        public const val APPROVED_HANDLER_CATEGORY =
            "android.provider.category.APPROVED_DOCUMENT_HANDLER"
    }

    /**
     * Retrieves the package names of approved document handlers from RRO.
     *
     * @return An array of package name strings.
     */
    private fun getPackageNames(): Array<String> {
        val approvedDocHandlers: Array<String> =
            context.resources.getStringArray(R.array.approved_document_handlers)
        if (DEBUG) {
            Log.d(
                TAG,
                "ApprovedDocHandlers ${R.array.approved_document_handlers} : ${approvedDocHandlers.contentToString()}",
            )
        }
        return approvedDocHandlers
    }

    /**
     * Creates and configures an [Intent] for finding approved document handlers.
     *
     * For a single selected item, it creates an [Intent.ACTION_SEND] intent. For multiple items, it
     * uses [Intent.ACTION_SEND_MULTIPLE]. The MIME type is set based on the selection, and a
     * specific category is added to filter for approved handlers.
     *
     * @param selectionDetails The details of the current document selection.
     * @return A configured [Intent] ready to be used for querying the [PackageManager].
     */
    private fun createIntentForSelection(selectionDetails: MenuManager.SelectionDetails): Intent {
        val intent =
            if (selectionDetails.size() == 1) {
                Intent(Intent.ACTION_SEND).apply { type = selectionDetails.mimeTypes().first() }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    if (selectionDetails.mimeTypes().isNotEmpty()) {
                        type = MimeTypes.findCommonMimeType(selectionDetails.mimeTypes().toList())
                    }
                }
            }
        intent.addCategory(APPROVED_HANDLER_CATEGORY)
        return intent
    }

    /**
     * Retrieves a list of approved document handlers that can handle the given selection.
     *
     * @param selectionDetails The details about the current selection of documents.
     * @return A list of [ApprovedDocHandler]s that can handle the selection.
     */
    fun getApprovedDocHandlers(
        selectionDetails: MenuManager.SelectionDetails
    ): List<ApprovedDocHandler> {
        val intent = createIntentForSelection(selectionDetails)
        val approvedPackages = getPackageNames().toSet()
        val resolveInfos =
            packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL or PackageManager.GET_META_DATA,
            )

        val handlers = buildList {
            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo
                if (activityInfo.packageName in approvedPackages) {
                    val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
                    // TODO(b/465299476): Truncate label before displaying on UI
                    val label = activityInfo.loadLabel(packageManager)?.toString()
                    if (label == null) {
                        Log.w(
                            TAG,
                            "Approved doc handler ${componentName.flattenToString()} has no label, skipping.",
                        )
                        continue
                    }
                    val isButton: Boolean =
                        resolveInfo.activityInfo.metaData?.getBoolean(AS_BUTTON_METADATA_KEY) ==
                            true
                    // TODO(b/465270650): Process loadIcon in background thread.
                    val icon: Drawable? =
                        if (isButton) activityInfo.loadIcon(packageManager) else null
                    add(ApprovedDocHandler(componentName, label, isButton, icon))
                }
            }
        }
        // TODO(b/465271281): Cache the handlers, use package notifications to invalidate and
        // re-fetch them when they change.
        return handlers
    }

    /**
     * Updates the provided menu with actions from approved document handlers. This method
     * dynamically adds or removes menu items based on the available approved document handlers and
     * the current selection.
     *
     * @param menu The menu to be updated.
     * @param selectionDetails Details about the current selection of documents.
     */
    fun updateApprovedDocHandlerMenus(menu: Menu, selectionDetails: MenuManager.SelectionDetails) {
        val approvedDocHandlersMap =
            getApprovedDocHandlers(selectionDetails).associateBy { it.componentName }.toMutableMap()
        // TODO(b/465271277): Implement a limitation of the number of action buttons and menu
        // items allowed
        val toRemove = mutableListOf<Int>()

        // TODO(b/466832041): Use a <group> in the menu to group the approved handlers together
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val intent = item.intent
            if (intent?.component == null || !intent.hasCategory(APPROVED_HANDLER_CATEGORY)) {
                continue
            }

            // Have asserted that intent.component is not null above.
            val component = intent.component!!
            val handler = approvedDocHandlersMap.remove(component)
            if (handler != null) {
                item.intent = injector.actions.createApprovedHandlerIntent(component)
            } else {
                toRemove.add(item.itemId)
            }
        }

        for (i in toRemove) {
            menu.removeItem(i)
        }

        // Add new handlers. The map now only contains handlers that are not in the menu.
        for (handler in approvedDocHandlersMap.values) {
            // TODO(b/465299476): Truncate the label if it is too long.
            val item = menu.add(Menu.NONE, View.generateViewId(), Menu.NONE, handler.label)
            item.intent = injector.actions.createApprovedHandlerIntent(handler.componentName)
            if (handler.isButton && handler.icon != null) {
                item.icon = handler.icon
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            } else {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
    }
}
