package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.view.Gravity;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.ui.widgets.dialogs.PromptDialogWidget;

public class PromptData {

    @IconType
    public int getIconType() {
        return iconType;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    @Nullable
    public String getIconUrl() {
        return iconUrl;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    @Nullable
    public String getCheckboxText() {
        return checkboxText;
    }

    public int getBodyGravity() {
        return bodyGravity;
    }

    @NonNull
    public String[] getBtnMsg() {
        return btnMsg;
    }

    @Nullable
    public PromptDialogWidget.Delegate getCallback() {
        return callback;
    }

    @IntDef(value = { NONE, URL, RES })
    public @interface IconType {}
    public static final int NONE = 0;
    public static final int RES = 1;
    public static final int URL = 2;

    @IconType
    private int iconType;
    @DrawableRes
    int iconRes;
    @Nullable
    String iconUrl;
    @Nullable
    private String title;
    @Nullable
    private String body;
    @Nullable
    private String checkboxText;
    private int bodyGravity;
    @Nullable
    private String[] btnMsg;
    @Nullable
    private PromptDialogWidget.Delegate callback;

    private PromptData(@NonNull PromptData.Builder builder) {
        iconType = builder.iconType;
        iconRes = builder.iconRes;
        iconUrl = builder.iconUrl;
        title = builder.title;
        body = builder.body;
        checkboxText = builder.checkboxText;
        bodyGravity = builder.bodyGravity;
        btnMsg = builder.btnMsg;
        callback = builder.callback;
    }

    public static class Builder {

        @IconType int iconType;
        @DrawableRes int iconRes;
        @Nullable String iconUrl;
        @Nullable String title = null;
        @Nullable String body = null;
        @Nullable String checkboxText;
        int bodyGravity = Gravity.NO_GRAVITY;
        @Nullable String[] btnMsg = null;
        @Nullable PromptDialogWidget.Delegate callback;

        public Builder() {
        }

        public Builder withTitle(@NonNull String title) {
            this.title = title;
            return this;
        }

        public Builder withBody(@NonNull String body) {
            this.body = body;
            return this;
        }

        public Builder withBodyGravity(int gravity) {
            this.bodyGravity = gravity;
            return this;
        }

        public Builder withIconRes(@DrawableRes int iconRes) {
            this.iconType = RES;
            this.iconRes = iconRes;
            return this;
        }

        public Builder withIconUrl(@NonNull String iconUrl) {
            this.iconType = URL;
            this.iconUrl = iconUrl;
            return this;
        }

        public Builder withBtnMsg(@NonNull String[] btnMsg) {
            this.btnMsg = btnMsg;
            return this;
        }

        public Builder withCheckboxText(@Nullable String text) {
            this.checkboxText = text;
            return this;
        }
        public Builder withCallback(@Nullable PromptDialogWidget.Delegate callback) {
            this.callback = callback;
            return this;
        }

        public PromptData build(){
            return new PromptData(this);
        }
    }
}
