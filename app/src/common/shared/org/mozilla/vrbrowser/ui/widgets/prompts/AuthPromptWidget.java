package org.mozilla.vrbrowser.ui.widgets.prompts;

import android.content.Context;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.views.settings.SettingsEditText;

public class AuthPromptWidget extends PromptWidget {

    public interface AuthPromptDelegate extends PromptDelegate {
        default void confirm(String password) {}
        default void confirm(String username, String password) {}
    }

    private AudioEngine mAudio;
    private SettingsEditText mUsernameText;
    private SettingsEditText mPasswordText;
    private TextView mUsernameTextLabel;
    private Button mOkButton;
    private Button mCancelButton;

    public AuthPromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public AuthPromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public AuthPromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    protected void initialize(Context aContext) {
        inflate(aContext, R.layout.prompt_auth, this);

        mAudio = AudioEngine.fromContext(aContext);

        mLayout = findViewById(R.id.layout);

        mTitle = findViewById(R.id.textTitle);
        mMessage = findViewById(R.id.textMessage);
        mMessage.setMovementMethod(new ScrollingMovementMethod());
        mUsernameText = findViewById(R.id.authUsername);
        mUsernameText.setShowSoftInputOnFocus(false);
        mUsernameTextLabel = findViewById(R.id.authUsernameLabel);
        mPasswordText = findViewById(R.id.authPassword);
        mPasswordText.setShowSoftInputOnFocus(false);

        mOkButton = findViewById(R.id.positiveButton);
        mOkButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            if (mPromptDelegate != null && mPromptDelegate instanceof  AuthPromptDelegate) {
                if (mUsernameText.getVisibility() == VISIBLE) {
                    ((AuthPromptDelegate) mPromptDelegate).confirm(mUsernameText.getText().toString(), mPasswordText.getText().toString());
                } else {
                    ((AuthPromptDelegate) mPromptDelegate).confirm(mPasswordText.getText().toString());
                }
            }

            hide(REMOVE_WIDGET);
        });

        mCancelButton = findViewById(R.id.negativeButton);
        mCancelButton.setOnClickListener(view -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            onDismiss();
        });

        CheckBox showPassword = findViewById(R.id.showPasswordCheckbox);
        showPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordText.setInputType(InputType.TYPE_CLASS_TEXT);
            } else {
                mPasswordText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        });
    }

    public void setAuthOptions(GeckoSession.PromptDelegate.AuthPrompt.AuthOptions aOptions) {
        if (aOptions.username != null) {
            mUsernameText.setText(aOptions.username);
        }
        if (aOptions.password != null) {
            mPasswordText.setText(aOptions.password);
        }
        if ((aOptions.flags & GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0) {
            // Hide the username input if basic auth dialog only requests a password.
            mUsernameText.setVisibility(View.GONE);
            mUsernameTextLabel.setVisibility(View.GONE);
        }
    }

    @Override
    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            mTitle.setText(getContext().getString(R.string.authentication_required));

        } else {
            mTitle.setText(title);
        }
    }

    public void setUsername(@NonNull String username) {
        mUsernameText.setText(username);
    }

    public void setPassword(@NonNull String password) {
        mPasswordText.setText(password);
    }

}
