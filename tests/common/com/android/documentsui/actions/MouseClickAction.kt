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

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions

/**
 * A ViewAction to perform a mouse click with button press events. This is different to the default
 * ViewActions.click() as that uses InputDevice.SOURCE_UNKNOWN which does not register as a click
 * from a mouse and, unlike this method, will return false for `Events.isMousyEvent()`. Use this to
 * test mouse tap behavior rather than touch tap behavior.
 */
fun mouseClick(): ViewAction {
    return ViewActions.actionWithAssertions(
        GeneralClickAction(
            Tap.SINGLE,
            GeneralLocation.VISIBLE_CENTER,
            Press.PINPOINT,
            InputDevice.SOURCE_MOUSE,
            MotionEvent.BUTTON_PRIMARY,
        )
    )
}
