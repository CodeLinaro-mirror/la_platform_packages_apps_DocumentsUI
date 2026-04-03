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
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.util.HumanReadables
import java.util.concurrent.TimeoutException
import org.hamcrest.Matcher

/**
 * An action that waits until a document matching the given matcher is gone from the given
 * RecyclerView. It will attempt to scroll through the RecyclerView's items to see if the item
 * exists. If timeoutMs is 0, it will check the RecyclerView exactly once. Typical usage:
 *
 *  <pre>
 *   onView(withId(R.id.rec_view_id)).perform(WaitUntilGoneFromRecyclerView(matcher, 500L))
 *  </pre>
 */
class WaitUntilGoneFromRecyclerView(
    private val matcher: Matcher<View>,
    private val timeoutMs: Long = 500L,
) : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return isAssignableFrom(RecyclerView::class.java)
    }

    override fun getDescription(): String {
        return "wait up to ${timeoutMs}ms for the view matching $matcher to be gone from " +
            " RecyclerView"
    }

    override fun perform(uiController: UiController, view: View) {
        val endTime = System.currentTimeMillis() + timeoutMs

        while (true) {
            try {
                // Try to scroll to the child. If this succeeds, the item still exists.
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(matcher)
                    .perform(uiController, view)
            } catch (_: Exception) {
                // The item was not found in the adapter.
                return
            }

            if (System.currentTimeMillis() >= endTime) {
                break
            }

            // If found, wait a bit and retry until timeout.
            uiController.loopMainThreadForAtLeast(100L)
        }

        throw PerformException.Builder()
            .withActionDescription(description)
            .withCause(
                TimeoutException(
                    "Waited ${timeoutMs}ms for view matching $matcher to be gone from RecyclerView"
                )
            )
            .withViewDescription(HumanReadables.describe(view))
            .build()
    }
}
