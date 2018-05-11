package org.mozilla.vrbrowser;

import android.support.annotation.Nullable;

public interface WidgetManagerDelegate {
    interface WidgetAddCallback {
        void onWidgetAdd(Widget aWidget);
    }

    void addWidget(WidgetPlacement aPlacement, boolean aVisible, @Nullable WidgetAddCallback aCallback);
    void updateWidget(int aHandle, boolean aVisible, @Nullable WidgetPlacement aPlacement);
    void removeWidget(int aHandle);
}
