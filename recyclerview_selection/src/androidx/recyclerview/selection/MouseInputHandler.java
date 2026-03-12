/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.recyclerview.selection;

import static androidx.core.util.Preconditions.checkArgument;

import android.view.MotionEvent;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.widget.RecyclerView;

import org.jspecify.annotations.NonNull;

/**
 * A MotionInputHandler that provides the high-level glue for mouse driven selection. This class
 * works with {@link RecyclerView}, {@link GestureRouter}, and {@link GestureSelectionHelper} to
 * implement the primary policies around mouse input.
 */
final class MouseInputHandler<K> extends MotionInputHandler<K> {

    private static final String TAG = "MouseInputHandler";

    private final ItemDetailsLookup<K> mDetailsLookup;
    private final OnContextClickListener mOnContextClickListener;
    private final OnItemActivatedListener<K> mOnItemActivatedListener;
    private final FocusDelegate<K> mFocusDelegate;

    MouseInputHandler(
            @NonNull SelectionTracker<K> selectionTracker,
            @NonNull ItemKeyProvider<K> keyProvider,
            @NonNull ItemDetailsLookup<K> detailsLookup,
            @NonNull OnContextClickListener onContextClickListener,
            @NonNull OnItemActivatedListener<K> onItemActivatedListener,
            @NonNull FocusDelegate<K> focusDelegate) {

        super(selectionTracker, keyProvider, focusDelegate);

        checkArgument(detailsLookup != null);
        checkArgument(onContextClickListener != null);
        checkArgument(onItemActivatedListener != null);

        mDetailsLookup = detailsLookup;
        mOnContextClickListener = onContextClickListener;
        mOnItemActivatedListener = onItemActivatedListener;
        mFocusDelegate = focusDelegate;
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if ((MotionEvents.isAltKeyPressed(e) && MotionEvents.isPrimaryMouseButtonPressed(e, true))
                || MotionEvents.isSecondaryMouseButtonPressed(e)) {
            return onRightClick(e);
        }

        return false;
    }

    @Override
    public boolean onScroll(
            @NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        // Don't scroll content window in response to mouse drag
        // If it's two-finger trackpad scrolling, we want to scroll
        return !MotionEvents.isTouchpadScroll(e2);
    }

    // Called when left-clicking on an item and there is an existing selection (which may or may
    // not include that item). We extend / clear / modify the selection (and adjust focus).
    private void onLeftClickWhenSomethingSelected(
            @NonNull MotionEvent e, @NonNull ItemDetails<K> item) {
        checkArgument(item != null);

        if (shouldExtendRange(e)) {
            extendSelectionRange(item);
            return;
        }

        K key = item.getSelectionKey();
        switch (item.classifySelectionHotspot(e)) {
            case ItemDetails.SELECTION_HOTSPOT_OUTSIDE:
                if (!mSelectionTracker.isSelected(key)) {
                    focusItemQuietly(item);
                } else if (mSelectionTracker.deselect(key)) {
                    mFocusDelegate.clearFocus();
                }
                return;

            case ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI:
                if (!mSelectionTracker.isSelected(key)) {
                    selectItem(item);
                } else if (mSelectionTracker.deselect(key)) {
                    mFocusDelegate.clearFocus();
                }
                return;

            case ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO:
                boolean wasTheOnlyThingSelected =
                        mSelectionTracker.isSelected(key)
                                && (mSelectionTracker.getSelection().size() == 1);
                clearSelectionQuietly();
                if (!wasTheOnlyThingSelected) {
                    selectItem(item);
                }
                return;

            case ItemDetails.SELECTION_HOTSPOT_INSIDE_CLEAR_AND_THEN_SET:
                clearSelectionQuietly();
                selectItem(item);
                return;
        }
    }

    // We use onSingleTapUp instead of onSingleTapConfirmed to remove the 300ms delay associated
    // with waiting for a double-tap. Selection happens immediately on the first ACTION_UP.
    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent e) {
        if (MotionEvents.isAltKeyPressed(e) || !MotionEvents.isPrimaryMouseButtonPressed(e, true)) {
            return false;
        }

        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if (item == null) {
            clearSelectionQuietly();
            mFocusDelegate.clearFocus();
            return false;
        }

        if (mSelectionTracker.hasSelection()) {
            onLeftClickWhenSomethingSelected(e, item);
            return true;
        }

        if (mFocusDelegate.hasFocusedItem() && MotionEvents.isShiftKeyPressed(e)) {
            mSelectionTracker.startRange(mFocusDelegate.getFocusedPosition());
            mSelectionTracker.extendRange(item.getPosition());
        } else if (item.classifySelectionHotspot(e) == ItemDetails.SELECTION_HOTSPOT_OUTSIDE) {
            focusItemQuietly(item);
        } else {
            selectItem(item);
        }
        return true;
    }

    private void focusItemQuietly(@NonNull ItemDetails<K> item) {
        clearSelectionQuietly();
        mFocusDelegate.focusItem(item);
    }

    private void clearSelectionQuietly() {
        // We use setItemsSelected(..., false) instead of clearSelection() because clearSelection()
        // triggers ResetManager, which cancels the GestureDetector's double-tap timer. Using a
        // "quiet" clear allows double-clicks to work even when they cause a selection change
        // (e.g. one item is selected and we double-click another unselected item).

        // We must copy the selection before passing it to setItemsSelected, as setItemsSelected
        // will modify the underlying selection set, potentially causing a
        // ConcurrentModificationException if we pass the live set.
        MutableSelection<K> copy = new MutableSelection<>();
        copy.copyFrom(mSelectionTracker.getSelection());
        mSelectionTracker.setItemsSelected(copy, false);
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (MotionEvents.isAltKeyPressed(e) || !MotionEvents.isPrimaryMouseButtonPressed(e, true)) {
            return false;
        }

        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if (item != null) {
            mOnItemActivatedListener.onItemActivated(item, e);
        }
        // DO NOT RETURN TRUE YET. Returning true here makes the `GestureDetector.onTouchEvent`
        // inside `GestureDetectorWrapper.onInterceptTouchEvent` returns true, which let the
        // GestureDetectorWrapper intercept the gesture stream (e.g. the ACTION_UP for the second
        // up event will only reach `GestureDetectorWrapper.onTouchEvent` which is an empty
        // implementation), making GestureDetector not receive the ACTION_UP for the second tap,
        // leaving its internal state stuck in the double click process.
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
        // Return true here for ACTION_UP because that's the last event for double click stream,
        // this makes sure no events in the double click gesture stream is intercepted by the
        // GestureDetectorWrapper (thus no events are routed to its onTouchEvent which is an empty
        // implementation), returning true also makes sure the double click event won't be passed
        // to other onItemTouchListener for RecyclerView.
        return MotionEvents.isActionUp(e);
    }

    private boolean onRightClick(@NonNull MotionEvent e) {
        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if ((item != null) && !mSelectionTracker.isSelected(item.getSelectionKey())) {
            clearSelectionQuietly();
            selectItem(item);
        }

        // We always delegate final handling of the event,
        // since the handler might want to show a context menu
        // in an empty area or some other weirdo view.
        return mOnContextClickListener.onContextClick(e);
    }
}
