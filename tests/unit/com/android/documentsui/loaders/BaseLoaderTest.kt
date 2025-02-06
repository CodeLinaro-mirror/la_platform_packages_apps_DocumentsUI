/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.documentsui.loaders

import android.os.Parcel
import com.android.documentsui.DirectoryResult
import com.android.documentsui.TestActivity
import com.android.documentsui.TestConfigStore
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.testing.ActivityManagers
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.UserManagers
import java.util.Locale
import org.junit.Before

/**
 * Returns the number of matched files, or -1.
 */
fun getFileCount(result: DirectoryResult?) = result?.cursor?.count ?: -1

/**
 * Common base class for search and folder loaders.
 */
open class BaseLoaderTest {
    lateinit var mEnv: TestEnv
    lateinit var mActivity: TestActivity
    lateinit var mTestConfigStore: TestConfigStore

    @Before
    open fun setUp() {
        mEnv = TestEnv.create()
        mTestConfigStore = TestConfigStore()
        mEnv.state.configStore = mTestConfigStore
        mEnv.state.showHiddenFiles = false
        val parcel = Parcel.obtain()
        mEnv.state.sortModel = SortModel.CREATOR.createFromParcel(parcel)

        mActivity = TestActivity.create(mEnv)
        mActivity.activityManager = ActivityManagers.create(false)
        mActivity.userManager = UserManagers.create()
    }

    fun createDocuments(count: Int): Array<DocumentInfo> {
        val extensionList = arrayOf("txt", "png", "mp4", "mpg")
        return Array<DocumentInfo>(count) { i ->
            val id = String.format(Locale.US, "%05d", i)
            mEnv.model.createFile("sample-$id.${extensionList[i % extensionList.size]}")
        }
    }
}
