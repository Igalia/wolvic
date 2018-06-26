package org.mozilla.vrbrowser.ui;

import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;

public class AnimationHelper {
    public static final long FADE_ANIMATION_DURATION = 150;
    
    public static void fadeIn(View aView, long delay) {
        aView.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(0, 1);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.setDuration(FADE_ANIMATION_DURATION);
        if (delay > 0) {
            animation.setStartOffset(delay);
        }
        aView.setAnimation(animation);
    }

    public static void fadeOut(final View aView, long delay) {
        aView.setVisibility(View.VISIBLE);
        Animation animation = new AlphaAnimation(1, 0);
        animation.setInterpolator(new AccelerateInterpolator());
        animation.setDuration(FADE_ANIMATION_DURATION);
        if (delay > 0) {
            animation.setStartOffset(delay);
        }
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                aView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        aView.setAnimation(animation);
    }
}
