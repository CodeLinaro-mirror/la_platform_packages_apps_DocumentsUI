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

package com.android.documentsui.util

import android.util.Log
import androidx.annotation.AnyRes
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled

/**
 * Mapping of resource IDs from the pre-material3 (aka legacy) theme to the new resource ID in the
 * material3 theme.
 */
private var idMapping: Map<Int, Int> = mapOf()
private var initialized = false

private const val TAG = "ThemeUtils"

/**
 * Only initialize the mapping when the use_material3 flag is enabled, because the IDs for the
 * Material3 version only exists then.
 */
private fun initializeIdMapping() {
  idMapping = mapOf()
}

interface Config {
  /**
   * Material3 is only fully enabled if the config forceMaterial3 is true AND the flag use_material3
   * is enabled.
   */
  var forceMaterial3: Boolean?
}

class Material3ConfigImpl() : Config {
  override var forceMaterial3: Boolean? = null
    set(value) {
      if (field != null) {
        Log.e(TAG, "forceMaterial3 is already set to $forceMaterial3")
        return
      }

      field = value
      if (DEBUG) {
        Log.d(
          TAG,
          "forceMaterial3 initializing with $value use_material3: ${
            isUseMaterial3FlagEnabled()
          }",
        )
      }
    }
}

abstract class Material3Config private constructor() {
  companion object {
    private val _instance: Config by lazy { Material3ConfigImpl() }

    @JvmStatic
    fun getInstance(): Config {
      return _instance
    }

    /**
     * Convert the resource ID from non-Material3 to Material3 version if the Material3 is enabled,
     * otherwise it returns the given ID as is.
     */
    @JvmStatic
    @AnyRes
    fun getRes(@AnyRes originalResourceId: Int): Int {
      if (!isUseMaterial3FlagEnabled()) {
        return originalResourceId
      }
      // TODO(lucmult): Enable this condition when all the resources are merged in one APK.
      // if (!(Material3Config.getInstance().forceMaterial3 ?: false)) {
      //   return originalResourceId
      // }
      if (!initialized) {
        initializeIdMapping()
      }

      val newId = idMapping[originalResourceId] ?: originalResourceId
      if (DEBUG) {
        if (newId != originalResourceId) {
          Log.d(
            TAG,
            "Replacing R ID from ${
              Integer.toHexString(
                originalResourceId
              )
            } to ${Integer.toHexString(newId)}",
          )
        }
      }

      return newId
    }

    @JvmStatic
    fun overrideForTest(overrides: Map<Int, Int>) {
      idMapping = overrides
    }
  }
}
