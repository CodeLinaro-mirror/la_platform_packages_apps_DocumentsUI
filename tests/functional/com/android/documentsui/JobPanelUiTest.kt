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

import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.View
import android.widget.ProgressBar
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.selectedDescendantsMatch
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.FileOperationService.ACTION_PROGRESS
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.testing.MutableJobProgress
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun withProgress(expectedProgress: Int): Matcher<View> {
    return object : BoundedMatcher<View, ProgressBar>(ProgressBar::class.java) {
        override fun matchesSafely(view: ProgressBar): Boolean {
            return view.progress == expectedProgress
        }

        override fun describeTo(description: Description) {
            description.appendText("with progress: " + expectedProgress)
        }
    }
}

// Helper function to match views inside a certain progress item view.
private fun insideItem(progress: MutableJobProgress) = hasSibling(withText(progress.msg))

@RequiresFlagsEnabled(FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO)
@RunWith(AndroidJUnit4::class)
class JobPanelUiTest : ActivityTestJunit4<FilesActivity>() {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private var mLastId = 0L

    private fun sendProgress(progresses: ArrayList<JobProgress>, id: Long = mLastId++) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var intent = Intent(ACTION_PROGRESS).apply {
            `package` = context.packageName
            putExtra("id", id)
            putParcelableArrayListExtra(EXTRA_PROGRESS, progresses)
        }
        context.sendBroadcast(intent)
    }

    @Test
    fun testInProgressItems() {
        onView(withId(R.id.option_menu_job_progress)).check(doesNotExist())
        onView(withId(R.id.job_progress_panel_title)).check(doesNotExist())

        val progress = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 4,
            requiredBytes = 10,
            msRemaining = 10000,
        )
        sendProgress(arrayListOf(progress.toJobProgress()))

        onView(withId(R.id.option_menu_job_progress))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.job_progress_panel_title)).check(matches(isDisplayed()))

        val expectedPrimaryStatus = "4 B of 10 B"
        val expectedSecondaryStatus = "10 seconds left"

        onView(withId(R.id.job_progress_item_title)).check(matches(withText("Job started")))
        onView(withId(R.id.job_progress_item_progress)).check(matches(withProgress(40)))
        onView(allOf(withText(expectedPrimaryStatus), isDisplayed())).check(doesNotExist())
        onView(allOf(withText(expectedSecondaryStatus), isDisplayed())).check(doesNotExist())
        onView(withId(R.id.job_progress_item_cancel)).check(matches(not(isDisplayed())))

        onView(withId(R.id.job_progress_item_expand)).perform(click())
        onView(withText(expectedPrimaryStatus)).check(matches(isDisplayed()))
        onView(withText(expectedSecondaryStatus)).check(matches(isDisplayed()))
        onView(withId(R.id.job_progress_item_cancel)).check(matches(isDisplayed()))

        onView(withId(R.id.job_progress_item_expand)).perform(click())
        onView(allOf(withText(expectedPrimaryStatus), isDisplayed())).check(doesNotExist())
        onView(allOf(withText(expectedSecondaryStatus), isDisplayed())).check(doesNotExist())
        onView(withId(R.id.job_progress_item_cancel)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testCompletedItems() {
        val progress1 = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_EXTRACT,
            state = Job.STATE_COMPLETED,
            msg = "Job1 completed",
            hasFailures = false,
        )
        val progress2 = MutableJobProgress(
            id = "jobId2",
            operationType = FileOperationService.OPERATION_MOVE,
            state = Job.STATE_COMPLETED,
            msg = "Job2 completed",
            hasFailures = true,
        )
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        onView(withId(R.id.option_menu_job_progress))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.job_progress_panel_title)).check(matches(isDisplayed()))

        onView(withChild(withText(progress1.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_completed),
                isDisplayed()
            ))
            .check(selectedDescendantsMatch(
                withText(R.string.extract_completed),
                isDisplayed()
            ))
        onView(withChild(withText(progress2.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_failed),
                isDisplayed()
            ))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_see_details),
                isDisplayed()
            ))

        // Dismiss the first item.
        onView(allOf(withId(R.id.job_progress_item_expand), insideItem(progress1)))
            .perform(click())
        onView(allOf(withId(R.id.job_progress_item_dismiss), insideItem(progress1)))
            .perform(click())
        onView(withText(progress1.msg)).check(doesNotExist())

        // Dismiss the second item. The panel should disappear.
        onView(allOf(withId(R.id.job_progress_item_expand), insideItem(progress2)))
            .perform(click())
        onView(allOf(withText(R.string.job_progress_item_see_details), isDisplayed()))
            .check(doesNotExist())
        onView(allOf(withId(R.id.job_progress_item_dismiss), insideItem(progress2)))
            .perform(click())
        onView(withId(R.id.option_menu_job_progress)).check(doesNotExist())
        onView(withId(R.id.job_progress_panel_title)).check(doesNotExist())
    }
}
