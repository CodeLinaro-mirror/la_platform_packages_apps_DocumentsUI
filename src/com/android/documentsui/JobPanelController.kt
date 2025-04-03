/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.documentsui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.documentsui.base.Menus
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.util.FormatUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Adds a gap between items in a vertical Recycler View.
 */
private class VerticalMarginItemDecoration(
    private val mMarginSize: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (parent.getChildAdapterPosition(view) > 0) {
            outRect.top = mMarginSize
        }
    }
}

/**
 * JobPanelController is responsible for receiving broadcast updates from the [FileOperationService]
 * and updating a given menu item to reflect the current progress.
 */
class JobPanelController(private val mContext: Context) : BroadcastReceiver() {
    companion object {
        private const val TAG = "JobPanelController"
        private const val MAX_PROGRESS = 100
    }

    class ProgressItemHolder(val mController: JobPanelController, view: View) :
        RecyclerView.ViewHolder(view) {

        val mContext = view.context

        val mTitleView = view.findViewById<TextView>(R.id.job_progress_item_title)
        val mProgressView =
            view.findViewById<LinearProgressIndicator>(R.id.job_progress_item_progress)
        val mPrimaryStatusView = view.findViewById<TextView>(R.id.job_progress_item_primary_status)
        val mSecondaryStatusView =
            view.findViewById<TextView>(R.id.job_progress_item_secondary_status)

        val mDismissButton = view.findViewById<Button>(R.id.job_progress_item_dismiss)

        fun setJobProgress(jobProgress: JobProgress) {
            mTitleView.text = jobProgress.msg
            mProgressView.isIndeterminate = jobProgress.isIndeterminate
            if (!mProgressView.isIndeterminate) {
                mProgressView.setProgress(jobProgress.toPercent().toInt())
            }
            if (jobProgress.state == Job.STATE_COMPLETED) {
                if (jobProgress.hasFailures) {
                    mPrimaryStatusView.setTextAppearance(R.style.JobProgressItemStatusText_Failure)
                    mPrimaryStatusView.text = mContext.getString(R.string.job_progress_item_failed)
                    mSecondaryStatusView.text =
                        mContext.getString(R.string.job_progress_item_see_details)
                } else {
                    mPrimaryStatusView.setTextAppearance(R.style.JobProgressItemStatusText_Success)
                    mPrimaryStatusView.text =
                        mContext.getString(R.string.job_progress_item_completed)
                    mSecondaryStatusView.text = getCompletionStatusString(jobProgress.operationType)
                }
            } else {
                mPrimaryStatusView.setTextAppearance(R.style.JobProgressItemStatusText)
                mPrimaryStatusView.text = mContext.getString(
                    R.string.job_progress_item_byte_progress,
                    Formatter.formatFileSize(mContext, jobProgress.currentBytes),
                    Formatter.formatFileSize(mContext, jobProgress.requiredBytes),
                )
                mSecondaryStatusView.text = mContext.getString(R.string.copy_remaining,
                    FormatUtils.formatDuration(jobProgress.msRemaining))
            }
            mDismissButton.setOnClickListener { mController.dismissProgress(jobProgress.id) }
        }

        private fun getCompletionStatusString(@FileOperationService.OpType opType: Int): String {
            return when (opType) {
                FileOperationService.OPERATION_COPY -> mContext.getString(R.string.copy_completed)
                FileOperationService.OPERATION_MOVE -> mContext.getString(R.string.move_completed)
                FileOperationService.OPERATION_DELETE ->
                    mContext.getString(R.string.delete_completed)
                FileOperationService.OPERATION_COMPRESS ->
                    mContext.getString(R.string.compress_completed)
                FileOperationService.OPERATION_EXTRACT ->
                    mContext.getString(R.string.extract_completed)
                else -> ""
            }
        }
    }

