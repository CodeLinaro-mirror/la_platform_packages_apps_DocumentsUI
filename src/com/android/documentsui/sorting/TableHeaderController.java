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

package com.android.documentsui.sorting;

import static com.android.documentsui.flags.Flags.useMaterial3;

import android.view.KeyEvent;
import android.view.View;

import com.android.documentsui.R;

import javax.annotation.Nullable;

/** View controller for table header that associates header cells in table header and columns. */
public final class TableHeaderController implements SortController.WidgetController {
    private final HeaderCell mTitleCell;
    private final HeaderCell mSummaryCell;
    private final HeaderCell mSizeCell;
    private final HeaderCell mFileTypeCell;
    private final HeaderCell mDateCell;
    private final SortModel mModel;
    // We assign this here porque each method reference creates a new object
    // instance (which is wasteful).
    private final View.OnClickListener mOnCellClickListener = this::onCellClicked;
    private final View.OnKeyListener mOnCellKeyListener = this::onCellKeyEvent;
    private final SortModel.UpdateListener mModelListener = this::onModelUpdate;
    private final View mTableHeader;

    private TableHeaderController(SortModel sortModel, View tableHeader) {
        assert (sortModel != null);
        assert (tableHeader != null);

        mModel = sortModel;
        mTableHeader = tableHeader;

        mTitleCell = tableHeader.findViewById(android.R.id.title);
        mSummaryCell = tableHeader.findViewById(android.R.id.summary);
        mSizeCell = tableHeader.findViewById(R.id.size);
        mFileTypeCell = tableHeader.findViewById(R.id.file_type);
        mDateCell = tableHeader.findViewById(R.id.date);

        onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);

        mModel.addListener(mModelListener);
    }

    /** Creates a TableHeaderController. */
    public static @Nullable TableHeaderController create(
            SortModel sortModel, @Nullable View tableHeader) {
        return (tableHeader == null) ? null : new TableHeaderController(sortModel, tableHeader);
    }

    private void onModelUpdate(SortModel model, int updateTypeUnspecified) {
        bindCell(mTitleCell, SortModel.SORT_DIMENSION_ID_TITLE);
        bindCell(mSummaryCell, SortModel.SORT_DIMENSION_ID_SUMMARY);
        bindCell(mSizeCell, SortModel.SORT_DIMENSION_ID_SIZE);
        bindCell(mFileTypeCell, SortModel.SORT_DIMENSION_ID_FILE_TYPE);
        bindCell(mDateCell, SortModel.SORT_DIMENSION_ID_DATE);
    }

    @Override
    public void setVisibility(int visibility) {
        mTableHeader.setVisibility(visibility);
    }

    @Override
    public void destroy() {
        mModel.removeListener(mModelListener);
    }

    private void bindCell(HeaderCell cell, int id) {
        assert (cell != null);
        SortDimension dimension = mModel.getDimensionById(id);

        cell.setTag(dimension);

        cell.onBind(dimension);
        if (dimension.getVisibility() == View.VISIBLE
                && dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
            cell.setOnClickListener(mOnCellClickListener);
            if (useMaterial3()) {
                cell.setSortArrowListeners(mOnCellClickListener, mOnCellKeyListener, dimension);
            }
        } else {
            cell.setOnClickListener(null);
            if (useMaterial3()) cell.setSortArrowListeners(null, null, null);
        }
    }

    private void onCellClicked(View v) {
        SortDimension dimension = (SortDimension) v.getTag();

        mModel.sortByUser(dimension.getId(), dimension.getNextDirection());
    }

    /** Sorts the column if the key pressed was Enter or Space. */
    private boolean onCellKeyEvent(View v, int keyCode, KeyEvent event) {
        if (!useMaterial3()) {
            return false;
        }
        // Only the enter and space bar should trigger the sort header to engage.
        if (event.getAction() == KeyEvent.ACTION_UP
                && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE)) {
            onCellClicked(v);
            return true;
        }
        return false;
    }
}
