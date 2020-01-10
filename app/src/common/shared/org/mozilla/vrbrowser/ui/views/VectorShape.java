package org.mozilla.vrbrowser.ui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.shapes.Shape;
import android.util.AttributeSet;
import android.util.Xml;

import androidx.core.graphics.PathParser;

import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

class VectorShape extends Shape {
    private static final String TAG_VECTOR = "vector";
    private static final String TAG_PATH = "path";

    private RectF viewportRect = new RectF();
    private List<Layer> layers = new ArrayList<>();

    VectorShape(Context context, int id) {
        XmlResourceParser parser = context.getResources().getXml(id);
        AttributeSet set = Xml.asAttributeSet(parser);
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (tagName.equals(TAG_VECTOR)) {
                        int[] attrs = { android.R.attr.viewportWidth, android.R.attr.viewportHeight };
                        TypedArray ta = context.obtainStyledAttributes(set, attrs);
                        viewportRect.set(0, 0, ta.getFloat(0, 0), ta.getFloat(1, 0));
                        ta.recycle();

                    } else if (tagName.equals(TAG_PATH)) {
                        int[] attrs = { android.R.attr.name, android.R.attr.fillColor, android.R.attr.pathData };
                        TypedArray ta = context.obtainStyledAttributes(set, attrs);
                        layers.add(new Layer(ta.getString(2), ta.getColor(1, 0xdeadc0de), ta.getString(0)));
                        ta.recycle();
                    }
                }
                eventType = parser.next();
            }

        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
    }

    public Deque<Layer> getLayers() {
        Deque<Layer> outLayers = new LinkedList<>();
        for (Layer layer : layers) {
            outLayers.addLast(layer);
        }

        return outLayers;
    }

    @Override
    protected void onResize(float width, float height) {
        Matrix matrix = new Matrix();
        Region shapeRegion = new Region(0, 0, (int) width, (int) height);
        matrix.setRectToRect(viewportRect, new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);
        for (Layer layer : layers) {
            layer.transform(matrix, shapeRegion);
        }
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        for (Layer layer : layers) {
            canvas.drawPath(layer.transformedPath, layer.paint);
        }
    }

    class Layer {
        Path originalPath;
        Path transformedPath = new Path();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Region region = new Region();
        String name;

        public Layer(String data, int color, String name) {
            originalPath = PathParser.createPathFromPathData(data);
            paint.setColor(color);
            this.name = name;
        }

        public void transform(Matrix matrix, Region clip) {
            originalPath.transform(matrix, transformedPath);
            region.setPath(transformedPath, clip);
        }

        @NotNull
        @Override public String toString() { return name; }
    }
}
