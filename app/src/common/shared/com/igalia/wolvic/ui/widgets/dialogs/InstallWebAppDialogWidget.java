package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.engine.SessionStore;
import com.igalia.wolvic.ui.adapters.WebApp;
import com.igalia.wolvic.utils.StringUtils;

import mozilla.components.browser.icons.IconRequest;

public class InstallWebAppDialogWidget extends PromptDialogWidget {

    private final WebApp mWebApp;

    public InstallWebAppDialogWidget(@NonNull Context aContext, @NonNull WebApp webApp) {
        super(aContext);

        mWebApp = webApp;

        initialize(aContext);
    }

    @Override
    public void updateUI() {
        super.updateUI();

        setButtons(new int[]{
                R.string.web_apps_dialog_cancel,
                R.string.web_apps_dialog_install
        });
        setCheckboxVisible(false);
        setBody(R.string.web_apps_dialog_body);

        // this may happen when the object is created
        if (mWebApp == null) {
            return;
        }

        if (StringUtils.isEmpty(mWebApp.getName())) {
            setTitle(R.string.web_apps_dialog_title);
        } else {
            setTitle(getContext().getString(R.string.web_apps_dialog_title_parameter, mWebApp.getShortName()));
        }

        SessionStore.get().getBrowserIcons().loadIntoView(mBinding.icon,
                mWebApp.getStartUrl(), mWebApp.getIconResources(), IconRequest.Size.LAUNCHER);
    }
}
