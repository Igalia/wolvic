/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.ui.widgets.dialogs;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.databinding.PromptDialogBinding;
import com.igalia.wolvic.ui.widgets.WidgetPlacement;
import com.igalia.wolvic.utils.BitmapCache;
import com.igalia.wolvic.utils.ViewUtils;

import java.io.IOException;
import java.net.URL;

public class PromptDialogWidget extends UIDialog {

    public interface Delegate {
        void onButtonClicked(int index, boolean isChecked);
        default void onDismiss() {}
    }

    public static final int NEGATIVE = 0;
    public static final int POSITIVE = 1;
    public static final int OTHER = 2;

    protected PromptDialogBinding mBinding;
    private Delegate mAppDialogDelegate;
    private ViewUtils.LinkClickableSpan mLinkDelegate;

    public PromptDialogWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        updateUI();
    }

    public void updateUI() {
        removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate this data binding layout
        mBinding = DataBindingUtil.inflate(inflater, R.layout.prompt_dialog, this, true);

        mBinding.leftButton.setOnClickListener(v ->  {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(NEGATIVE, isChecked());
            }
        });
        mBinding.rightButton.setOnClickListener(v -> {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(POSITIVE, isChecked());
            }
        });
        mBinding.optionallyAddedButton.setOnClickListener(v -> {
            if (mAppDialogDelegate != null) {
                mAppDialogDelegate.onButtonClicked(OTHER, isChecked());
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        updateUI();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        // We align it at the same position as the settings panel
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.prompt_dialog_height);
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorY = 0.0f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_y) -
                WidgetPlacement.unitFromMeters(getContext(), R.dimen.window_world_y);
        updatePlacementTranslationZ();
    }

    @Override
    public void updatePlacementTranslationZ() {
        getPlacement().translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.settings_world_z) -
                WidgetPlacement.getWindowWorldZMeters(getContext());
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        mWidgetPlacement.parentHandle = mWidgetManager.getFocusedWindow().getHandle();

        super.show(aShowFlags);
    }

    public void setButtonsDelegate(Delegate delegate) {
        mAppDialogDelegate = delegate;
    }

    public void setLinkDelegate(@NonNull ViewUtils.LinkClickableSpan delegate) {
        mLinkDelegate = delegate;
    }

    public void setIconVisible(boolean visible) {
        mBinding.imageContainer.setVisibility(visible ? VISIBLE: GONE);
    }

    public void setIcon(Drawable icon) {
        mBinding.icon.setImageDrawable(icon);
    }

    public void setIcon(@DrawableRes int icon) {
        mBinding.icon.setImageResource(icon);
    }

    public void setIcon(String iconUrl) {
        BitmapCache.getInstance(getContext()).getBitmap(iconUrl).thenAccept(bitmap -> {
            if (bitmap == null) {
                ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().backgroundThread().post(() -> {
                    try {
                        URL url = new URL(iconUrl);
                        Bitmap icon = BitmapFactory.decodeStream(url.openStream());
                        ((VRBrowserApplication) getContext().getApplicationContext()).getExecutors().mainThread().execute(() -> {
                            BitmapDrawable iconDrawable = new BitmapDrawable(getContext().getResources(), icon);
                            BitmapCache.getInstance(getContext()).addBitmap(iconUrl, icon);
                            mBinding.icon.setImageDrawable(iconDrawable);
                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            } else {
                BitmapDrawable iconDrawable = new BitmapDrawable(getContext().getResources(), bitmap);
                mBinding.icon.setImageDrawable(iconDrawable);
            }
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }

    public void setTitle(@StringRes int title) {
        if (title != -1) {
            mBinding.title.setText(title);

        } else {
            mBinding.title.clearComposingText();
        }
    }

    public void setTitle(String title) {
        if (title != null) {
            mBinding.title.setText(title);

        } else {
            mBinding.title.clearComposingText();
        }
    }

    public void setBody(String body) {
        if (body != null) {
            ViewUtils.setTextViewHTML(mBinding.body, body, (widget, url) -> {
                if (mLinkDelegate != null) {
                    mLinkDelegate.onClick(PromptDialogWidget.this, url);
                }
            });

        } else {
            mBinding.body.clearComposingText();
        }
    }

    public void setBody(@StringRes int body) {
        if (body != -1) {
            ViewUtils.setTextViewHTML(mBinding.body, getResources().getString(body), (widget, url) -> {
                if (mLinkDelegate != null) {
                    mLinkDelegate.onClick(PromptDialogWidget.this, url);
                }
            });

        } else {
            mBinding.body.clearComposingText();
        }
    }

    public void setBody(CharSequence body) {
        if (body != null) {
            mBinding.body.setText(body);

        } else {
            mBinding.body.clearComposingText();
        }
    }

    public void setCheckboxVisible(boolean visible) {
        mBinding.checkboxContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setCheckboxText(@StringRes int text) {
        mBinding.checkbox.setText(text);
    }

    public void setCheckboxText(String text) {
        mBinding.checkbox.setText(text);
    }

    public void setDescriptionVisible(boolean visible) {
        mBinding.descriptionContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setDescription(@StringRes int description) {
        mBinding.description.setText(description);
    }

    public void setDescription(String description) {
        mBinding.description.setText(description);
    }

    public void setButtons(@NonNull @StringRes int[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[NEGATIVE]);
            mBinding.leftButton.setVisibility(View.VISIBLE);
        } else {
            mBinding.leftButton.setVisibility(View.GONE);
        }

        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[POSITIVE]);
            mBinding.rightButton.setVisibility(View.VISIBLE);
        } else {
            mBinding.rightButton.setVisibility(View.GONE);
        }

        if (buttons.length > 2) {
            mBinding.optionallyAddedButton.setText(buttons[OTHER]);
            mBinding.optionallyAddedButton.setVisibility(View.VISIBLE);
        } else {
            mBinding.optionallyAddedButton.setVisibility(View.GONE);
        }
    }

    public void setButtons(@NonNull String[] buttons) {
        if (buttons.length > 0) {
            mBinding.leftButton.setText(buttons[NEGATIVE]);
            mBinding.leftButton.setVisibility(View.VISIBLE);
        } else {
            mBinding.leftButton.setVisibility(View.GONE);
        }

        if (buttons.length > 1) {
            mBinding.rightButton.setText(buttons[POSITIVE]);
            mBinding.rightButton.setVisibility(View.VISIBLE);
        } else {
            mBinding.rightButton.setVisibility(View.GONE);
        }

        if (buttons.length > 2) {
            mBinding.optionallyAddedButton.setText(buttons[OTHER]);
            mBinding.optionallyAddedButton.setVisibility(View.VISIBLE);
        } else {
            mBinding.optionallyAddedButton.setVisibility(View.GONE);
        }
    }

    public boolean isChecked() {
        return mBinding.checkbox.isChecked();
    }

    public void setChecked(boolean checked) {
        mBinding.checkbox.setChecked(checked);
    }

    public void setTitleGravity(int gravity) {
        mBinding.title.setGravity(gravity);
    }

    public void setBodyGravity(int gravity) {
        mBinding.body.setGravity(gravity);
    }

}
