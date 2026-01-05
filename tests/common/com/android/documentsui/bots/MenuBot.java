/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.bots;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.annotation.LayoutRes;
import android.content.Context;
import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import java.util.Map;

/**
 * A test helper class that provides support for controlling menu items.
 */
public class MenuBot extends Bots.BaseBot {

    public MenuBot(UiDevice device, Context context, int timeout, @LayoutRes Integer layoutId) {
        super(device, context, timeout, layoutId);
    }

    /** Attempts to find the menu item by also scrolling it into view if necessary. */
    private boolean scrollMenuItemIntoView(String label) {
        final UiObject2 item = mDevice.wait(Until.findObject(By.text(label)), /* timeout= */ 3000);
        if (item != null) {
            return true;
        }

        // Can't find the item, attempt to scroll to it instead.
        UiSelector scrollableView = new UiSelector().scrollable(true);
        final UiObject scrollableViewObject = mDevice.findObject(scrollableView);
        if (!scrollableViewObject.exists()) {
            // If there is no scrollable view available then the menu item is just not visible.
            return false;
        }

        UiScrollable contextScroller = new UiScrollable(scrollableView);
        try {
            final UiObject menuItem =
                    contextScroller.getChildByText(
                            new UiSelector().className(TextView.class), label);
            return menuItem != null;
        } catch (UiObjectNotFoundException e) {
            return false;
        }
    }

    public boolean hasMenuItem(String menuLabel) {
        return mDevice.findObject(By.text(menuLabel)) != null;
    }

    public boolean hasMenuItemByDesc(String menuDesc) {
        return mDevice.findObject(By.desc(menuDesc)) != null;
    }

    public void clickMenuItem(String label) throws UiObjectNotFoundException {
        final UiObject2 item = mDevice.wait(Until.findObject(By.text(label)), mTimeout);
        if (item == null) {
            throw new UiObjectNotFoundException("Cannot find the '" + label + "' menu item");
        }
        item.click();
    }

    /** Asserts that some menu items are present (and attempts to scroll to them if not). */
    public void assertPresentMenuItems(Map<String, Boolean> menuStates)
            throws UiObjectNotFoundException {
        for (String key : menuStates.keySet()) {
            boolean exists = scrollMenuItemIntoView(key);
            if (menuStates.get(key)) {
                assertTrue(key + " expected to be shown", exists);
            } else {
                assertFalse(key + " expected not to be shown", exists);
            }
        }
    }
}
