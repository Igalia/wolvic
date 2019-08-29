package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.views.CustomListView;

import java.util.ArrayList;
import java.util.List;

public class SuggestionsWidget extends UIWidget implements WidgetManagerDelegate.FocusChangeListener {

    private CustomListView mList;
    private SuggestionsAdapter mAdapter;
    private Animation mScaleUpAnimation;
    private Animation mScaleDownAnimation;
    private URLBarPopupDelegate mURLBarDelegate;
    private String mHighlightedText;
    private AudioEngine mAudio;

    public interface URLBarPopupDelegate {
        default void OnItemClicked(SuggestionItem item) {};
        default void OnItemDeleted(SuggestionItem item) {};
    }

    public SuggestionsWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public SuggestionsWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public SuggestionsWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.list_popup_window, this);

        mWidgetManager.addFocusChangeListener(this);

        mList = findViewById(R.id.list);

        mScaleUpAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.popup_scaleup);
        mScaleDownAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.popup_scaledown);
        mScaleDownAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                ThreadUtils.postToUiThread(() -> SuggestionsWidget.super.hide(REMOVE_WIDGET));
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        mAdapter = new SuggestionsAdapter(getContext(), R.layout.list_popup_window_item, new ArrayList<>());
        mList.setAdapter(mAdapter);

        mAudio = AudioEngine.fromContext(aContext);

        mHighlightedText = "";
    }

    @Override
    public void releaseWidget() {
        mWidgetManager.removeFocusChangeListener(this);

        super.releaseWidget();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.url_bar_popup_world_width);
        aPlacement.height =  WidgetPlacement.dpDimension(getContext(), R.dimen.url_bar_popup_height);
        aPlacement.parentAnchorX = 0.0f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationX = WidgetPlacement.unitFromMeters(getContext(), R.dimen.url_bar_popup_world_x);
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.url_bar_popup_world_z);
        aPlacement.translationY = WidgetPlacement.unitFromMeters(getContext(), R.dimen.url_bar_popup_world_y);
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);
        mList.startAnimation(mScaleUpAnimation);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        mList.startAnimation(mScaleDownAnimation);
    }

    // FocusChangeListener

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (oldFocus != null && isVisible()) {
            onDismiss();
        }
    }

    public void setURLBarPopupDelegate(URLBarPopupDelegate aDelegate) {
        mURLBarDelegate = aDelegate;
    }

    public void setHighlightedText(String text) {
        mHighlightedText = text;
    }

    public void updateItems(List<SuggestionItem> items) {
        mAdapter.clear();
        mAdapter.addAll(items);
        mAdapter.notifyDataSetChanged();
    }

    public void updatePlacement(int aWidth) {
        mWidgetPlacement.width = aWidth;
        float worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width);
        float aspect = mWidgetPlacement.width / mWidgetPlacement.height;
        float worldHeight = worldWidth / aspect;
        float area = worldWidth * worldHeight;

        float targetWidth = (float) Math.sqrt(area * aspect);

        mWidgetPlacement.worldWidth = targetWidth * (mWidgetPlacement.width/getWorldWidth());
    }

    public static class SuggestionItem {

        public enum Type {
            COMPLETION,
            BOOKMARK,
            HISTORY,
            SUGGESTION
        }

        public String faviconURL;
        public String title;
        public String url;
        public Type type = Type.SUGGESTION;

        public static SuggestionItem create(@NonNull String title, String url, String faviconURL, Type type) {
            SuggestionItem item = new SuggestionItem();
            item.title = title;
            item.url = url;
            item.faviconURL = faviconURL;
            item.type = type;

            return item;
        }
    }

    public class SuggestionsAdapter extends ArrayAdapter<SuggestionItem> {

        private class ItemViewHolder {
            ViewGroup layout;
            ImageView favicon;
            TextView title;
            TextView url;
            ImageButton delete;
            View divider;
        }

        public SuggestionsAdapter(@NonNull Context context, int resource, @NonNull List<SuggestionItem> objects) {
            super(context, resource, objects);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View listItem = convertView;

            ItemViewHolder itemViewHolder;
            if (listItem == null) {
                listItem = LayoutInflater.from(getContext()).inflate(R.layout.list_popup_window_item, parent, false);

                itemViewHolder = new ItemViewHolder();

                itemViewHolder.layout = listItem.findViewById(R.id.layout);
                itemViewHolder.layout.setTag(R.string.position_tag, position);
                itemViewHolder.layout.setOnClickListener(mRowListener);
                itemViewHolder.favicon = listItem.findViewById(R.id.favicon);
                itemViewHolder.title = listItem.findViewById(R.id.title);
                itemViewHolder.url = listItem.findViewById(R.id.url);
                itemViewHolder.delete = listItem.findViewById(R.id.delete);
                itemViewHolder.delete.setTag(R.string.position_tag, position);
                itemViewHolder.delete.setOnClickListener(mDeleteButtonListener);
                itemViewHolder.divider = listItem.findViewById(R.id.divider);

                listItem.setTag(R.string.list_item_view_tag, itemViewHolder);

                listItem.setOnHoverListener(mHoverListener);
                listItem.setOnTouchListener(mTouchListener);

            } else {
                itemViewHolder = (ItemViewHolder) listItem.getTag(R.string.list_item_view_tag);
                itemViewHolder.layout.setTag(R.string.position_tag, position);
                itemViewHolder.delete.setTag(R.string.position_tag, position);
            }

            SuggestionItem selectedItem = getItem(position);

            // Make search substring as bold
            itemViewHolder.title.setText(createHighlightedText(selectedItem.title));

            // Set the URL text
            if (selectedItem.url == null) {
                itemViewHolder.url.setVisibility(GONE);

            } else {
                itemViewHolder.url.setVisibility(VISIBLE);
                itemViewHolder.url.setText(createHighlightedText(selectedItem.url));
            }

            // Set the description
            if (selectedItem.faviconURL == null) {
                itemViewHolder.favicon.setVisibility(GONE);

            } else {
                // TODO: Load favicon
                itemViewHolder.favicon.setVisibility(VISIBLE);
            }

            // Type related
            if (selectedItem.type == SuggestionItem.Type.SUGGESTION) {
                itemViewHolder.favicon.setImageResource(R.drawable.ic_icon_search);
            } else if (selectedItem.type == SuggestionItem.Type.COMPLETION) {
                itemViewHolder.favicon.setImageResource(R.drawable.ic_icon_globe);
            } else if(selectedItem.type ==SuggestionItem.Type.HISTORY) {
                itemViewHolder.favicon.setImageResource(R.drawable.ic_icon_history);
            } else if (selectedItem.type == SuggestionItem.Type.BOOKMARK) {
                itemViewHolder.favicon.setImageResource(R.drawable.ic_icon_bookmark);
            }

            itemViewHolder.delete.setVisibility(GONE);
            itemViewHolder.favicon.setVisibility(VISIBLE);

            if (position == 0) {
                itemViewHolder.divider.setVisibility(VISIBLE);
            } else {
                itemViewHolder.divider.setVisibility(GONE);
            }

            return listItem;
        }

        OnClickListener mDeleteButtonListener = v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            int position = (Integer)v.getTag(R.string.position_tag);
            SuggestionItem item = getItem(position);
            mAdapter.remove(item);
            mAdapter.notifyDataSetChanged();

            if (mURLBarDelegate != null) {
                mURLBarDelegate.OnItemDeleted(item);
            }
        };

        OnClickListener mRowListener = v -> {
            if (mAudio != null) {
                mAudio.playSound(AudioEngine.Sound.CLICK);
            }

            hide(KEEP_WIDGET);

            requestFocus();
            requestFocusFromTouch();

            if (mURLBarDelegate != null) {
                int position = (Integer)v.getTag(R.string.position_tag);
                SuggestionItem item = getItem(position);
                mURLBarDelegate.OnItemClicked(item);
            }
        };

        private OnTouchListener mTouchListener = (view, event) -> {
            int position = (int)view.getTag(R.string.position_tag);
            if (!isEnabled(position)) {
                return false;
            }

            int ev = event.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_UP:
                    view.setPressed(false);
                    view.performClick();
                    return true;

                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    return true;
            }

            return false;
        };

        private OnHoverListener mHoverListener = (view, motionEvent) -> {
            int position = (int)view.getTag(R.string.position_tag);
            if (!isEnabled(position)) {
                return false;
            }

            View favicon = view.findViewById(R.id.favicon);
            TextView title = view.findViewById(R.id.title);
            View url = view.findViewById(R.id.url);
            View delete = view.findViewById(R.id.delete);
            int ev = motionEvent.getActionMasked();
            switch (ev) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    view.setHovered(true);
                    favicon.setHovered(true);
                    title.setHovered(true);
                    title.setShadowLayer(title.getShadowRadius(), title.getShadowDx(), title.getShadowDy(), getContext().getColor(R.color.text_shadow_light));
                    url.setHovered(true);
                    delete.setHovered(true);
                    return true;

                case MotionEvent.ACTION_HOVER_EXIT:
                    view.setHovered(false);
                    favicon.setHovered(false);
                    title.setHovered(false);
                    title.setShadowLayer(title.getShadowRadius(), title.getShadowDx(), title.getShadowDy(), getContext().getColor(R.color.text_shadow));
                    url.setHovered(false);
                    delete.setHovered(false);
                    return true;
            }

            return false;
        };
    }

    private SpannableStringBuilder createHighlightedText(@NonNull String text) {
        final SpannableStringBuilder sb = new SpannableStringBuilder(text);
        final StyleSpan bold = new StyleSpan(Typeface.BOLD);
        final StyleSpan normal = new StyleSpan(Typeface.NORMAL);
        int start = text.toLowerCase().indexOf(mHighlightedText.toLowerCase());
        if (start >= 0) {
            int end = start + mHighlightedText.length();
            sb.setSpan(normal, 0, start, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(bold, start, end, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(normal, end, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }

        return sb;
    }
}
