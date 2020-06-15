package org.mozilla.vrbrowser.ui.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;

public class VolumeControl extends FrameLayout implements SeekBar.OnSeekBarChangeListener {
    private SeekBar mSeekBar;
    private double mVolume;
    private boolean mMuted;
    private boolean mTouching;
    private Delegate mDelegate;

    public VolumeControl(Context context) {
        super(context);
        initialize();
    }

    public VolumeControl(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public VolumeControl(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public VolumeControl(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initialize() {
        inflate(getContext(), R.layout.volume_control, this);
        mSeekBar = findViewById(R.id.volumeSeekBar);
        mSeekBar.setProgress(100);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setOnTouchListener((v, event) -> {

            if ((event.getAction() == MotionEvent.ACTION_UP) || (event.getAction() == MotionEvent.ACTION_CANCEL)) {
                if ((event.getAction() == MotionEvent.ACTION_CANCEL) && (mDelegate != null)) {
                    mDelegate.onSeekBarActionCancelled();
                }

                return true;
            }
            return false;
        });
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void setVolume(double aVolume) {
        mVolume = aVolume;
        if (!mTouching) {
            updateProgress();
        }
    }

    public void setMuted(boolean aMuted) {
        if (mMuted == aMuted) {
            return;
        }
        mMuted = aMuted;
        if (mMuted && !mTouching) {
            mSeekBar.setProgress(0);
        } else if (!mMuted) {
            updateProgress();
        }
    }

    private void updateProgress() {
        mSeekBar.setProgress((int) (mVolume * mSeekBar.getMax()));
    }

    // SeekBar.OnSeekBarChangeListener
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && mDelegate != null) {
            mDelegate.onVolumeChange((double) progress / (double) seekBar.getMax());
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mTouching = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mTouching = false;
    }

    public interface Delegate {
        void onVolumeChange(double aVolume);

        void onSeekBarActionCancelled();
    }
}
