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


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewAssertion;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A test helper class that provides support for controlling the UI Breadcrumb
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class BreadBot extends Bots.BaseBot {

    private final String mBreadCrumbId;

    /** A view assertion that extract the path and checks it against the given predicate. */
    private static class PathViewAssertion implements ViewAssertion {
        private final String[] mExpectedPath;
        private final BiPredicate<String, String> mMatcher;

        PathViewAssertion(String[] expectedPath, BiPredicate<String, String> matcher) {
            mExpectedPath = expectedPath;
            mMatcher = matcher;
        }

        private List<String> getBreadcrumbV2Path(ViewGroup parent) {
            List<String> path = new ArrayList<>();
            for (int i = 0; i < parent.getChildCount(); ++i) {
                View child = parent.getChildAt(i);
                if (child instanceof TextView) {
                    path.add(((TextView) child).getText().toString());
                }
            }
            return path;
        }

        @Override
        public void check(View view, NoMatchingViewException noViewFoundException) {
            if (!(view instanceof ViewGroup) || noViewFoundException != null) {
                throw new AssertionFailedError("Failed to locate the view");
            }
            List<String> shownPath = getBreadcrumbV2Path((ViewGroup) view);
            String got = String.join("/", shownPath);
            String want = String.join("/", mExpectedPath);
            if (!mMatcher.test(got, want)) {
                throw new AssertionFailedError("Path '" + want + "' does not match '" + got + "'");
            }
        }
    }

    /**
     * @param expectedPath Path to be compared with the one shown in the breadcrumbs.
     * @return A view assertion that checks if the path is equal to the expected.
     */
    public ViewAssertion pathEqualsTo(String... expectedPath) {
        return new PathViewAssertion(expectedPath, (String got, String want) -> want.equals(got));
    }

    /**
     * @param expectedPath Path to be compared with the one shown in the breadcrumbs.
     * @return A view assertion that checks if the path starts with the expected path.
     */
    public ViewAssertion pathStartsWith(String... expectedPath) {
        return new PathViewAssertion(expectedPath, String::startsWith);
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
}
