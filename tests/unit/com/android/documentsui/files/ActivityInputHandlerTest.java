/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.files;

import static android.view.KeyEvent.KEYCODE_DEL;
import static android.view.KeyEvent.META_ALT_LEFT_ON;
import static android.view.KeyEvent.META_ALT_ON;
import static android.view.KeyEvent.META_CTRL_LEFT_ON;
import static android.view.KeyEvent.META_CTRL_ON;
import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.EnableFlags;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.rules.OverrideFlagsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActivityInputHandlerTest {
    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private ActivityInputHandler mActivityInputHandler;
    private boolean mDeleteOrTrashHappened;
    private boolean mDeleteForeverHappened;

    @Before
    public void setUp() {
        mDeleteOrTrashHappened = false;
        mDeleteForeverHappened = false;
        mActivityInputHandler =
                new ActivityInputHandler(
                        () -> {
                            mDeleteOrTrashHappened = true;
                        },
                        () -> {
                            mDeleteForeverHappened = true;
                        });
    }

    @Test
    public void testDeleteOrTrash() {
        // ALT + BACKSPACE (No Shift)
        KeyEvent event =
                new KeyEvent(
                        0,
                        0,
                        MotionEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DEL,
                        0,
                        KeyEvent.META_ALT_ON);
        assertTrue(mActivityInputHandler.onKeyDown(event.getKeyCode(), event));
        assertTrue(mDeleteOrTrashHappened);
        assertFalse(mDeleteForeverHappened);
    }

    @Test
    public void testDeleteForever() {
        // SHIFT + ALT + BACKSPACE
        KeyEvent event =
                new KeyEvent(
                        0,
                        0,
                        MotionEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DEL,
                        0,
                        KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        assertTrue(mActivityInputHandler.onKeyDown(event.getKeyCode(), event));
        assertFalse(mDeleteOrTrashHappened);
        assertTrue(mDeleteForeverHappened);
    }

    @Test
    public void testDeleteForever_WithForwardDel() {
        // SHIFT + FORWARD_DEL
        KeyEvent event =
                new KeyEvent(
                        0,
                        0,
                        MotionEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_FORWARD_DEL,
                        0,
                        KeyEvent.META_SHIFT_ON);
        assertTrue(mActivityInputHandler.onKeyDown(event.getKeyCode(), event));
        assertFalse(mDeleteOrTrashHappened);
        assertTrue(mDeleteForeverHappened);
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testAltCtrlBackspaceShouldNotDelete() {
        KeyEvent event =
                new KeyEvent(
                        0,
                        0,
                        ACTION_DOWN,
                        KEYCODE_DEL,
                        0,
                        META_ALT_ON | META_ALT_LEFT_ON | META_CTRL_ON | META_CTRL_LEFT_ON);
        assertThat(mActivityInputHandler.onKeyDown(event.getKeyCode(), event)).isFalse();
        assertThat(mDeleteOrTrashHappened).isFalse();
        assertThat(mDeleteForeverHappened).isFalse();
    }
}
