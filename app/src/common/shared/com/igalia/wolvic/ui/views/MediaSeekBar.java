package com.igalia.wolvic.ui.views;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.igalia.wolvic.R;

public class MediaSeekBar extends LinearLayout implements SeekBar.OnSeekBarChangeListener {
    private SeekBar mSeekBar;
    private TextView mLeftText;
    private TextView mRightText;
    private ImageView mLiveIcon;
    private double mDuration;
    private double mCurrentTime;
    private double mBuffered;
    private boolean mTouching;
    private boolean mSeekable = true;
    private Delegate mDelegate;
    private Handler mHandler;

    public MediaSeekBar(Context context) {
        super(context);
        initialize();
    }

    public MediaSeekBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public MediaSeekBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public MediaSeekBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    public interface Delegate {
        void onSeekDragStart();
        void onSeek(double aTargetTime);
        void onSeekDragEnd();
        void onSeekHoverStart();
        void onSeekHoverEnd();
        void onSeekPreview(String aText, double aRatio);
    }

    private void initialize() {
        inflate(getContext(), R.layout.media_controls_seek_bar, this);
        mHandler = new Handler();
        mSeekBar = findViewById(R.id.mediaSeekBar);
        mLeftText = findViewById(R.id.mediaSeekLeftLabel);
        mRightText = findViewById(R.id.mediaSeekRightLabel);
        mLiveIcon = findViewById(R.id.mediaIconLive);
        mLeftText.setText("0:00");
        mRightText.setText("0:00");
        mSeekBar.setProgress(0);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setEnabled(false);
        mSeekBar.setOnHoverListener((view, event) -> {
            if (mDelegate == null || mDuration <= 0) {
                return false;
            }
            boolean notify = false;
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                mDelegate.onSeekHoverStart();
                notify = true;
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                mDelegate.onSeekHoverEnd();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
                notify = true;
            }

            if (notify) {
                final float ratio = event.getX() / view.getWidth();
                notifySeekPreview(mDuration * ratio);
            }

            return false;
        });
    }

    private String formatTime(double aSeconds) {
        final int total = (int)Math.floor(aSeconds);

        final int seconds = total % 60;
        final int minutes = (total / 60) % 60;
        final int hours = total / 3600;
        if (mDuration >= 3600 | hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public void setDelegate(Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    public void setCurrentTime(double aTime) {
        mCurrentTime = aTime;
        if (mSeekable) {
            mLeftText.setText(formatTime(aTime));
            updateProgress();
        }
    }

    public void setDuration(double aDuration) {
        mDuration = aDuration;
        mRightText.setText(formatTime(aDuration));
        if (mDuration > 0 && mSeekable) {
            updateProgress();
            updateBufferedProgress();
            mSeekBar.setEnabled(true);
        }
    }

    public void setSeekable(boolean aSeekable) {
        if (mSeekable == aSeekable) {
            return;
        }
        mSeekable = aSeekable;
        if (mSeekable) {
            mLeftText.setText(formatTime(mCurrentTime));
            if (mDuration > 0) {
                updateProgress();
                updateBufferedProgress();
            }
        } else {
            mLeftText.setText(R.string.video_controls_live);
            mSeekBar.setProgress(mSeekBar.getMax());
        }

        mRightText.setVisibility(mSeekable ? View.VISIBLE : View.GONE);
        mSeekBar.getThumb().mutate().setAlpha(mSeekable ? 255 : 0);
        mSeekBar.setEnabled(mSeekable && mDuration > 0);
        mLiveIcon.setVisibility(aSeekable ? View.GONE : View.VISIBLE);
    }

    public void setEnabled(boolean aEnabled) {
        setSeekable(aEnabled && mSeekable);
        if (!aEnabled) {
            mLeftText.setText("");
            mLiveIcon.setVisibility(View.GONE);
        }
    }

    public void setBuffered(double aBuffered) {
        mBuffered = aBuffered;
        if (mSeekable) {
            updateBufferedProgress();
        }
    }

    public View getSeekBarView() {
        return mSeekBar;
    }

    private void updateProgress() {
        if (mTouching || mDuration <= 0) {
            return;
        }
        double t = mCurrentTime / mDuration;
        mSeekBar.setProgress((int)(t * mSeekBar.getMax()));
    }

    private void updateBufferedProgress() {
        if (mDuration <= 0) {
            mSeekBar.setSecondaryProgress(0);
            return;
        }
        double t = mBuffered / mDuration;
        mSeekBar.setSecondaryProgress((int)(t * mSeekBar.getMax()));
    }

    private void notifySeekProgress() {
        if (mDelegate != null) {
            mDelegate.onSeek(getTargetTime());
        }
    }

    private void notifySeekPreview(double aTargetTime) {
        if (mDelegate != null) {
            mDelegate.onSeekPreview(formatTime(aTargetTime), aTargetTime / mDuration);
        }
    }

    private double getTargetTime() {
        return mDuration * (double) mSeekBar.getProgress() / (double) mSeekBar.getMax();
    }

    // SeekBar.OnSeekBarChangeListener
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && mDelegate != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler.postDelayed(this::notifySeekProgress, 250);
            notifySeekPreview(getTargetTime());
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mTouching = true;
        if (mDelegate != null) {
            mDelegate.onSeekDragStart();
            notifySeekPreview(getTargetTime());
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mTouching = false;
        if (mDelegate != null) {
            mHandler.removeCallbacksAndMessages(null);
            notifySeekProgress();
            mDelegate.onSeekDragEnd();
        }
    }
}
