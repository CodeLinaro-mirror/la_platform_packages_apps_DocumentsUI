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

package com.android.documentsui.bots;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.android.documentsui.R;

import junit.framework.Assert;

import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * A test helper class that provides support for controlling the UI Breadcrumb
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class BreadBot extends Bots.BaseBot {

    private final String mBreadCrumbId;

    /**
     * Helper view action that, given a linear view, attempts to collect text from all TextView
     * children contained in it.
     */
    // TODO(b:416108180): Consider making it a EqualsToPathAssertion extends ViewAssertion.
    private static ViewAction getTextOfTextViews(final List<String> texts) {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(LinearLayout.class);
            }

            @Override
            public String getDescription() {
                return "Gets text from linear layout inside breadcrumb v2";
            }

            @Override
            public void perform(UiController uiController, View view) {
                if (view.getVisibility() != View.VISIBLE) {
                    return;
                }
                LinearLayout linearLayout = (LinearLayout) view;
                for (int i = 0; i < linearLayout.getChildCount(); i++) {
                    View child = linearLayout.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView textView = (TextView) child;
                        texts.add(textView.getText().toString());
                    }
                }
            }
        };
    }

    public BreadBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
        mBreadCrumbId = mTargetPackage + ":id/horizontal_breadcrumb";
    }

    public void clickItem(String label) {
        findHorizontalEntry(label).click();
    }

    public void assertItemsPresent(String... items) {
        Predicate<String> checker = this::hasHorizontalEntry;
        assertItemsPresent(items, checker);
    }

    private void assertItemsPresent(String[] items, Predicate<String> predicate) {
        List<String> absent = new ArrayList<>();
        for (String item : items) {
            if (!predicate.test(item)) {
                absent.add(item);
            }
        }
        if (!absent.isEmpty()) {
            Assert.fail("Expected iteams " + Arrays.asList(items)
                    + ", but missing " + absent);
        }
    }

    private boolean hasHorizontalEntry(String label) {
        return findHorizontalEntry(label) != null;
    }

    @SuppressWarnings("unchecked")
    private UiObject2 findHorizontalEntry(String label) {
        UiObject2 breadcrumb = mDevice.findObject(By.res(mBreadCrumbId));
        return breadcrumb.findObject(By.text(label));
    }

    /**
     * Returns the full path displayed in breadcrumb v2, providing it is visible.
     *
     * @return A list of strings extracted from breadcrumb v2 TextView elements.
     */
    public List<String> getBreadcrumbV2Path() {
        List<String> collectedTexts = new ArrayList<>();
        onView(withId(R.id.breadcrumb_path_holder)).perform(getTextOfTextViews(collectedTexts));
        return collectedTexts;
    }
}
