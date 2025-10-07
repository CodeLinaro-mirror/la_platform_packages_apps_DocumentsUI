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

import android.platform.test.microbenchmark.Functional
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags
import com.android.documentsui.util.FlagUtils
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Suite
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError

/** Enum for different synthetic target profiles. */
enum class SyntheticTarget(val flags: Map<String, Boolean>) {
    NO_OVERRIDE(mapOf()),

    // Staging mirrors the flags in desktop trunk_staging although the flag values here are advanced
    // before advancing the flags in aconfig to ensure tests will pass.
    STAGING(
        mapOf(
            Flags.FLAG_USE_MATERIAL3 to true,
            Flags.FLAG_DESKTOP_FILE_HANDLING_RO to true,
            Flags.FLAG_DESKTOP_UX_PHASE_2_RO to true,
            Flags.FLAG_ENABLE_TRASH_FLOW_RO to false,
            Flags.FLAG_USE_SEARCH_V2_READ_ONLY to true,
            Flags.FLAG_VISUAL_SIGNALS_RO to true,
            Flags.FLAG_ZIP_NG_RO to true,
        )
    ),

    // Prod mirrors the flags in phone nextfood. Similar to staging, the flag values here are
    // advanced before aconfig flags.
    PROD(
        mapOf(
            Flags.FLAG_USE_MATERIAL3 to true,
            Flags.FLAG_DESKTOP_FILE_HANDLING_RO to false,
            Flags.FLAG_DESKTOP_UX_PHASE_2_RO to false,
            Flags.FLAG_ENABLE_TRASH_FLOW_RO to false,
            Flags.FLAG_USE_SEARCH_V2_READ_ONLY to false,
            Flags.FLAG_VISUAL_SIGNALS_RO to false,
            Flags.FLAG_ZIP_NG_RO to false,
        )
    ),

    // Mainline mirrors the flags in last Android release (similar to mainline module flags). The
    // flag values are updated when a new version of Android is released.
    MAINLINE(
        mapOf(
            Flags.FLAG_USE_MATERIAL3 to false,
            Flags.FLAG_DESKTOP_FILE_HANDLING_RO to false,
            Flags.FLAG_DESKTOP_UX_PHASE_2_RO to false,
            Flags.FLAG_ENABLE_TRASH_FLOW_RO to false,
            Flags.FLAG_USE_SEARCH_V2_READ_ONLY to false,
            Flags.FLAG_VISUAL_SIGNALS_RO to false,
            Flags.FLAG_ZIP_NG_RO to false,
        )
    ),
}

/** Annotation to run tests with a list of synthetic targets. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ParameterizeSyntheticTargets(vararg val value: SyntheticTarget)

/**
 * Custom `Suite` runner wrapping over `Functional` test runner and enables flag parameterization.
 *
 * <p>This runner supports `@ParameterizeSyntheticTargets` to run tests against a list of synthetic
 * targets that mimic a specific environment (e.g. STAGING, PROD).
 *
 * <p>The parameterization can be disabled with:
 * ```
 * $ atest DocumentsUIGoogleTests -- \
 *     --test-arg com.android.tradefed.testtype.AndroidJUnitTest:instrumentation-arg:documentsui_disable_parameterization:=true
 * ```
 */
class TestRunner(klass: Class<*>) : Suite(klass, createRunners(klass)) {
    companion object {
        /** Creates a runner for each synthetic target. */
        private fun createRunners(klass: Class<*>): List<Runner> {
            val args = InstrumentationRegistry.getArguments()
            if (args.getString("documentsui_disable_parameterization") == "true") {
                return listOf(object : Functional(klass) {})
            }

            val syntheticTargets = getSyntheticTargets(klass)
            return syntheticTargets.map { syntheticTarget ->
                object : Functional(klass) {
                    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
                        val originalOverrides = FlagUtils.getInstance().copyOverrideState()
                        try {
                            syntheticTarget.flags.forEach { (flag, state) ->
                                FlagUtils.getInstance().setOverride(flag, state)
                            }
                            super.runChild(method, notifier)
                        } finally {
                            FlagUtils.getInstance().restoreOverrideState(originalOverrides)
                        }
                    }

                    override fun testName(method: FrameworkMethod): String {
                        if (syntheticTarget != SyntheticTarget.NO_OVERRIDE) {
                            return "${method.name}[SyntheticTarget=${syntheticTarget.name}]"
                        }
                        return method.name
                    }
                }
            }
        }

        /**
         * Returns a list of synthetic targets for the given test class.
         *
         * The synthetic targets are retrieved from the @ParameterizeSyntheticTargets annotation on
         * the test class.
         */
        private fun getSyntheticTargets(klass: Class<*>): List<SyntheticTarget> {
            val syntheticTargetsAnnotation =
                klass.getAnnotation(ParameterizeSyntheticTargets::class.java)

            val targets = syntheticTargetsAnnotation?.value ?: arrayOf(SyntheticTarget.NO_OVERRIDE)

            if (targets.isEmpty()) {
                throw InitializationError(
                    "@ParameterizeSyntheticTargets must contain at least one target."
                )
            }
            return targets.toList()
        }
    }
}
