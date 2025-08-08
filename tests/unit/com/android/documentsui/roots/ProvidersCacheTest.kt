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

package com.android.documentsui.roots

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.R
import com.android.documentsui.rules.OverrideFlagsRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
@SmallTest
class ProvidersCacheTest {
    @get:Rule val setFlags = OverrideFlagsRule()

    @Mock private lateinit var context: Context
    lateinit var providers: ProvidersCache

    @Mock private lateinit var resources: Resources
    private lateinit var authoritiesArray: Array<String>
    private lateinit var rootIdsArray: Array<String>
    private lateinit var parentDocIdsArray: Array<String>
    private lateinit var titlesArray: Array<String>

    @Mock private lateinit var iconTypedArray: TypedArray
    private val ICON_DEFAULT_RES_ID: Int = 10

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        providers = ProvidersCache(context)
        authoritiesArray = arrayOf("test authority", "test authority")
        rootIdsArray = arrayOf("root one", "root two")
        parentDocIdsArray = arrayOf("parent one", "parent two")
        titlesArray = arrayOf("title A", "title B")
        whenever(context.getResources()).thenReturn(resources)
        whenever(resources.obtainTypedArray(R.array.shortcut_icons)).thenReturn(iconTypedArray)
        whenever(iconTypedArray.getResourceId(anyInt(), anyInt())).thenReturn(ICON_DEFAULT_RES_ID)
    }

    // TODO: b/446064228 - Test flag dependency by returning the async task in updateAsync() and
    //  testing the output of updateAsync().
    @Test
    fun testGetShortcutResourcesOneShortcutSuccess() {
        // Set up the resources for mocking
        whenever(resources.getStringArray(R.array.shortcut_authorities))
            .thenReturn(arrayOf(authoritiesArray[0]))
        whenever(resources.getStringArray(R.array.shortcut_root_ids))
            .thenReturn(arrayOf(rootIdsArray[0]))
        whenever(resources.getStringArray(R.array.shortcut_parent_doc_ids))
            .thenReturn(arrayOf(parentDocIdsArray[0]))
        whenever(resources.getStringArray(R.array.shortcut_titles))
            .thenReturn(arrayOf(titlesArray[0]))
        whenever(iconTypedArray.length()).thenReturn(1)

        val expected: List<ShortcutResourceValues> =
            listOf(
                ShortcutResourceValues(
                    authoritiesArray[0],
                    rootIdsArray[0],
                    parentDocIdsArray[0],
                    titlesArray[0],
                    ICON_DEFAULT_RES_ID,
                )
            )

        assertEquals(expected, providers.getShortcutResources())
    }

    // TODO: b/446064228 - Test flag dependency by returning the async task in updateAsync() and
    //  testing the output of updateAsync().
    @Test
    fun testGetShortcutResourcesMultipleShortcutsSuccess() {
        // Set up the resources for mocking
        whenever(resources.getStringArray(R.array.shortcut_authorities))
            .thenReturn(authoritiesArray)
        whenever(resources.getStringArray(R.array.shortcut_root_ids)).thenReturn(rootIdsArray)
        whenever(resources.getStringArray(R.array.shortcut_parent_doc_ids))
            .thenReturn(parentDocIdsArray)
        whenever(resources.getStringArray(R.array.shortcut_titles)).thenReturn(titlesArray)
        whenever(iconTypedArray.length()).thenReturn(2)

        val shortcutResources1 =
            ShortcutResourceValues(
                authoritiesArray[0],
                rootIdsArray[0],
                parentDocIdsArray[0],
                titlesArray[0],
                ICON_DEFAULT_RES_ID,
            )
        val shortcutResources2 =
            ShortcutResourceValues(
                authoritiesArray[1],
                rootIdsArray[1],
                parentDocIdsArray[1],
                titlesArray[1],
                ICON_DEFAULT_RES_ID,
            )

        val expected: List<ShortcutResourceValues> = listOf(shortcutResources1, shortcutResources2)

        assertEquals(expected, providers.getShortcutResources())
    }

    // TODO: b/446064228 - Test flag dependency by returning the async task in updateAsync() and
    //  testing the output of updateAsync().
    @Test
    fun testGetShortcutResourcesFailedMismatchInSize() {
        // Set up the resources for mocking. rootIdsArray will only return 1 value rather than 2
        whenever(resources.getStringArray(R.array.shortcut_authorities))
            .thenReturn(authoritiesArray)
        whenever(resources.getStringArray(R.array.shortcut_root_ids))
            .thenReturn(arrayOf(rootIdsArray[0]))
        whenever(resources.getStringArray(R.array.shortcut_parent_doc_ids))
            .thenReturn(parentDocIdsArray)
        whenever(resources.getStringArray(R.array.shortcut_titles)).thenReturn(titlesArray)
        whenever(iconTypedArray.length()).thenReturn(2)

        val expected: List<ShortcutResourceValues> = listOf()

        assertEquals(expected, providers.getShortcutResources())
    }
}
