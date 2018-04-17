package org.mozilla.vrbrowser;

public interface WidgetManagerDelegate {
    interface WidgetAddCallback {
        void onWidgetAdd(Widget aWidget);
    }

    void addWidget(WidgetPlacement aPlacement, WidgetAddCallback aCallback);
    void setWidgetVisible(int aHandle, boolean aVisible);
    void removeWidget(int aHandle);
}
