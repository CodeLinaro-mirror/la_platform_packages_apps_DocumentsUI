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
package com.android.documentsui.bots

import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.R
import com.google.android.material.appbar.MaterialToolbar
import org.hamcrest.CoreMatchers.allOf

/**
 * A test helper class that provides support for controlling the peek overlay and making assertions
 * against the state of it.
 */
class PeekBot(device: UiDevice, context: Context, timeout: Int) :
    Bots.BaseBot(device, context, timeout) {
    private val peekOverlayMatcher = withId(R.id.peek_overlay)
    private val peekContainerMatcher =
        allOf(withId(R.id.peek_container), isDescendantOfA(peekOverlayMatcher))
    private val toolbarMatcher =
        allOf(isAssignableFrom(MaterialToolbar::class.java), withId(R.id.peek_toolbar))

    fun assertPeekHidden() {
        onView(peekOverlayMatcher)
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
    }

    fun assertPeekActive() {
        onView(peekContainerMatcher).check(matches(isDisplayed()))
    }

    fun assertHasTitle(title: String) {
        onView(allOf(withText(title), isDescendantOfA(peekContainerMatcher)))
            .check(matches(isDisplayed()))
    }

    fun hide() {
        onView(allOf(withContentDescription("Hide file preview"), isDescendantOfA(toolbarMatcher)))
            .perform(ViewActions.click())
        assertPeekHidden()
    }
}
