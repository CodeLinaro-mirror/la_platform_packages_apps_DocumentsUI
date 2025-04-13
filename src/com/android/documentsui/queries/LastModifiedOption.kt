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
package com.android.documentsui.queries

import com.android.documentsui.R

/**
 * Enumerates possible options for the last modified filters. These values correspond directly
 * to the values of hte search_last_modified_menu.
 */
enum class LastModifiedOption(val value: Int) {
    ANY_TIME(R.id.last_modified_any_time_option),
    LAST_DAY(R.id.last_modified_1_day_option),
    LAST_2_DAYS(R.id.last_modified_2_days_option),
    LAST_7_DAYS(R.id.last_modified_7_days_option),
    LAST_30_DAYS(R.id.last_modified_30_days_option),
    LAST_365_DAYS(R.id.last_modified_365_days_option),
}

/**
 * For the given integer value, attempts to return the corresponding LastModifiedOption enum.
 */
fun lastModifiedOptionFor(value: Int): LastModifiedOption? =
    enumValues<LastModifiedOption>().firstOrNull { it.ordinal == value }
