/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.documentsui.actions

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.util.HumanReadables
import java.util.concurrent.TimeoutException
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher

/**
 * An action that waits until a view matching the given matcher exists within a RecyclerView. It
 * will attempt to scroll through the RecyclerView's items to find a match. This differs from
 * WaitUntilVisible which should be used only if the view already exists in the RecyclerView.
 * Typical use:
 *
 *  <pre>
 *   onView(withId(R.id.rec_view_id)).perform(WaitUntilExists(matcher, 500L))
 *  </pre>
 */
class WaitUntilExistsInRecyclerView(
    private val matcher: Matcher<View>,
    private val timeoutMs: Long = 500L,
) : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return CoreMatchers.allOf(
            CoreMatchers.any(View::class.java),
            CoreMatchers.instanceOf(RecyclerView::class.java),
        )
    }

    override fun getDescription(): String {
        return "wait up to ${timeoutMs}ms for the view matching $matcher to exist in RecyclerView"
    }

    override fun perform(uiController: UiController, view: View) {
        val recyclerView = view as RecyclerView
        val endTime = System.currentTimeMillis() + timeoutMs

        do {
            val adapter = recyclerView.adapter
            if (adapter != null) {
                for (i in 0 until adapter.itemCount) {
                    // Scroll to the next item and check if the view is present.
                    recyclerView.scrollToPosition(i)
                    uiController.loopMainThreadUntilIdle()

                    for (j in 0 until recyclerView.childCount) {
                        if (matcher.matches(recyclerView.getChildAt(j))) {
                            return
                        }
                    }
                }
            }
            uiController.loopMainThreadForAtLeast(50L)
        } while (System.currentTimeMillis() < endTime)

        throw PerformException.Builder()
            .withActionDescription(description)
            .withCause(
                TimeoutException("Waited ${timeoutMs}ms for view matching $matcher in RecyclerView")
            )
            .withViewDescription(HumanReadables.describe(view))
            .build()
    }
}
