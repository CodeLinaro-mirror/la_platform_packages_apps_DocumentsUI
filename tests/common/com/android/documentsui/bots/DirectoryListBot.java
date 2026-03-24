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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSubstring;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.actions.TouchscreenLongTapActionKt.touchscreenLongTap;
import static com.android.documentsui.actions.TouchscreenTapActionKt.touchscreenTap;
import static com.android.documentsui.bots.Matchers.documentMatcher;
import static com.android.documentsui.bots.Matchers.firstDocumentMatcher;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;

import android.annotation.LayoutRes;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.R;
import com.android.documentsui.actions.RightClickActionKt;
import com.android.documentsui.actions.WaitUntilDoesNotExist;
import com.android.documentsui.actions.WaitUntilExists;
import com.android.documentsui.actions.WaitUntilExistsInRecyclerView;
import com.android.documentsui.actions.WaitUntilGone;
import com.android.documentsui.actions.WaitUntilGoneFromRecyclerView;
import com.android.documentsui.actions.WaitUntilVisible;

import junit.framework.AssertionFailedError;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

import javax.annotation.Nullable;

/**
 * A test helper class that provides support for controlling directory list and making assertions
 * against the state of it.
 */
public class DirectoryListBot extends Bots.BaseBot {

    private static final int MAX_LAYOUT_LEVEL = 10;
    private static final int MAX_SEARCH_SWIPES = 1000;

    private final String mDirContainerId;
    private final String mDirListId;
    private final String mItemRootId;
    private final String mPreviewId;
    private final String mGridSelectionRegionId;
    private final String mListSelectionRegionId;

    private final UiAutomation mAutomation;

