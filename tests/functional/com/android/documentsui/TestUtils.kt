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

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import org.junit.Assume

class TestUtils {
    companion object {
        fun dpToPx(dp: Float, metrics: DisplayMetrics?): Float {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics)
        }

        fun pxToDp(px: Float, metrics: DisplayMetrics): Float {
            return TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_DIP, px, metrics)
        }

        /**
         * Checks that the size of the window fulfills the given predicate. If the conditions are
         * not met, throws an AssumptionViolatedException, which causes the test to be halted and
         * ignored.
         */
        fun assumeWindowSizeFulfills(
            windowMgr: WindowManager,
            message: String,
            predicate: (Float, Float) -> Boolean,
        ) {
            val windowMetrics = windowMgr.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val windowWidthDp = bounds.width() / windowMetrics.density
            val windowHeightDp = bounds.height() / windowMetrics.density
            Assume.assumeTrue(
                "Skipping test: window size ${windowWidthDp}dp x ${windowHeightDp}dp " + message,
                predicate(windowWidthDp, windowHeightDp),
            )
        }
    }
}
