/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.vrbrowser.ui;

import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.search.SearchEngine;
import org.mozilla.vrbrowser.telemetry.TelemetryWrapper;

import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

public class NavigationURLBar extends FrameLayout {
    private EditText mURL;
    private ImageButton mMicrophoneButton;
    private ImageView mInsecureIcon;
    private ImageView mLoadingView;
    private Animation mLoadingAnimation;
    private RelativeLayout mURLLeftContainer;
    private boolean mIsLoading = false;
    private boolean mIsInsecure = false;
    private int mDefaultURLLeftPadding = 0;
    private int mURLProtocolColor;
    private int mURLWebsiteColor;
    private Pattern mURLPattern;

    public NavigationURLBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.navigation_url, this);
        mURLPattern = Pattern.compile("[\\d\\w][.][\\d\\w]");
        mURL = findViewById(R.id.urlEditText);
        mURL.setShowSoftInputOnFocus(false);
        mURL.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView aTextView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleURLEdit(aTextView.getText().toString());
                return true;
            }
            return false;
            }
        });
        mURL.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (b && mURL.getText().length() > 0) {
                    showVoiceSearch(false);
                }
            }
        });
        mURL.addTextChangedListener(mURLTextWatcher);
        mMicrophoneButton = findViewById(R.id.microphoneButton);
        mMicrophoneButton.setOnClickListener(mMicrophoneListener);
        mURLLeftContainer = findViewById(R.id.urlLeftContainer);
        mInsecureIcon = findViewById(R.id.insecureIcon);
        mLoadingView = findViewById(R.id.loadingView);
        mLoadingAnimation = AnimationUtils.loadAnimation(aContext, R.anim.loading);
        mDefaultURLLeftPadding = mURL.getPaddingLeft();

        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = aContext.getTheme();
        theme.resolveAttribute(R.attr.urlProtocolColor, typedValue, true);
        mURLProtocolColor = typedValue.data;
        theme.resolveAttribute(R.attr.urlWebsiteColor, typedValue, true);
        mURLWebsiteColor = typedValue.data;

        // Prevent the URL TextEdit to get focus when user touches something outside of it
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
    }

    public void setURL(String aURL) {
        mURL.removeTextChangedListener(mURLTextWatcher);

        int index = -1;
        if (aURL != null) {
            if (aURL.startsWith("jar:"))
                return;
            else if (aURL.startsWith("resource:") || SessionStore.get().isHomeUri(aURL))
                aURL = "";
            else
                index = aURL.indexOf("://");
        }
        mURL.setText(aURL);
        if (index > 0) {
            SpannableString spannable = new SpannableString(aURL);
            ForegroundColorSpan color1 = new ForegroundColorSpan(mURLProtocolColor);
            ForegroundColorSpan color2 = new ForegroundColorSpan(mURLWebsiteColor);
            spannable.setSpan(color1, 0, index + 3, 0);
            spannable.setSpan(color2, index + 3, aURL.length(), 0);
            mURL.setText(spannable);
        } else {
            mURL.setText(aURL);
        }

        mURL.addTextChangedListener(mURLTextWatcher);
    }

    public void setURLText(String aText) {
        mURL.removeTextChangedListener(mURLTextWatcher);
        mURL.setText(aText);
        mURL.addTextChangedListener(mURLTextWatcher);
    }

    public void setIsInsecure(boolean aIsInsecure) {
        if (mIsInsecure != aIsInsecure) {
            mIsInsecure = aIsInsecure;
            syncViews();
        }
    }

    public void setIsLoading(boolean aIsLoading) {
        if (mIsLoading != aIsLoading) {
            mIsLoading = aIsLoading;
            if (mIsLoading) {
                mLoadingView.startAnimation(mLoadingAnimation);
            } else {
                mLoadingView.clearAnimation();
            }
            syncViews();
        }
    }

    public void showVoiceSearch(boolean enabled) {
        if (enabled) {
            mMicrophoneButton.setImageResource(R.drawable.ic_icon_microphone);
            mMicrophoneButton.setOnClickListener(mMicrophoneListener);

        } else {
            mMicrophoneButton.setImageResource(R.drawable.ic_icon_clear);
            mMicrophoneButton.setOnClickListener(mClearListener);
        }
    }

    private void syncViews() {
        boolean showContainer = mIsInsecure || mIsLoading;
        int leftPadding = mDefaultURLLeftPadding;
        if (showContainer) {
            mURLLeftContainer.setVisibility(View.VISIBLE);
            mURLLeftContainer.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mLoadingView.setVisibility(mIsLoading ? View.VISIBLE : View.GONE);
            mInsecureIcon.setVisibility(!mIsLoading && mIsInsecure ? View.VISIBLE : View.GONE);
            leftPadding = mURLLeftContainer.getMeasuredWidth();
        }
        else {
            mURLLeftContainer.setVisibility(View.GONE);
        }

        mURL.setPadding(leftPadding, mURL.getPaddingTop(), mURL.getPaddingRight(), mURL.getPaddingBottom());
    }

    private void handleURLEdit(String text) {
        text = text.trim();
        URI uri = null;
        try {
            boolean hasProtocol = text.contains("://");
            String urlText = text;
            // Detect when the protocol is missing from the URL.
            // Look for a separated '.' in the text with no white spaces.
            if (!hasProtocol && !urlText.contains(" ") && mURLPattern.matcher(urlText).find()) {
                urlText = "https://" + urlText;
                hasProtocol = true;
            }
            if (hasProtocol) {
                URL url = new URL(urlText);
                uri = url.toURI();
            }
        }
        catch (Exception ex) {
        }

        String url;
        if (uri != null) {
            url = uri.toString();
            TelemetryWrapper.urlBarEvent(true);
        } else if (text.startsWith("about:") || text.startsWith("resource://")) {
            url = text;
        } else {
            url = SearchEngine.get(getContext()).getSearchURL(text);

            // Doing search in the URL bar, so sending "aIsURL: false" to telemetry.
            TelemetryWrapper.urlBarEvent(false);
        }

        if (SessionStore.get().getCurrentUri() != url) {
            SessionStore.get().loadUri(url);
        }

        showVoiceSearch(true);
    }

    public void setPrivateMode(boolean isEnabled) {
        if (isEnabled)
            mURL.setBackground(getContext().getDrawable(R.drawable.url_background_private));
        else
            mURL.setBackground(getContext().getDrawable(R.drawable.url_background));
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        mURL.setEnabled(clickable);
    }

    private OnClickListener mMicrophoneListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            TelemetryWrapper.voiceInputEvent();
        }
    };

    private OnClickListener mClearListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            mURL.getText().clear();
        }
    };

    private TextWatcher mURLTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            if (mURL.getText().length() > 0) {
                showVoiceSearch(false);

            } else {
                showVoiceSearch(true);
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    };
}
