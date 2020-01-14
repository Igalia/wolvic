package org.mozilla.vrbrowser.ui.views;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.Deque;

class VectorClippedEventDelegate extends ClippedEventDelegate {

    private @DrawableRes int mRes;

    VectorClippedEventDelegate(@NonNull View view, @DrawableRes int res) {
        super(view);

        mRes = res;
    }

    @Override
    boolean onUpdateRegion() {
        Path path = createPathFromResource(mRes);
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);

        bounds = new RectF();
        path.computeBounds(bounds, true);
        mRegion = new Region();
        mRegion.setPath(path, new Region((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));

        return true;
    }

    private Path createPathFromResource(@DrawableRes int res) {
        VectorShape shape = new VectorShape(mView.getContext(), res);
        shape.onResize(mView.getWidth(), mView.getHeight());
        Deque<VectorShape.Layer> layers = shape.getLayers();
        VectorShape.Layer layer = layers.getFirst();

        // TODO Handle state changes and update the Region based on the new current state shape

        return layer.transformedPath;
    }

}
