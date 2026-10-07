package com.igalia.wolvic.browser.persistence;

import com.igalia.wolvic.browser.engine.SessionState;
import com.igalia.wolvic.ui.widgets.Windows;

import java.util.ArrayList;

public class WindowsState {
    public Windows.WindowPlacement focusedWindowPlacement = Windows.WindowPlacement.FRONT;
    public ArrayList<WindowState> regularWindowsState = new ArrayList<>();
    public ArrayList<SessionState> tabs = new ArrayList<>();
    public boolean privateMode = false;
}
