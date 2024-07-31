package com.igalia.wolvic.ui.widgets;

import com.igalia.wolvic.browser.engine.Session;

import java.util.List;

public interface TabDelegate {
    void onTabSelect(Session aTab);
    void onTabAdd();
    void onTabsClose(List<Session> aTabs);
}
