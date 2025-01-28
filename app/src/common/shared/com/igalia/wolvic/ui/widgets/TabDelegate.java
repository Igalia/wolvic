package com.igalia.wolvic.ui.widgets;

import com.igalia.wolvic.browser.engine.Session;

import java.util.List;

public interface TabDelegate {
    void onTabAdd();
    void onTabSelect(Session aTab);
    void onTabsClose(List<Session> aTabs);
    void onTabsBookmark(List<Session> aTabs);
    void onTabSync();
}
