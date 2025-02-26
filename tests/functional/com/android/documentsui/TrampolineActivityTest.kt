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
package com.android.documentsui

import android.content.Intent
import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.documentsui.picker.TrampolineActivity
import java.util.regex.Pattern
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses

@SmallTest
@RunWith(Suite::class)
@SuiteClasses(
    TrampolineActivityTest.ShouldLaunchCorrectPackageTest::class,
    TrampolineActivityTest.RedirectTest::class
)
class TrampolineActivityTest() {
    companion object {
        const val UI_TIMEOUT = 5000L
        val PHOTOPICKER_PACKAGE_REGEX: Pattern = Pattern.compile(".*photopicker.*")
        val DOCUMENTSUI_PACKAGE_REGEX: Pattern = Pattern.compile(".*documentsui.*")

        private var device: UiDevice? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        }
    }

    @RunWith(Parameterized::class)
    class ShouldLaunchCorrectPackageTest {
        enum class AppType {
            PHOTOPICKER,
            DOCUMENTSUI,
        }

        data class GetContentIntentData(
            val mimeType: String,
            val expectedApp: AppType,
            val extraMimeTypes: Array<String>? = null,
        ) {
            override fun toString(): String {
                if (extraMimeTypes != null) {
                    return "${mimeType}_${extraMimeTypes.joinToString("_")}"
                }
                return mimeType
            }
        }

        companion object {
            @Parameterized.Parameters(name = "{0}")
            @JvmStatic
            fun parameters() =
                listOf(
                    GetContentIntentData(
                        mimeType = "*/*",
                        expectedApp = AppType.DOCUMENTSUI,
                    ),
                    GetContentIntentData(
                        mimeType = "image/*",
                        expectedApp = AppType.PHOTOPICKER,
                    ),
                    GetContentIntentData(
                        mimeType = "video/*",
                        expectedApp = AppType.PHOTOPICKER,
                    ),
                    GetContentIntentData(
                        mimeType = "image/*",
                        extraMimeTypes = arrayOf("video/*"),
                        expectedApp = AppType.PHOTOPICKER,
                    ),
                    GetContentIntentData(
                        mimeType = "video/*",
                        extraMimeTypes = arrayOf("image/*"),
                        expectedApp = AppType.PHOTOPICKER,
                    ),
                    GetContentIntentData(
                        mimeType = "video/*",
                        extraMimeTypes = arrayOf("text/*"),
                        expectedApp = AppType.DOCUMENTSUI,
                    ),
                    GetContentIntentData(
                        mimeType = "video/*",
                        extraMimeTypes = arrayOf("image/*", "text/*"),
                        expectedApp = AppType.DOCUMENTSUI,
                    ),
                    GetContentIntentData(
                        mimeType = "*/*",
                        extraMimeTypes = arrayOf("image/*", "video/*"),
                        expectedApp = AppType.DOCUMENTSUI,
                    ),
                    GetContentIntentData(
                        mimeType = "image/*",
                        extraMimeTypes = arrayOf(),
                        expectedApp = AppType.DOCUMENTSUI,
                    )
                )
        }

        @Parameterized.Parameter(0)
        lateinit var testData: GetContentIntentData

        @Before
        fun setUp() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setClass(context, TrampolineActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setType(testData.mimeType)
            testData.extraMimeTypes?.let { intent.putExtra(Intent.EXTRA_MIME_TYPES, it) }

            context.startActivity(intent)
        }

        @Test
        fun testCorrectAppIsLaunched() {
            val bySelector = when (testData.expectedApp) {
                AppType.PHOTOPICKER -> By.pkg(PHOTOPICKER_PACKAGE_REGEX)
                else -> By.pkg(DOCUMENTSUI_PACKAGE_REGEX)
            }

            assertNotNull(device?.wait(Until.findObject(bySelector), UI_TIMEOUT))
        }
    }

    @RunWith(AndroidJUnit4::class)
    class RedirectTest {
        @Test
        fun testReferredGetContentFromPhotopickerShouldNotRedirectBack() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setClass(context, TrampolineActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setType("image/*")

            context.startActivity(intent)
            val moreButton = device?.wait(Until.findObject(By.desc("More")), UI_TIMEOUT)
            moreButton?.click()

            val browseButton = device?.wait(Until.findObject(By.textContains("Browse")), UI_TIMEOUT)
            browseButton?.click()

            assertNotNull(
                "DocumentsUI has not launched",
                device?.wait(Until.findObject(By.pkg(DOCUMENTSUI_PACKAGE_REGEX)), UI_TIMEOUT)
            )
        }

        @Test
        @SdkSuppress(minSdkVersion = VERSION_CODES.S, maxSdkVersion = VERSION_CODES.S_V2)
        fun testAndroidSWithTakeoverGetContentDisabledShouldNotReferToDocumentsUI() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setClass(context, TrampolineActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setType("image/*")

            try {
                // Disable Photopicker from taking over `ACTION_GET_CONTENT`. In this situation, it
                // should ALWAYS defer to DocumentsUI regardless if the mimetype satisfies the
                // conditions.
                device?.executeShellCommand(
                    "device_config put mediaprovider take_over_get_content false"
                )
                context.startActivity(intent)
                assertNotNull(
                    device?.wait(Until.findObject(By.pkg(DOCUMENTSUI_PACKAGE_REGEX)), UI_TIMEOUT)
                )
            } finally {
                device?.executeShellCommand(
                    "device_config delete mediaprovider take_over_get_content"
                )
            }
        }
    }
}
