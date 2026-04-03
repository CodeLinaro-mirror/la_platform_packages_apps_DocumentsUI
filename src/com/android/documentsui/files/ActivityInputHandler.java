/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.files;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.view.KeyEvent;

/**
 * Used by {@link FilesActivity} to manage global keyboard shortcuts tied to file actions
 */
final class ActivityInputHandler {

    private final Runnable mDeleteOrTrashHandler;
    private final Runnable mDeleteForeverHandler;

    ActivityInputHandler(Runnable deleteOrTrashHandler, Runnable deleteForeverHandler) {
        mDeleteOrTrashHandler = deleteOrTrashHandler;
        mDeleteForeverHandler = deleteForeverHandler;
    }

    boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FORWARD_DEL:
                // Standard 'Delete' key should also support Shift for permanent delete
                if (event.isShiftPressed()) {
                    mDeleteForeverHandler.run();
                } else {
                    mDeleteOrTrashHandler.run();
                }
                return true;
            case KeyEvent.KEYCODE_DEL:
                if (isUseMaterial3FlagEnabled()) {
                    // Strict check for Alt + Shift + Backspace
                    if (event.hasModifiers(KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)) {
                        mDeleteForeverHandler.run();
                        return true;
                    }
                    // Strict check for Alt + Backspace
                    if (event.hasModifiers(KeyEvent.META_ALT_ON)) {
                        mDeleteOrTrashHandler.run();
                        return true;
                    }
                } else if (event.isAltPressed()) {
                    if (event.isShiftPressed()) {
                        mDeleteForeverHandler.run();
                    } else {
                        mDeleteOrTrashHandler.run();
                    }
                    return true;
                }
                return false;
            default:
                return false;
        }
    }
}
