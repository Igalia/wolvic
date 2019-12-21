package org.mozilla.vrbrowser.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;

import org.mozilla.vrbrowser.ui.widgets.UIWidget;

public class ViewUtils {

    public interface LinkClickableSpan {
        void onClick(@NonNull View widget, @NonNull String url);
    }

    public static void makeLinkClickable(@NonNull SpannableStringBuilder strBuilder, @NonNull final URLSpan span, @NonNull LinkClickableSpan listener)
    {
        int start = strBuilder.getSpanStart(span);
        int end = strBuilder.getSpanEnd(span);
        int flags = strBuilder.getSpanFlags(span);
        ClickableSpan clickable = new ClickableSpan() {
            public void onClick(View view) {
                listener.onClick(view, span.getURL());
            }
        };
        strBuilder.setSpan(clickable, start, end, flags);
        strBuilder.removeSpan(span);
    }

    public static void setTextViewHTML(@NonNull TextView text, @NonNull String html, LinkClickableSpan listener)
    {
        CharSequence sequence = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY);
        SpannableStringBuilder strBuilder = new SpannableStringBuilder(sequence);
        URLSpan[] urls = strBuilder.getSpans(0, sequence.length(), URLSpan.class);
        for(URLSpan span : urls) {
            makeLinkClickable(strBuilder, span, listener);
        }
        text.setText(strBuilder);
        text.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public enum TooltipPosition {
        TOP(0), BOTTOM(1);
        int id;

        TooltipPosition(int id) {
            this.id = id;
        }

        public static TooltipPosition fromId(int id) {
            for (TooltipPosition f : values()) {
                if (f.id == id) return f;
            }
            throw new IllegalArgumentException();
        }
    }

    public static UIWidget getParentWidget(@NonNull View view) {
        if (view == null) {
            return null;
        }

        ViewParent v = view.getParent();
        if (v instanceof UIWidget) {
            return (UIWidget)v;

        } else if (v instanceof View){
            return getParentWidget((View)v);

        } else {
            return null;
        }
    }

    public static Spanned getSpannedText(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(text);
        }
    }

    public static boolean isChildrenOf(@NonNull View parent, @NonNull View view) {
        if (parent == null || view == null) {
            return false;
        }

        if (!(parent instanceof ViewGroup)) {
            return false;
        }

        return parent.findViewById(view.getId()) != null;
    }

    public static boolean isEqualOrChildrenOf(@NonNull ViewGroup aParent, @NonNull View aView) {
        if (aParent == aView) {
            return true;
        }
        return isChildrenOf(aParent, aView);
    }

    public static boolean isInsideView(@NonNull View view, int rx, int ry) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = view.getWidth();
        int h = view.getHeight();

        if (rx < x || rx > x + w || ry < y || ry > y + h) {
            return false;
        }
        return true;
    }

    public static int ARGBtoRGBA(int c) {
        return (c & 0x00FFFFFF) << 8 | (c & 0xFF000000) >>> 24;
    }

    public static float GetLetterPositionX(@NonNull TextView aView, int aLetterIndex, boolean aClamp) {
        Layout layout = aView.getLayout();
        if (layout == null) {
            return 0;
        }
        float x = layout.getPrimaryHorizontal(aLetterIndex);
        x += aView.getPaddingLeft();
        x -= aView.getScrollX();
        if (aClamp && x > (aView.getMeasuredWidth() - aView.getPaddingRight())) {
            x = aView.getMeasuredWidth() - aView.getPaddingRight();
        }
        if (aClamp && x < aView.getPaddingLeft()) {
            x = aView.getPaddingLeft();
        }
        return x;
    }


    public static int getCursorOffset(@NonNull EditText aView, float aX) {
        Layout layout = aView.getLayout();
        if (layout != null) {
            float x = aX + aView.getScrollX() - aView.getPaddingLeft();
            return layout.getOffsetForHorizontal(0, x);
        }

        return -1;
    }

    public static void placeSelection(@NonNull EditText aView, int offset1, int offset2) {
        if (offset1 < 0 || offset2 < 0 || offset1 == offset2) {
            return;
        }

        int start = Math.min(offset1, offset2);
        int end = Math.max(offset1, offset2);
        aView.setSelection(start, end);
    }

    static class StickyClickListener implements View.OnTouchListener {
        private boolean mTouched;
        private View.OnClickListener mClickListener;

        StickyClickListener(View.OnClickListener aListener) {
            mClickListener = aListener;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    mTouched = true;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mTouched && ViewUtils.isInsideView(view, (int)event.getRawX(), (int)event.getRawY())) {
                        view.requestFocus();
                        view.requestFocusFromTouch();
                        if (mClickListener != null) {
                            mClickListener.onClick(view);
                        }
                    }
                    view.setPressed(false);
                    mTouched = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    mTouched = false;
                    return true;
            }
            return false;
        }
    }

    public static void setStickyClickListener(@NonNull View aView, View.OnClickListener aCallback) {
        aView.setOnTouchListener(new StickyClickListener(aCallback));
    }

    @NonNull
    public static Bitmap getRoundedCroppedBitmap(@NonNull Bitmap bitmap) {
        int widthLight = bitmap.getWidth();
        int heightLight = bitmap.getHeight();

        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        Paint paintColor = new Paint();
        paintColor.setFlags(Paint.ANTI_ALIAS_FLAG);

        RectF rectF = new RectF(new Rect(0, 0, widthLight, heightLight));

        canvas.drawRoundRect(rectF, widthLight / 2 ,heightLight / 2,paintColor);

        Paint paintImage = new Paint();
        paintImage.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        canvas.drawBitmap(bitmap, 0, 0, paintImage);

        return output;
    }
}
