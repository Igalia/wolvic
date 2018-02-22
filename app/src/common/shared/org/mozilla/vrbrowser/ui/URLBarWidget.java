/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.ImageButton;

import org.mozilla.vrbrowser.R;

public class URLBarWidget extends UIWidget {
    private ImageButton mBackButton;
    private ImageButton mReloadButton;
    private EditText mURL;

    public URLBarWidget(Context aContext) {
        super(aContext);
    }

    public URLBarWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
    }

    public URLBarWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackButton = findViewById(R.id.backButton);
        mReloadButton = findViewById(R.id.reloadButton);
        mURL = findViewById(R.id.urlBar);
        mURL.setRawInputType(InputType.TYPE_NULL);
        mURL.setTextIsSelectable(false);
        mURL.setCursorVisible(false);
        mURL.setText("http://");
    }
}
