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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.R
import com.android.documentsui.base.MimeTypes
import com.android.documentsui.base.SharedMinimal.DEBUG
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * Data class representing the status of an approved document handler.
 *
 * @property isSupported Whether this handler supports the action and MIME type combination.
 * @property isOutdated Whether this handler information is outdated. This is set to true when the
 *   package is added, changed, or replaced, indicating that the handler info needs to be
 *   re-fetched.
 * @property handler The handler info, if supported.
 */
data class HandlerStatus(
    val isSupported: Boolean,
    val isOutdated: Boolean,
    val handler: ApprovedDocHandler? = null,
)

/**
 * A class for discovering approved document handlers.
 *
 * This class queries the system for activities within the approved document handler apps, that can
 * handle the current selection.
 *
 * This class and all methods in this class will only be called when the flag
 * `isUseApprovedDocumentHandlerEnabled` is true.
 *
 * Note: This ViewModel is currently retrieved at the Activity scope level. This works fine as long
 * as user do not switch profiles within the activity.
 *
 * The launching user will initialize the PackageManager, and if the user subsequently chooses a
 * different profile (e.g. Work Profile), this instance will still use the PackageManager of the
 * original user. This behavior prevents seamless Work Profile support and needs to be addressed
 * if/when Work Profile support is enabled. This class and its dependencies assume a single user
 * context for the lifecycle of the ViewModel.
 */
