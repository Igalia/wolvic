package org.mozilla.vrbrowser.ui.views;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.annotation.NonNull;

class ShapeClippedEventDelegate extends ClippedEventDelegate {

    ShapeClippedEventDelegate(@NonNull View view) {
        super(view);
    }

    @Override
    boolean onUpdateRegion() {
        Path path = null;
        if (mView.getBackground() != null && mView.getBackground().getCurrent() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) mView.getBackground().getCurrent();
            int shape = background.getShape();
            RectF bounds = new RectF(background.getBounds());
            switch (shape) {
                case GradientDrawable.OVAL:
                    path = new Path();
                    path.addOval(bounds, Path.Direction.CCW);
                    break;

                case GradientDrawable.RECTANGLE:
                    float[] radii;
                    try {
                        radii = background.getCornerRadii();

                    } catch (NullPointerException e) {
                        radii = new float[]{0, 0, 0, 0, 0, 0, 0, 0};
                    }
                    path = new Path();
                    path.addRoundRect(bounds, radii, Path.Direction.CCW);
                    break;
                case GradientDrawable.LINE:
                case GradientDrawable.RING:
                    break;
            }

        } else {
            if (mView.getBackground() != null) {
                path = new Path();
                path.addRect(new RectF(mView.getBackground().getBounds()), Path.Direction.CCW);
            }
        }

        if (path != null) {
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);

            bounds = new RectF();
            path.computeBounds(bounds, true);
            mRegion = new Region();
            mRegion.setPath(path, new Region((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));
        }

        return true;
    }

}
