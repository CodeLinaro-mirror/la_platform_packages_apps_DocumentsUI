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

package com.android.documentsui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.util.Material3Config.getRes;

import android.platform.test.annotations.EnableFlags;
import android.view.KeyEvent;

import androidx.annotation.IdRes;
import androidx.test.filters.LargeTest;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class KeyboardNavigationUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                docsHelper.createDocument(root, "image/png", "files1.png");
                            });

    // Tests that pressing tab switches focus between the roots and directory listings.
    @Ignore
    @Test
    public void testKeyboard_tab() throws Exception {
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        bots.roots.assertHasFocus();
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        bots.directory.assertHasFocus();
    }

    // Tests that arrow keys do not switch focus away from the dir list.
    @Ignore
    @Test
    public void testKeyboard_arrowsDirList() throws Exception {
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_LEFT);
            bots.directory.assertHasFocus();
        }
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            bots.directory.assertHasFocus();
        }
    }

    @Ignore
    @Test
    public void testKeyboard_tabFocuses() throws Exception {
        bots.roots.closeDrawer();
        if (bots.main.inFixedLayout()) {
            // Tablet devices need to press one more tab since it focuses on root list first
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        }
        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        bots.directory.assertFirstDocumentHasFocus();

        // This should not cause any exceptions
        bots.keyboard.pressKey(KeyEvent.KEYCODE_F);
    }

    // Tests that arrow keys do not switch focus away from the roots list.
    @Test
    public void testKeyboard_arrowsRootsList() throws Exception {

        // Open the drawer so we can ensure root list available even for phones
        bots.roots.openDrawer();

        bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_RIGHT);
            bots.roots.assertHasFocus();
        }
        for (int i = 0; i < 10; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_DPAD_LEFT);
            bots.roots.assertHasFocus();
        }
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3})
    public void testKeyboard_tabCycleInRootsList() throws Exception {
        // Focus the root CoordinatorLayout explicitly before the test to avoid the first
        // Tab press accidentally focus on the root CoordinatorLayout.
        // TODO(b/417871278): remove this after removing the grey overlay.
        onView(withId(R.id.coordinator_layout)).check((view, noViewFoundException) -> {
            if (view != null) {
                view.post(view::requestFocus);
            }
        }).check(matches(hasFocus()));

        // We want to explicitly check the focus inside the nav rail root list in nav rail layout,
        // otherwise, check it in the drawer (container_roots).
        final @IdRes int containerId =
                bots.main.inNavRailLayout()
                        ? getRes(R.id.nav_rail_container_roots)
                        : getRes(R.id.container_roots);

        if (bots.main.inDrawerLayout()) {
            // If drawer layout is used, we need to open drawer first to show all the nav roots.
            bots.roots.openDrawer();
        } else if (bots.main.inNavRailLayout()) {
            // If nav rail layout is used, the first Tab will move the focus to the burger menu
            // inside the nav rail root list.
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
            onView(withId(R.id.nav_rail_burger_menu)).check(matches(hasFocus()));
        }

        // Only check the first 2 items here because we don't want to deal with the divider item
        // (which is also a child of the root list), the first 2 items are guaranteed not to be
        // divider items.
        for (int i = 0; i <= 1; i++) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB);
            bots.roots.assertPositionFocused(containerId, i);
        }
    }
}