class ApprovedDocHandlers(
    private val applicationContext: Context,
    private val injector: Injector<*>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /**
     * [PackageManager] instance for the application context.
     *
     * Note: This package manager is tied to the application context. It will not update if the user
     * profile changes within the activity. This is a known limitation that prevents full Work
     * Profile support at this time.
     */
    private val packageManager: PackageManager = applicationContext.packageManager

    /**
     * A cache to store the results of handler queries. The key is a combination of intent action
     * and MIME type, and the value is a map of [ComponentName] to [HandlerStatus].
     */
    private val cache = MutableStateFlow<Map<String, Map<ComponentName, HandlerStatus>>>(emptyMap())

    /** A set of package names that are approved document handlers, loaded from resources. */
    private val approvedPackages: Set<String> = getPackageNames().toSet()

    /**
     * A set of keys of the cache that are currently being loaded. The key is a combination of
     * intent action and MIME type.
     */
    private val loadingKeys = Collections.synchronizedSet(mutableSetOf<String>())

    private val isMonitoring = AtomicBoolean(false)

    private sealed interface CacheUpdateOp {
        data class Remove(val packageName: String) : CacheUpdateOp

        data class MarkOutdated(val packageName: String) : CacheUpdateOp

        data class Add(val key: String, val handlers: List<ApprovedDocHandler>) : CacheUpdateOp
    }

    companion object {
        private const val TAG = "ApprovedDocHandlers"
        public const val AS_BUTTON_METADATA_KEY = "android.approvedtarget.as_button"

        // TODO(b/464388012): Reference actual intent category when it's available.
        public const val APPROVED_HANDLER_CATEGORY =
            "android.provider.category.APPROVED_DOCUMENT_HANDLER"
    }

    /**
     * Starts monitoring the approved document handler packages for changes.
     *
     * This method is called when the approved document handlers is requested and cached the first
     * time, then it keeps monitoring in the background for changes to the approved document handler
     * packages and updates the cache accordingly.
     */
    private fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            viewModelScope.launch(ioDispatcher) {
                createPackageChangeFlow().collect { op -> applyCacheUpdate(op) }
            }
        }
    }

    /**
     * Applies a [CacheUpdateOp] to the cache sequentially.
     *
     * @param op The [CacheUpdateOp] to apply.
     */
    private fun applyCacheUpdate(op: CacheUpdateOp) {
        when (op) {
            is CacheUpdateOp.Remove -> {
                updateCacheForPackage(op.packageName) { status ->
                    status.copy(isSupported = false, isOutdated = false)
                }
            }
            is CacheUpdateOp.MarkOutdated -> {
                updateCacheForPackage(op.packageName) { status -> status.copy(isOutdated = true) }
            }
            is CacheUpdateOp.Add -> {
                cache.update { currentCache ->
                    val newHandlersMap = mutableMapOf<ComponentName, HandlerStatus>()
                    approvedPackages.forEach { packageName ->
                        newHandlersMap[ComponentName(packageName, "")] =
                            HandlerStatus(isSupported = false, isOutdated = false)
                    }
                    op.handlers.forEach { handler ->
                        newHandlersMap[handler.componentName] =
                            HandlerStatus(isSupported = true, isOutdated = false, handler = handler)
                    }
                    currentCache + (op.key to newHandlersMap)
                }
            }
        }
    }

    private fun updateCacheForPackage(
        packageName: String,
        updateStatus: (HandlerStatus) -> HandlerStatus,
    ) {
        cache.update { currentCache ->
            currentCache.mapValues { (_, handlers) ->
                handlers.mapValues { (component, status) ->
                    if (component.packageName == packageName) {
                        updateStatus(status)
                    } else {
                        status
                    }
                }
            }
        }
    }

    /**
     * Creates a [Flow] that emits a [CacheUpdateOp] when an approved document handler package is
     * added, removed, changed, or replaced.
     *
     * @return A [Flow] that emits a [CacheUpdateOp].
     */
    private fun createPackageChangeFlow(): Flow<CacheUpdateOp> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val packageName = intent.data?.schemeSpecificPart
                    val action = intent.action
                    if (packageName != null && action != null && packageName in approvedPackages) {
                        val op =
                            if (action == Intent.ACTION_PACKAGE_REMOVED) {
                                CacheUpdateOp.Remove(packageName)
                            } else {
                                CacheUpdateOp.MarkOutdated(packageName)
                            }
                        trySend(op)
                    }
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        applicationContext.registerReceiver(receiver, filter)
        awaitClose { applicationContext.unregisterReceiver(receiver) }
    }

    /**
     * Retrieves the package names of approved document handlers from RRO.
     *
     * @return An array of package name strings.
     */
    private fun getPackageNames(): Array<String> {
        val approvedDocHandlers: Array<String> =
            applicationContext.resources.getStringArray(R.array.approved_document_handlers)
                ?: emptyArray()
        if (DEBUG) {
            Log.d(
                TAG,
                "ApprovedDocHandlers ${R.array.approved_document_handlers} : " +
                    approvedDocHandlers.contentToString(),
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
                Intent(Intent.ACTION_SEND).apply {
                    type = selectionDetails.mimeTypes()?.firstOrNull() ?: "*/*"
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    if (
                        selectionDetails.mimeTypes() == null ||
                            selectionDetails.mimeTypes().isEmpty()
                    ) {
                        type = "*/*"
                    } else if (selectionDetails.mimeTypes().isNotEmpty()) {
                        type = MimeTypes.findCommonMimeType(selectionDetails.mimeTypes().toList())
                    }
                }
            }
        intent.addCategory(APPROVED_HANDLER_CATEGORY)
        return intent
    }

    /**
     * Queries the [PackageManager] for activities that can handle the given [Intent].
     *
     * @param intent The [Intent] to query for.
     * @return A list of [ApprovedDocHandler] objects.
     */
    private fun queryApprovedHandlers(intent: Intent): List<ApprovedDocHandler> {
        val resolveInfos =
            packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL or PackageManager.GET_META_DATA,
            )

        return convertToHandlers(resolveInfos)
    }

    /**
     * Retrieves a list of approved document handlers that can handle the given selection, and
     * updates the cache with the results.
     *
     * @param selectionDetails The details about the current selection of documents.
     * @return A list of [ApprovedDocHandler]s that can handle the selection.
     */
    fun getApprovedDocHandlers(
        selectionDetails: MenuManager.SelectionDetails
    ): List<ApprovedDocHandler> {
        if (approvedPackages.isEmpty()) {
            return emptyList()
        }

        startMonitoring()

        val intent = createIntentForSelection(selectionDetails)
        val key = "${intent.action}:${intent.type}"

        // Check if this kind of intent is already in the cache, then check if it's outdated.
        // If it's not outdated, return the cached handlers.
        val handlersMap = cache.value[key]
        if (handlersMap != null && handlersMap.values.none { it.isOutdated }) {
            return handlersMap.values.mapNotNull { if (it.isSupported) it.handler else null }
                ?: emptyList()
        }

        // If it's outdated or not in the cache, query the package manager.
        // If it's already loading, don't query again, just wait for the cache to update.
        if (loadingKeys.add(key)) {
            viewModelScope.launch(ioDispatcher) {
                try {
                    val approvedHandlers = queryApprovedHandlers(intent)
                    applyCacheUpdate(CacheUpdateOp.Add(key, approvedHandlers))
                } finally {
                    loadingKeys.remove(key)
                }
            }
        }
        return handlersMap?.values?.mapNotNull { if (it.isSupported) it.handler else null }
            ?: emptyList()
    }

    /**
     * Converts a list of [ResolveInfo] objects to a list of [ApprovedDocHandler] objects.
     *
     * @param resolveInfos The list of [ResolveInfo] objects to convert.
     * @return A list of [ApprovedDocHandler] objects.
     */
    private fun convertToHandlers(resolveInfos: List<ResolveInfo>): List<ApprovedDocHandler> {
        return buildList {
            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo
                if (activityInfo.packageName in approvedPackages) {
                    val componentName = ComponentName(activityInfo.packageName, activityInfo.name)
                    val label = activityInfo.loadLabel(packageManager)?.toString()
                    if (label == null) {
                        Log.w(
                            TAG,
                            "Approved doc handler ${componentName.flattenToString()} has no " +
                                "label, skipping.",
                        )
                        continue
                    }
                    val isButton: Boolean =
                        resolveInfo.activityInfo.metaData?.getBoolean(AS_BUTTON_METADATA_KEY) ==
                            true
                    val icon: Drawable? =
                        if (isButton) activityInfo.loadIcon(packageManager) else null
                    add(ApprovedDocHandler(componentName, label, isButton, icon))
                }
            }
        }
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
                val intent = injector.actions.createApprovedHandlerIntent(component)
                if (intent != null) {
                    item.intent = intent
                } else {
                    // If the intent is null, the handler is not valid.
                    // This can happen when selection is cleared before the menu is updated.
                    toRemove.add(item.itemId)
                }
            } else {
                toRemove.add(item.itemId)
            }
        }

        for (i in toRemove) {
            menu.removeItem(i)
        }

        // Add new handlers. The map now only contains handlers that are not in the menu.
        for (handler in approvedDocHandlersMap.values) {
            val intent = injector.actions.createApprovedHandlerIntent(handler.componentName)
            if (intent == null) {
                // If the intent is null, the handler is not valid.
                // This can happen when selection is cleared before the menu is updated.
                continue
            }
            val item = menu.add(Menu.NONE, View.generateViewId(), Menu.NONE, handler.label)
            item.intent = intent
            if (handler.isButton && handler.icon != null) {
                item.icon = handler.icon
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            } else {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
    }
}
