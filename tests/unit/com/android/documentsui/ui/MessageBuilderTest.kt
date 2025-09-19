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
package com.android.documentsui.ui

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_CONVERTED
import com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_FAILURE
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS
import com.android.documentsui.services.FileOperationService.OPERATION_COPY
import com.android.documentsui.services.FileOperationService.OPERATION_DELETE
import com.android.documentsui.services.FileOperationService.OPERATION_EXTRACT
import com.android.documentsui.services.FileOperationService.OPERATION_MOVE
import com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class MessageBuilderTest() {
    @get:Rule val setFlags = OverrideFlagsRule()

    private val context =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private val messageBuilder = MessageBuilder(context)

    @Test
    fun generateDeleteMessage() {
        assertThat(messageBuilder.generateDeleteMessage(listOf(file("File"))))
            .isEqualTo("Delete \"File\"?")
        assertThat(messageBuilder.generateDeleteMessage(listOf(directory("Dir"))))
            .isEqualTo("Delete folder \"Dir\" and its contents?")
        assertThat(messageBuilder.generateDeleteMessage(listOf(file("File 1"), file("File 2"))))
            .isEqualTo("Delete 2 files?")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(directory("Directory 1"), directory("Directory 2"))
                )
            )
            .isEqualTo("Delete 2 folders and their contents?")
        assertThat(
                messageBuilder.generateDeleteMessage(
                    listOf(file("File 1"), directory("Directory 1"))
                )
            )
            .isEqualTo("Delete 2 items?")
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO)
    fun generateListMessage() {
        data class Params(val dialog: Int, val op: Int, val want1: String, val want2: String)
        val params =
            listOf(
                Params(
                    dialog = DIALOG_TYPE_CONVERTED,
                    op = OPERATION_UNKNOWN,
                    want1 = "This file was converted to another format: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files were converted to another format: " +
                            "<p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COPY,
                    want1 = "This file wasn’t copied: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t copied: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_MOVE,
                    want1 = "This file wasn’t moved: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t moved: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COMPRESS,
                    want1 = "This file wasn’t zipped: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t zipped: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_EXTRACT,
                    want1 = "This file wasn’t extracted: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t extracted: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_DELETE,
                    want1 = "This file wasn’t deleted: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t deleted: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
            )

        for (p in params) {
            assertThat(
                    messageBuilder.generateListMessage(
                        p.dialog,
                        p.op,
                        listOf(file("File 1")),
                        null,
                        null,
                    )
                )
                .isEqualTo(p.want1)

            assertThat(
                    messageBuilder.generateListMessage(
                        p.dialog,
                        p.op,
                        listOf(file("File 1")),
                        listOf("content://random-uri/File+2".toUri()),
                        listOf("/Dir/File 3"),
                    )
                )
                .isEqualTo(p.want2)
        }
    }

    @Test
    @DisableFlags(FLAG_ZIP_NG_RO)
    fun generateListMessageOld() {
        data class Params(val dialog: Int, val op: Int, val want1: String, val want2: String)
        val params =
            listOf(
                Params(
                    dialog = DIALOG_TYPE_CONVERTED,
                    op = OPERATION_UNKNOWN,
                    want1 = "This file was converted to another format: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files were converted to another format: " +
                            "<p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COPY,
                    want1 = "This file wasn’t copied: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t copied: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_MOVE,
                    want1 = "This file wasn’t moved: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t moved: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_COMPRESS,
                    want1 = "This file wasn’t compressed: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t compressed: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_EXTRACT,
                    want1 = "This file wasn’t extracted: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t extracted: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
                Params(
                    dialog = DIALOG_TYPE_FAILURE,
                    op = OPERATION_DELETE,
                    want1 = "This file wasn’t deleted: <p>&#8226; File 1<br></p>",
                    want2 =
                        "These files weren’t deleted: <p>&#8226; File 1<br>&#8226; " +
                            "content://random-uri/File+2<br>&#8226; File 3<br></p>",
                ),
            )

        for (p in params) {
            assertThat(
                    messageBuilder.generateListMessage(
                        p.dialog,
                        p.op,
                        listOf(file("File 1")),
                        null,
                        null,
                    )
                )
                .isEqualTo(p.want1)

            assertThat(
                    messageBuilder.generateListMessage(
                        p.dialog,
                        p.op,
                        listOf(file("File 1")),
                        listOf("content://random-uri/File+2".toUri()),
                        listOf("/Dir/File 3"),
                    )
                )
                .isEqualTo(p.want2)
        }
    }
}

fun file(displayName: String): DocumentInfo {
    val doc = DocumentInfo()
    doc.displayName = displayName
    return doc
}

fun directory(displayName: String): DocumentInfo {
    val doc = DocumentInfo()
    doc.displayName = displayName
    doc.mimeType = MIME_TYPE_DIR
    return doc
}
