package org.mozilla.vrbrowser.utils;

import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import org.mozilla.vrbrowser.ui.widgets.UIWidget;

public class ViewUtils {

    public enum TooltipPosition {
        TOP(0), BOTTOM(1);
        int id;

        TooltipPosition(int id) {
            this.id = id;
        }

        public static TooltipPosition fromId(int id) {
            for (TooltipPosition f : values()) {
                if (f.id == id) return f;
            }
            throw new IllegalArgumentException();
        }
    }

    public static UIWidget getParentWidget(@NonNull View view) {
        if (view == null)
            return null;

        ViewParent v = view.getParent();
        if (v instanceof UIWidget) {
            return (UIWidget)v;

        } else if (v instanceof View){
            return getParentWidget((View)v);

        } else {
            return null;
        }
    }

}
