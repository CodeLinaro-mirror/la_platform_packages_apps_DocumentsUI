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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.allOf;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.matcher.ViewMatchers;

import com.android.documentsui.R;

import junit.framework.AssertionFailedError;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Support methods for working with Espresso related matchers 'n stuff.
 */
public final class Matchers {

    private Matchers() {}

    public static boolean present(Matcher<View> matcher) {
        return present(onView(matcher), isDisplayed());
    }

    public static boolean present(ViewInteraction vi, Matcher<View> matcher) {
        try {
            vi.check(matches(matcher));
            vi.check(matches(isDisplayed()));
            return true;
        }
        // Catch AssertionFailedError because vi.check(matches(isDisplayed())) throws that if
        // assertion fails.
        catch (Exception|AssertionFailedError e) {
            return false;
        }
    }

    /**
     * Return a matcher for the document root container (item_root) with a TextView descendant with
     * the given label.
     */
    public static Matcher<View> documentMatcher(String label) {
        return allOf(withId(R.id.item_root), ViewMatchers.hasDescendant(withText(label)));
    }

    /** Return a matcher for the first document root container (item_root) in the directory list. */
    public static Matcher<View> firstDocumentMatcher() {
        return allOf(withId(R.id.item_root), documentAtPositionMatcher(0));
    }

    /**
     * Returns a matcher that matches the child at the specified position within a parent matched by
     * the given matcher.
     */
    public static Matcher<View> documentAtPositionMatcher(final int position) {
        return new TypeSafeMatcher<>() {
            private final Matcher<View> mParentMatcher = withId(R.id.dir_list);

            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                mParentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup
                        && mParentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
