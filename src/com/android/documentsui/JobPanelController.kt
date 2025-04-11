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
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.google.android.material.button.MaterialButton
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

    data class ProgressViewModel(val jobProgress: JobProgress, val expanded: Boolean = false)

    private class ProgressItemHolder(
        private val controller: JobPanelController,
        private val cardView: View,
        ) : RecyclerView.ViewHolder(cardView) {

        private val context = cardView.context

        // Header elements.
        private val titleView = cardView.findViewById<TextView>(R.id.job_progress_item_title)
        private val toggleExpandButton =
            cardView.findViewById<MaterialButton>(R.id.job_progress_item_expand)

        // Body elements.
        private val progressView =
            cardView.findViewById<LinearProgressIndicator>(R.id.job_progress_item_progress)
        private val primaryStatusView =
            cardView.findViewById<TextView>(R.id.job_progress_item_primary_status)
        private val statusSeparator =
            cardView.findViewById<TextView>(R.id.job_progress_item_status_separator)
        private val secondaryStatusView =
            cardView.findViewById<TextView>(R.id.job_progress_item_secondary_status)

        // Buttons
        private val cancelButton = cardView.findViewById<Button>(R.id.job_progress_item_cancel)
        private val showInFolderButton =
            cardView.findViewById<Button>(R.id.job_progress_item_show_in_folder)
        private val dismissButton = cardView.findViewById<Button>(R.id.job_progress_item_dismiss)

        fun setJobProgress(jobProgress: JobProgress, expanded: Boolean) {
            titleView.text = jobProgress.msg
            toggleExpandButton.icon = context.getDrawable(when (expanded) {
                true -> R.drawable.ic_job_progress_collapse
                false -> R.drawable.ic_job_progress_expand
            })

            updateProgressBar(jobProgress)
            setStatusText(jobProgress, expanded)

            cardView.setOnClickListener { controller.toggleExpanded(jobProgress.id) }
            toggleExpandButton.setOnClickListener { controller.toggleExpanded(jobProgress.id) }

            cancelButton.isVisible = expanded && !jobProgress.isFinal
            showInFolderButton.isVisible = expanded && jobProgress.isFinal
            dismissButton.isVisible = expanded && jobProgress.isFinal
            dismissButton.setOnClickListener { controller.dismissProgress(jobProgress.id) }
        }

        private fun updateProgressBar(jobProgress: JobProgress) {
            progressView.let {
                it.isGone = jobProgress.isFinal
                it.isIndeterminate = jobProgress.isIndeterminate
                if (!it.isIndeterminate) {
                    it.progress = jobProgress.toPercent().toInt()
                }
            }
        }

        private fun setStatusText(jobProgress: JobProgress, expanded: Boolean) {
            primaryStatusView.isGone = false
            secondaryStatusView.isGone = false
            if (jobProgress.state == Job.STATE_COMPLETED) {
                if (jobProgress.hasFailures) {
                    primaryStatusView.setTextAppearance(R.style.JobProgressItemStatusText_Failure)
                    primaryStatusView.text = context.getString(R.string.job_progress_item_failed)
                    secondaryStatusView.isGone = expanded
                    secondaryStatusView.text =
                        context.getString(R.string.job_progress_item_see_details)
                } else {
                    primaryStatusView.setTextAppearance(R.style.JobProgressItemStatusText_Success)
                    primaryStatusView.text =
                        context.getString(R.string.job_progress_item_completed)
                    secondaryStatusView.text = getCompletionStatusString(jobProgress.operationType)
                }
            } else if (expanded && jobProgress.state == Job.STATE_SET_UP &&
                !jobProgress.isIndeterminate) {
                primaryStatusView.setTextAppearance(R.style.JobProgressItemStatusText)
                primaryStatusView.text = context.getString(
                    R.string.job_progress_item_byte_progress,
                    Formatter.formatFileSize(context, jobProgress.currentBytes),
                    Formatter.formatFileSize(context, jobProgress.requiredBytes),
                )
                secondaryStatusView.text = context.getString(R.string.copy_remaining,
                    FormatUtils.formatDuration(jobProgress.msRemaining))
            } else {
                primaryStatusView.isGone = true
                secondaryStatusView.isGone = true
            }
            statusSeparator.isGone = primaryStatusView.isGone || secondaryStatusView.isGone
        }

        private fun getCompletionStatusString(@FileOperationService.OpType opType: Int): String {
            return when (opType) {
                FileOperationService.OPERATION_COPY -> context.getString(R.string.copy_completed)
                FileOperationService.OPERATION_MOVE -> context.getString(R.string.move_completed)
                FileOperationService.OPERATION_DELETE ->
                    context.getString(R.string.delete_completed)
                FileOperationService.OPERATION_COMPRESS ->
                    context.getString(R.string.compress_completed)
                FileOperationService.OPERATION_EXTRACT ->
                    context.getString(R.string.extract_completed)
                else -> ""
            }
        }
    }

    private class ProgressListAdapter(private val controller: JobPanelController) :
        ListAdapter<ProgressViewModel, ProgressItemHolder>(JobDiffCallback) {

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
            return ProgressItemHolder(controller, view)
        }

        override fun onBindViewHolder(holder: ProgressItemHolder, position: Int) {
            val (jobProgress, expanded) = getItem(position)
            holder.setJobProgress(jobProgress, expanded)
        }

        object JobDiffCallback : DiffUtil.ItemCallback<ProgressViewModel>() {
            override fun areItemsTheSame(oldModel: ProgressViewModel, newModel: ProgressViewModel) =
                oldModel.jobProgress.id == newModel.jobProgress.id

            override fun areContentsTheSame(
                oldModel: ProgressViewModel,
                newModel: ProgressViewModel
            ) = oldModel == newModel
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
    private val mCurrentJobs = LinkedHashMap<String, ProgressViewModel>()

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
            mCurrentJobs.merge(jobProgress.id, ProgressViewModel(jobProgress)) {
                old, new -> ProgressViewModel(new.jobProgress, old.expanded)
            }
        }
        for ((jobProgress, _) in mCurrentJobs.values) {
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

    private fun toggleExpanded(id: String) {
        mCurrentJobs.computeIfPresent(id) { _, (jobProgress, expanded) ->
            ProgressViewModel(jobProgress, !expanded)
        }
        mProgressListAdapter?.submitList(ArrayList(mCurrentJobs.values))
    }
}
