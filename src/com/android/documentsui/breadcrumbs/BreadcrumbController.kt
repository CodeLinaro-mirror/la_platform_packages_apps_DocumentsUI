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
package com.android.documentsui.breadcrumbs

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.android.documentsui.base.SharedMinimal.DEBUG
import java.util.function.IntConsumer

/**
 * Represents a breadcrumb view that shows a path to the currently selected element in the
 * DocumentsUI.
 */
class BreadcrumbController(
    lifeCycleOwner: LifecycleOwner,
    private val model: BreadcrumbModel,
    private val view: BreadcrumbView,
) {

    private var pathClickConsumer: IntConsumer? = null

    init {
        model.pathData.observe(lifeCycleOwner, Observer { newPath -> onModelUpdated(newPath) })
        view.setClickConsumer { index -> pathClickConsumer?.accept(index) }
    }

    private fun onModelUpdated(newValue: List<String>) {
        if (DEBUG) {
            Log.d(TAG, "Controller onModelUpdated event with $newValue")
        }
        view.setPath(newValue.toTypedArray())
    }

    /** Registers the consumer of path item clicks. */
    fun setClickConsumer(consumer: IntConsumer?) {
        pathClickConsumer = consumer
    }

    /** Sets whether the breadcrumb should be visible or not. */
    fun setVisible(visible: Boolean) {
        view.setVisible(visible)
    }

    /**
     * Provides access to the model, so that it can be explicitly assigned values to it. This is due
     * to the fact that we wish this model to be a bridge between two existing models in the
     * DocumentsUI: the directory path, and the path to the selected file.
     */
    fun getModel() = model
}