    /**
     * Espresso matcher that can be used to assert whether or not there are exactly {@code
     * expectedCount} items in the directory list (or grid) with the specified filename. Note that
     * this does not scroll the RecyclerView: the items must be already visible.
     *
     * @param filename the filename to look for.
     * @param expectedCount the number of times to expect the filename.
     */
    public static Matcher<View> withDisplayedFilenameCount(
            final String filename, final int expectedCount) {
        return new BoundedDiagnosingMatcher<View, RecyclerView>(RecyclerView.class) {
            @Override
            protected boolean matchesSafely(
                    RecyclerView recyclerView, Description mismatchDescription) {
                final ViewGroup viewGroup = (ViewGroup) recyclerView;
                int matchingFilenames = 0;

                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    if (hasDescendant(withText(equalTo(filename))).matches(child)) {
                        matchingFilenames++;
                    }
                }

                if (matchingFilenames == expectedCount) {
                    return true;
                } else {
                    mismatchDescription.appendText(
                            "actual count of matching filenames was: " + matchingFilenames);
                    return false;
                }
            }

            @Override
            public void describeMoreTo(Description description) {
                description.appendText(
                        "with count:" + expectedCount + " filenames matching: " + filename);
            }
        };
    }

    public DirectoryListBot(
            UiDevice device,
            UiAutomation automation,
            Context context,
            long timeout,
            @LayoutRes Integer layoutId) {
        super(device, context, timeout, layoutId);
        mAutomation = automation;
        mDirContainerId = mTargetPackage + ":id/container_directory";
        mDirListId = mTargetPackage + ":id/dir_list";
        mItemRootId = mTargetPackage + ":id/item_root";
        mPreviewId = mTargetPackage + ":id/preview_icon";
        mListSelectionRegionId = mTargetPackage + ":id/icon";
        mGridSelectionRegionId =
                mTargetPackage + (isUseMaterial3FlagEnabled() ? ":id/thumbnail" : ":id/icon");
    }

    public void assertDocumentsCount(int count) throws UiObjectNotFoundException {
        UiObject docsList = findDocumentsList();
        assertEquals(count, docsList.getChildCount());
    }

    /**
     * Asserts that the summary column for a given document has the expected text. This method will
     * scroll the list/grid if necessary to find the document.
     *
     * @param label The display name of the document file.
     * @param expectedSummary The text expected to be in the summary column.
     */
    public void assertDocumentSummary(String label, String expectedSummary) {
        // First, ensure the document is scrolled into view.
        waitForDocument(label);

        // Find the title element for the document.
        UiObject2 titleElement = mDevice.findObject(By.text(label));
        assertNotNull("Could not find document with label: " + label, titleElement);

        // Traverse up the view hierarchy to find the root of the list item the 'item_root'.
        UiObject2 itemRoot = findItemRoot(titleElement);
        assertEquals("Could not find item_root view", mItemRootId, itemRoot.getResourceName());

        // Now, find the summary TextView within that item's hierarchy.
        UiObject2 summaryView = itemRoot.findObject(By.textContains(expectedSummary));

        // Assert that the summary view exists and its text is correct.
        assertNotNull(
                "Summary view not found for document: '"
                        + label
                        + "' summary: '"
                        + expectedSummary
                        + "'",
                summaryView);
    }

    /**
     * Asserts that target objects, identified by objectResourceIds, within the bounds of the
     * document item, found by its label, become (or are already) visible.
     *
     * @param label The text label of the document item to find.
     * @param objectResourceIds The resource IDs of the objects expected to be on the document.
     */
    public void assertObjectsEventuallyAppearOnDocument(
            String label, @IdRes int... objectResourceIds) throws AssertionFailedError {
        // Wait for the document to exist first.
        waitForDocument(label);
        for (int id : objectResourceIds) {
            // Check each object appears on the document.
            onView(allOf(withId(id), isDescendantOfA(documentMatcher(label))))
                    .perform(new WaitUntilVisible(mTimeout));
        }
    }

    /**
     * Asserts that target objects, identified by objectResourceIds, within the bounds of the
     * document item, found by its label, become (or are already) hidden.
     *
     * @param label The text label of the document item to find.
     * @param objectResourceIds The resource IDs of the objects expected to disappear from the
     *     document.
     */
    public void assertObjectsEventuallyHiddenOnDocument(
            String label, @IdRes int... objectResourceIds) {
        // Find document to exist first.
        waitForDocument(label);
        for (int id : objectResourceIds) {
            // Check each object disappears from the document.
            onView(allOf(withId(id), isDescendantOfA(documentMatcher(label))))
                    .perform(new WaitUntilGone(mTimeout));
        }
    }

    /**
     * Asserts that the sync state icons are not visible on the document.
     *
     * @param label The display name of the document file.
     */
    public void assertDocumentSyncIconsNotVisible(String label) {
        assertObjectsEventuallyHiddenOnDocument(
                label,
                android.R.id.progress,
                R.id.sync_error_icon,
                R.id.upload_icon,
                R.id.progress_tick_icon);
    }

    /** Asserts that the document with the given label is disabled. */
    public void assertDocumentDisabled(String label) throws AssertionFailedError {
        findDocument(label).check(matches(isNotEnabled()));
    }

    /** Asserts that the document with the given label is enabled. */
    public void assertDocumentEnabled(String label) throws AssertionFailedError {
        findDocument(label).check(matches(isEnabled()));
    }

    public void assertDocumentsCountOnList(boolean exists, int count)
            throws UiObjectNotFoundException {
        UiObject docsList = findDocumentsList();
        assertEquals(exists, docsList.exists());
        if (docsList.exists()) {
            assertEquals(count, docsList.getChildCount());
        }
    }

    public void assertHasMessage(String expected) throws UiObjectNotFoundException {
        UiObject messageTextView = findHeaderMessageTextView();
        String msg = String.valueOf(expected);
        assertEquals(msg, messageTextView.getText());
    }

    public void assertHasMessage(boolean expected) throws UiObjectNotFoundException {
        UiObject messageTextView = findHeaderMessageTextView();
        if (expected) {
            assertTrue(messageTextView.exists());
        } else {
            assertFalse(messageTextView.exists());
        }
    }

    public void assertHasMessageButtonText(String expected) throws UiObjectNotFoundException {
        UiObject button = findHeaderMessageButton();
        String msg = String.valueOf(expected);
        assertEquals(msg.toUpperCase(), button.getText().toUpperCase());
    }

    public void clickMessageButton() throws UiObjectNotFoundException {
        UiObject button = findHeaderMessageButton();
        button.click();
    }

    /**
     * Checks against placeholder text. Placeholder can be Empty page, No results page, or the
     * "Hourglass" page (ie. something-went-wrong page).
     */
    public void waitAndAssertPlaceholderMessageText(String message)
            throws UiObjectNotFoundException {
        final UiObject messageTextView = findPlaceholderMessageTextView();
        assertTrue(messageTextView.exists());
        assertEquals(message, messageTextView.getText());
    }

    /** Checks that the no results page is displayed. */
    public void waitAndAssertNoResultsMessage(String rootTitle) throws UiObjectNotFoundException {
        String message;
        if (isUseMaterial3FlagEnabled()) {
            message = mContext.getString(getRes(R.string.no_results));
        } else {
            // When the flag is off, the message includes the root title.
            message = String.format(mContext.getString(R.string.no_results), rootTitle);
        }

        waitAndAssertPlaceholderMessageText(message);
    }

    private UiObject findHeaderMessageTextView() {
        return findObject(mDirContainerId, mTargetPackage + ":id/message_textview");
    }

    private UiObject findHeaderMessageButton() {
        return findObject(mDirContainerId, mTargetPackage + ":id/dismiss_button");
    }

    private UiObject findPlaceholderMessageTextView() throws UiObjectNotFoundException {
        final String childResourceId = mTargetPackage + ":id/message";
        scrollIntoView(new UiSelector().resourceId(mDirContainerId), childResourceId);
        return findObject(mDirContainerId, childResourceId);
    }

    public void waitForHolderMessage() throws UiObjectNotFoundException {
        if (!findPlaceholderMessageTextView().waitForExists(mTimeout)) {
            throw new UiObjectNotFoundException("Holder message not found after timeout");
        }
    }

    /** Open the document with the given label. */
    public void openDocument(String label) {
        actionOnDocumentItem(label, touchscreenTap());
    }

    /**
     * Selects the given document, identified by its label, assuming that it is not already
     * selected. It does not change the selectedness of other documents.
     *
     * @param label The filename of the document
     * @param numSelected Which nth document it is. The number corresponding to "n selected"
     */
    public void selectDocument(String label, int numSelected) throws UiObjectNotFoundException {
        // Wait for document to exist first.
        waitForDocument(label);

        // Long tap on the selection hotspot.
        onView(allOf(withId(getSelectionHotspotId()), isDescendantOfA(documentMatcher(label))))
                .perform(touchscreenLongTap());

        // Check the selection was successful.
        assertSelection(numSelected);
    }

    /**
     * Select the first document that has a selectable region in the list or grid view. This method
     * assumes that no documents are already selected.
     */
    public void selectFirstDocument() {
        // Wait for the 0th document to exist first.
        waitForFirstDocument();

        // Long tap on the selection hotspot.
        onView(allOf(withId(getSelectionHotspotId()), isDescendantOfA(firstDocumentMatcher())))
                .perform(touchscreenLongTap());

        // Check the selection was successful.
        assertSelection(1);
    }

    private int getSelectionHotspotId() {
        return (mBots.main.isInGridMode() && isUseMaterial3FlagEnabled())
                ? R.id.thumbnail
                : R.id.icon;
    }

    private BySelector getSelectionRegionSelector() {
        BySelector selectionRegionSelector = By.res(mGridSelectionRegionId);
        if (mDevice.findObject(selectionRegionSelector) == null) {
            selectionRegionSelector = By.res(mListSelectionRegionId);
        }
        return selectionRegionSelector;
    }

    /** Finds a list item's (whose text has the given label) selection hotspot. */
    public UiObject2 findSelectionHotspot(String label) throws UiObjectNotFoundException {
        return findItemAndSelectionHotspot(label)[1];
    }

    /** Finds a list item (whose text has the given label) and the selection hotspot within it. */
    public UiObject2[] findItemAndSelectionHotspot(String label) throws UiObjectNotFoundException {
        final BySelector list = By.res(mDirListId);

        BySelector selector = By.hasChild(By.text(label));

        final UiSelector docList = findDocumentsListSelector();
        new UiScrollable(docList).scrollIntoView(new UiSelector().text(label));

        final BySelector selectionRegionSelector = getSelectionRegionSelector();
        UiObject2 parent = mDevice.findObject(list).findObject(selector);
        UiObject2 selectionHotspot = null;
        for (int i = 1; i <= MAX_LAYOUT_LEVEL; i++) {
            parent = parent.getParent();
            selectionHotspot = parent.findObject(selectionRegionSelector);
            if (selectionHotspot != null) {
                break;
            }
        }
        return new UiObject2[] {parent, selectionHotspot};
    }

    /** Clicks the "X" cancel selection button. */
    public void clearSelection() {
        boolean useMaterial3 = isUseMaterial3FlagEnabled();
        int parentId = useMaterial3 ? R.id.selection_bar : androidx.appcompat.R.id.action_mode_bar;
        int contentDescription = useMaterial3 ? R.string.clear_selection : android.R.string.cancel;
        ViewInteraction interaction =
                onView(
                        allOf(
                                withContentDescription(contentDescription),
                                isDescendantOfA(withId(parentId))));

        if (useMaterial3) {
            interaction.perform(clickAndRetryOnLongPress());
        } else {
            // For non-material 3 a long press will still trigger a click.
            interaction.perform(ViewActions.click());
        }
    }

    public void pasteFilesFromClipboard() {
        mDevice.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);
    }

    public UiObject2 getSnackbar(String message) {
        return mDevice.wait(Until.findObject(By.text(message)), mTimeout);
    }

    /**
     * Wait for the document with the given label to exist in the directory list and scrolls it into
     * view. Uses the default timeout.
     */
    public void waitForDocument(String label) {
        waitForDocument(label, mTimeout);
    }

    /**
     * Wait for the document with the given label to exist in the directory list and scrolls it into
     * view. If timeoutMs is 0, the document will not be waited on.
     */
    public void waitForDocument(String label, Long timeout) {
        // Wait for the document to exist in the directory list.
        onView(withId(R.id.dir_list))
                .perform(new WaitUntilExistsInRecyclerView(documentMatcher(label), timeout));
    }

    /**
     * Wait for the 0th document to exist in the directory list and scrolls it into view. Uses the
     * default timeout.
     */
    public void waitForFirstDocument() {
        // Wait for at least one document to exist in the directory list.
        onView(withId(R.id.dir_list))
                .perform(new WaitUntilExistsInRecyclerView(withId(R.id.item_root), mTimeout));
    }

    /**
     * Returns a ViewInteraction representing the document with the given label, waiting for it and
     * scrolling if necessary to ensure that it exists and is completely visible.
     */
    public ViewInteraction findDocument(String label) {
        waitForDocument(label);
        return onView(documentMatcher(label));
    }

    /**
     * Wait until a document with the given label is gone from the directory list, scrolling if
     * necessary. This is different to performing the action {@code WaitUntilGone} because that
     * assumes the document continues to exist but becomes hidden.
     */
    public void waitUntilDocumentDoesNotExist(String label) {
        onView(withId(R.id.dir_list))
                .perform(new WaitUntilGoneFromRecyclerView(documentMatcher(label), mTimeout));
    }

    /** Perform the specified action on the item with the specified label in the directory list. */
    public void actionOnDocumentItem(String label, ViewAction action) {
        waitForDocument(label);
        var dirListMatcher = allOf(withId(R.id.dir_list), isDisplayed());
        onView(dirListMatcher)
                .perform(RecyclerViewActions.actionOnItem(documentMatcher(label), action));
    }

    /** Returns the bounds of the UiObject representing the document with the given label. */
    public Rect findDocumentBounds(String label) throws UiObjectNotFoundException {
        return findDocumentUiObject(label).getVisibleBounds();
    }

    /** Returns the UiObject representing the document with the given label. */
    private UiObject findDocumentUiObject(String label) throws UiObjectNotFoundException {
        // Wait for the document to exist first.
        waitForDocument(label);
        final UiSelector docList = findDocumentsListSelector();
        scrollIntoView(docList, label);
        if (mBots.main.isInGridMode()) {
            // For grid items, the label still might be out of view, even if the element is in
            // view. Scroll again to ensure the label is visible.
            getScrollable(docList).scrollTextIntoView(label);
        }
        return mDevice.findObject(docList.childSelector(new UiSelector().text(label)));
    }

    /** Return true if the document with the given label exists in the directory list. */
    public Boolean hasDocument(String label) {
        try {
            waitForDocument(label, /* timeout= */ 0L);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean hasDocumentPreview(String label) {
        final BySelector list = By.res(mDirListId);

        UiObject2 parent = findItemRoot(mDevice.findObject(list).findObject(By.text(label)));
        return parent != null && parent.hasObject(By.res(mPreviewId));
    }

    /** Traverse up the view hierarchy to find the root of the list item the 'item_root'. */
    private @Nullable UiObject2 findItemRoot(UiObject2 initialParent) {
        UiObject2 parent = initialParent;
        for (int i = 0; i < MAX_LAYOUT_LEVEL; i++) {
            if (mItemRootId.equals(parent.getResourceName())) {
                return parent;
            }
            parent = parent.getParent();
            assertNotNull("Failed to find item_root parent for document: " + initialParent, parent);
        }

        return null;
    }

    /** Assert the document with the specified {@code label} is the focused item. */
    public void assertDocumentHasFocus(String label) {
        findDocument(label).check(matches(hasFocus()));
    }

    public void assertFirstDocumentHasFocus() throws UiObjectNotFoundException {
        final UiSelector docList = findDocumentsListSelector();

        // Wait for the first list item to appear
        UiObject doc = new UiObject(docList.childSelector(new UiSelector()));
        if (!doc.waitForExists(mTimeout)) {
            throw new UiObjectNotFoundException("First document not found after timeout");
        }

        assertTrue(doc.isFocused());
    }

    /** Returns whether a document is focused. */
    public boolean anyDocumentHasFocus() throws UiObjectNotFoundException {
        UiObject2 o = mDevice.findObject(By.res(mDirListId));
        return (o != null) && (o.findObject(By.focused(true)) != null);
    }

    public UiObject findDocumentsList() {
        return findObject(mDirContainerId, mDirListId);
    }

    private UiSelector findDocumentsListSelector() {
        return new UiSelector()
                .resourceId(mDirContainerId)
                .childSelector(new UiSelector().resourceId(mDirListId));
    }

    public void assertHasFocus() {
        assertHasFocus(mDirListId);
    }

    /** Assert that 0 things are selected. */
    public void assertNoSelection() {
        onView(isRoot())
                .perform(
                        new WaitUntilDoesNotExist(
                                allOf(withSubstring(" selected"), isDisplayed()), mTimeout));
    }

    /** Assert that N things are selected, for positive N. */
    public void assertSelection(int numSelected) {
        if (numSelected == 0) {
            assertNoSelection();
            return;
        }
        var selectionText = numSelected + " selected";
        onView(isRoot())
                .perform(
                        new WaitUntilExists(
                                allOf(withText(selectionText), isDisplayed()), mTimeout));
    }

    public void assertOrder(String[] dirs, String[] files) throws UiObjectNotFoundException {
        long remaining = mTimeout;
        if (remaining < 0) {
            remaining = 0;
        }
        // 1048576 is (1 << 20), a power of two close to one million. The value is basically
        // arbitrary. We just want our sleeps to start as a small fraction of the default timeout,
        // but double in length each iteration.
        long retryTimeout = remaining / 1048576;
        if ((retryTimeout < 1) && (remaining != 0)) {
            retryTimeout = 1;
        }

        // Check that the bounding boxes for the (dirs ++ files) UI items are ordered. Use
        // exponential backoff in case we have to wait (without explicit synchronization) for a
        // worker thread to sort things (and having that trigger UI changes).
        //
        // Loop invariants:
        //  • (0 <= retryTimeout) and (retryTimeout <= remaining)
        //  • (0 < retryTimeout) unless (0 == remaining), in which case (0 == retryTimeout)
        //  • remaining decreases on each complete (no return or fail) iteration
        while (true) {
            try {
                for (int i = 0; i < dirs.length - 1; ++i) {
                    checkOrder(dirs[i], dirs[i + 1]);
                }

                if (dirs.length > 0 && files.length > 0) {
                    checkOrder(dirs[dirs.length - 1], files[0]);
                }

                for (int i = 0; i < files.length - 1; ++i) {
                    checkOrder(files[i], files[i + 1]);
                }

                return;
            } catch (NotInOrderException nioe) {
                if (remaining <= 0) {
                    fail(nioe.getMessage());
                }
                SystemClock.sleep(retryTimeout);

                remaining -= retryTimeout;
                retryTimeout *= 2;
                if ((retryTimeout > remaining) || (retryTimeout <= 0)) {
                    retryTimeout = remaining;
                }
            }
        }
    }

    /** Right-clicks on the document with the given label. */
    public void rightClickDocument(String label) {
        actionOnDocumentItem(label, RightClickActionKt.rightClick());
    }

    /** Sends a right click at the given point. */
    public void rightClick(Point point) {
        MotionEvent motionDown =
                getTestRightClickMotionEvent(MotionEvent.ACTION_DOWN, point.x, point.y);
        mAutomation.injectInputEvent(motionDown, true);
        SystemClock.sleep(100);

        MotionEvent motionUp =
                getTestRightClickMotionEvent(MotionEvent.ACTION_UP, point.x, point.y);

        mAutomation.injectInputEvent(motionUp, true);
    }

    private UiScrollable getScrollable(UiSelector selector) {
        UiScrollable scrollable = new UiScrollable(selector);
        // In drawer_layout the file list occupied the whole app window height because of
        // the CollapsingToolbarLayout, so "scrollIntoView" might start swipe on the
        // app bar or breadcrumb area, which doesn't actually trigger the scroll of the list.
        // Setting a dead zone here to avoid starting swipe on these areas.
        // Note: 0.2 is just an estimated percentage here (the dead zone ratio on 4 sides, we
        // only need dead zone for the top and the bottom, but there's no API to set specific
        // sides).
        final boolean docListCoveredByOtherViews = inDrawerLayout() && isUseMaterial3FlagEnabled();
        if (docListCoveredByOtherViews) {
            scrollable.setSwipeDeadZonePercentage(0.2);
        }
        // scrollIntoView will only attempt to scroll MaxSearchSwipes number of times.
        // For directories containing a large number of files this default value is inadequate.
        // Replace the default value with a larger one that will ensure the file
        // is found if it is present.
        scrollable.setMaxSearchSwipes(MAX_SEARCH_SWIPES);
        return scrollable;
    }

    /**
     * Scroll the provided on-screen element for text defined by {@code label}.
     *
     * <p>Wrapper around {@link UiScrollable#scrollIntoView(UiSelector)} with additional dead-zone
     * and max search swipe configuration to handle various device form factors and UI layouts.
     *
     * @param selector A UiSelector that specifies the scrollable container view itself.
     * @param label the text to search for when scrolling.
     * @throws UiObjectNotFoundException if the text matching {@code label} is not found.
     */
    private void scrollIntoView(UiSelector selector, String label)
            throws UiObjectNotFoundException {
        getScrollable(selector).scrollIntoView(new UiSelector().text(label));
    }

    private void checkOrder(String first, String second)
            throws NotInOrderException, UiObjectNotFoundException {
        final UiObject firstObj = findDocumentUiObject(first);
        final UiObject secondObj = findDocumentUiObject(second);

        final int layoutDirection = mContext.getResources().getConfiguration().getLayoutDirection();
        final Rect firstBound = firstObj.getVisibleBounds();
        final Rect secondBound = secondObj.getVisibleBounds();
        if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            if (firstBound.bottom < secondBound.top || firstBound.right < secondBound.left) {
                return;
            }
        } else {
            if (firstBound.bottom < secondBound.top || firstBound.left > secondBound.right) {
                return;
            }
        }
        throw new NotInOrderException(first + " is not located before " + second);
    }

    private static class NotInOrderException extends Exception {
        NotInOrderException(String m) {
            super(m);
        }
    }
}
