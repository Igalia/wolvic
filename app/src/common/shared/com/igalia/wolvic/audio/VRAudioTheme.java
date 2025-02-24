/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.audio;

public class VRAudioTheme implements AudioEngine.AudioTheme {
    @Override
    public String getPath(AudioEngine.Sound aSound) {
        String name = filename(aSound);
        if (name != null) {
            return "sounds/" + name;
        }
        return null;
    }

    private String filename(AudioEngine.Sound aSound) {
        switch (aSound) {
            case KEYBOARD: return "keyboard_sound.wav";
            case CLICK: return "click.wav";
            case BACK: return "back.wav";
            case EXIT: return "exit.wav";
            case ERROR: return "error.wav";
        }

        return null;
    }
}