    class ProgressListAdapter(val mController: JobPanelController) :
        ListAdapter<JobProgress, ProgressItemHolder>(JobDiffCallback) {

        companion object {
            // Constants for the different view types created by this adapter. The type depends on
            // the position of the item in the list.
            private const val VIEW_MIDDLE = 0
            private const val VIEW_TOP = 1
            private const val VIEW_BOTTOM = 2
            private const val VIEW_TOP_BOTTOM = 3
        }

        override fun getItemViewType(position: Int): Int {
            if (itemCount == 1) return VIEW_TOP_BOTTOM
            if (position == 0) return VIEW_TOP
            if (position == itemCount - 1) return VIEW_BOTTOM
            return VIEW_MIDDLE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressItemHolder {
            val context = parent.context
            val view = LayoutInflater.from(context)
                .inflate(R.layout.job_progress_item, parent, false) as MaterialCardView
            if (viewType != 0) {
                val outerRadius =
                    context.resources.getDimension(R.dimen.job_progress_list_corner_radius)
                view.shapeAppearanceModel = ShapeAppearanceModel
                    .builder(context, R.style.JobProgressItemCardBaseShape, 0)
                    .apply {
                        if (viewType == VIEW_TOP_BOTTOM || viewType == VIEW_TOP) {
                            setTopLeftCornerSize(outerRadius)
                            setTopRightCornerSize(outerRadius)
                        }
                        if (viewType == VIEW_TOP_BOTTOM || viewType == VIEW_BOTTOM) {
                            setBottomLeftCornerSize(outerRadius)
                            setBottomRightCornerSize(outerRadius)
                        }
                    }.build()
            }
            return ProgressItemHolder(mController, view)
        }

        override fun onBindViewHolder(holder: ProgressItemHolder, position: Int) {
            holder.setJobProgress(getItem(position))
        }

        object JobDiffCallback : DiffUtil.ItemCallback<JobProgress>() {
            override fun areItemsTheSame(oldJobProgress: JobProgress, newJobProgress: JobProgress) =
                oldJobProgress.id == newJobProgress.id

            override fun areContentsTheSame(
                oldJobProgress: JobProgress,
                newJobProgress: JobProgress
            ) = oldJobProgress == newJobProgress
        }
    }

    private enum class State {
        INVISIBLE, INDETERMINATE, VISIBLE
    }

    /** The current state of the menu progress item. */
    private var mState = State.INVISIBLE

    /** The total progress from 0 to MAX_PROGRESS. */
    private var mTotalProgress = 0

    /** List of jobs currently tracked by this class. */
    private val mCurrentJobs = LinkedHashMap<String, JobProgress>()

    /** Current menu item being controlled by this class. */
    private var mMenuItem: MenuItem? = null

    /** Current panel popup shown if any. */
    private var mPopup: PopupWindow? = null

    /** Adapter used to display JobProgresses in the recycler list. */
    private var mProgressListAdapter: ProgressListAdapter? = null

    init {
        val filter = IntentFilter(FileOperationService.ACTION_PROGRESS)
        mContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun updateMenuItem(animate: Boolean) {
        if (mState == State.INVISIBLE) {
            mPopup?.dismiss()
        }

        mMenuItem?.let {
            Menus.setEnabledAndVisible(it, mState != State.INVISIBLE)
            val icon = it.actionView as ProgressBar
            when (mState) {
                State.INDETERMINATE -> icon.isIndeterminate = true
                State.VISIBLE -> icon.apply {
                    isIndeterminate = false
                    setProgress(mTotalProgress, animate)
                }
                State.INVISIBLE -> {}
            }
        }
    }

    /**
     * Sets the menu item controlled by this class. The item's actionView must be a [ProgressBar].
     */
    @Suppress("ktlint:standard:comment-wrapping")
    fun setMenuItem(menuItem: MenuItem) {
        val progressIcon = menuItem.actionView as ProgressBar
        progressIcon.max = MAX_PROGRESS
        progressIcon.setOnClickListener { view ->
            val panel = LayoutInflater.from(mContext).inflate(
                R.layout.job_progress_panel,
                /* root= */ null
            )
            val listAdapter = ProgressListAdapter(this)
            listAdapter.submitList(ArrayList(mCurrentJobs.values))
            panel.findViewById<RecyclerView>(R.id.job_progress_list).apply {
                layoutManager = LinearLayoutManager(mContext)
                addItemDecoration(VerticalMarginItemDecoration(
                    mContext.resources.getDimensionPixelSize(R.dimen.job_progress_list_gap)
                ))
                itemAnimator = null
                adapter = listAdapter
            }
            mProgressListAdapter = listAdapter
            val popupWidth = mContext.resources.getDimension(R.dimen.job_progress_panel_width) +
                    mContext.resources.getDimension(R.dimen.job_progress_panel_margin)
            mPopup = PopupWindow(
                /* contentView= */ panel,
                /* width= */ popupWidth.toInt(),
                /* height= */ ViewGroup.LayoutParams.WRAP_CONTENT,
                /* focusable= */ true
            ).apply {
                setOnDismissListener { mProgressListAdapter = null }
                showAsDropDown(
                    /* anchor= */ view,
                    /* xoff= */ view.width - popupWidth.toInt(),
                    /* yoff= */ 0
                )
            }
        }
        mMenuItem = menuItem
        updateMenuItem(animate = false)
    }

    override fun onReceive(context: Context?, intent: Intent) {
        val progresses = intent.getParcelableArrayListExtra<JobProgress>(
            EXTRA_PROGRESS,
            JobProgress::class.java
        )
        updateProgress(progresses!!)
    }

    private fun updateProgress(progresses: List<JobProgress>) {
        var requiredBytes = 0L
        var currentBytes = 0L
        var allFinished = true

        for (jobProgress in progresses) {
            Log.d(TAG, "Received $jobProgress")
            mCurrentJobs.put(jobProgress.id, jobProgress)
        }
        for (jobProgress in mCurrentJobs.values) {
            if (jobProgress.state != Job.STATE_COMPLETED) {
                allFinished = false
            }
            if (jobProgress.requiredBytes != -1L && jobProgress.currentBytes != -1L) {
                requiredBytes += jobProgress.requiredBytes
                currentBytes += jobProgress.currentBytes
            }
        }

        if (mCurrentJobs.isEmpty()) {
            mState = State.INVISIBLE
        } else if (requiredBytes != 0L) {
            mState = State.VISIBLE
            mTotalProgress = (MAX_PROGRESS * currentBytes / requiredBytes).toInt()
        } else if (allFinished) {
            mState = State.VISIBLE
            mTotalProgress = MAX_PROGRESS
        } else {
            mState = State.INDETERMINATE
        }
        updateMenuItem(animate = true)
        mProgressListAdapter?.submitList(ArrayList(mCurrentJobs.values))
    }

    private fun dismissProgress(id: String) {
        mCurrentJobs.remove(id)
        updateProgress(emptyList())
    }
}
