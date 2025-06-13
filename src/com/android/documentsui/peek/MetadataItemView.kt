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
package com.android.documentsui.peek

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.android.documentsui.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

class MetadataItemView(context: Context, title: String, layoutType: LayoutType) :
    FrameLayout(context) {
    enum class LayoutType {
        TOP_CARD,
        MIDDLE_CARD,
        BOTTOM_CARD
    }

    init {
        @Suppress("ktlint:standard:comment-wrapping")
        LayoutInflater.from(context).inflate(R.layout.peek_metadata_item_layout, /* root= */ this)

        layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.size_extra_small_1)
            }
        val smallRadius = resources.getDimension(R.dimen.size_extra_small_2)
        val largeRadius = resources.getDimension(R.dimen.size_small_2)

        val shapeAppearanceModel =
            ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(
                    if (layoutType == LayoutType.TOP_CARD) largeRadius else smallRadius
                )
                .setTopRightCornerSize(
                    if (layoutType == LayoutType.TOP_CARD) largeRadius else smallRadius
                )
                .setBottomLeftCornerSize(
                    if (layoutType == LayoutType.BOTTOM_CARD) largeRadius else smallRadius
                )
                .setBottomRightCornerSize(
                    if (layoutType == LayoutType.BOTTOM_CARD) largeRadius else smallRadius
                )
                .build()

        val backgroundDrawable =
            MaterialShapeDrawable(shapeAppearanceModel).apply {
                fillColor =
                    ColorStateList.valueOf(
                        MaterialColors.getColor(
                            this@MetadataItemView,
                            com.google.android.material.R.attr.colorSurfaceBright))
            }

        background = backgroundDrawable

        findViewById<TextView>(R.id.peek_item_title)?.apply { text = title }
    }

    fun setValue(value: String) {
        findViewById<TextView>(R.id.peek_item_value)?.apply { text = value }
    }
}
