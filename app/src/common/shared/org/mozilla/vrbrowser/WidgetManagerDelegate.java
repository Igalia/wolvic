package org.mozilla.vrbrowser;

public interface WidgetManagerDelegate {
    int newWidgetHandle();
    void addWidget(Widget aWidget);
    void updateWidget(Widget aWidget);
    void removeWidget(Widget aWidget);
}
