/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.audio;

import com.igalia.wolvic.R;

public class VRAudioTheme implements AudioEngine.AudioTheme {
    @Override
    public int getResourceId(AudioEngine.Sound aSound) {
        switch (aSound) {
            case KEYBOARD: return R.raw.keyboard_sound;
            case CLICK: return R.raw.click_sound;
            case BACK: return R.raw.back_sound;
            case EXIT: return R.raw.exit_sound;
            case ERROR: return R.raw.error_sound;
        }
        return 0;
    }
}
