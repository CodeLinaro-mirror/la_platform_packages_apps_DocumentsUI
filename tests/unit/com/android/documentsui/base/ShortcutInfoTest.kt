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

package com.android.documentsui.base

import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ShortcutInfoTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    @Test
    fun testEqualsSameDocsProviderRootSameUser() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(0, "title", rootInfo, "parentDocumentId")

        val shortcutInfo2 = ShortcutInfo(0, "title", rootInfo, "parentDocumentId")

        assertEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsSameDocsProviderRootDiffUser() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }

        val copyRootInfo = RootInfo.copyRootInfo(rootInfo)
        copyRootInfo.userId = UserId.of(200)
        val shortcutInfo = ShortcutInfo(0, "title", rootInfo, "parentDocumentId")

        val shortcutInfo2 = ShortcutInfo(0, "title", copyRootInfo, "parentDocumentId")

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsDiffDocsProviderRoot() {
        val rootInfo1 =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority1"
                rootId = "root1"
            }
        val shortcutInfo = ShortcutInfo(0, "title", rootInfo1, "parentDocumentId")

        val rootInfo2 =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root2"
            }
        val shortcutInfo2 = ShortcutInfo(0, "title", rootInfo2, "parentDocumentId")

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testNotEqualsDiffUri() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(0, "title", rootInfo, "parentDocumentId")
        val shortcutInfo2 = ShortcutInfo(0, "title", rootInfo, "parentDocumentId")
        // Uri is determined by the document id
        shortcutInfo.documentId = "documentId"
        shortcutInfo2.documentId = "documentId2"

        assertNotEquals(shortcutInfo, shortcutInfo2)
    }

    @Test
    fun testEqualsGetUri() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "authority"
                rootId = "root"
            }
        val shortcutInfo = ShortcutInfo(0, "title", rootInfo, "parentDocumentId")
        val expected = DocumentsContract.buildDocumentUri(rootInfo.authority, "some doc id")

        // Uri is determined by the document id
        shortcutInfo.documentId = "some doc id"

        assertEquals(expected, shortcutInfo.uri)
    }

    @Test
    fun testToStringShortcut() {
        val rootInfo =
            RootInfo().apply {
                userId = UserId.of(100)
                authority = "aaa"
                rootId = "rrr"
            }
        val shortcutInfo = ShortcutInfo(0, "ttt", rootInfo, "ppp")
        shortcutInfo.documentId = "ddd"

        val expected =
            "ShortcutInfo{title=ttt, documentId=ddd" +
                ", root=Root{userId=" +
                UserId.of(100) +
                ", authority=aaa, rootId=rrr, title=null, isUsb=false, isSd=false, isMtp=false" +
                "} @ " +
                DocumentsContract.buildRootUri("aaa", "rrr") +
                "} @ " +
                DocumentsContract.buildDocumentUri("aaa", "ddd")

        assertEquals(expected, shortcutInfo.toString())
    }
}
