package org.mozilla.vrbrowser.ui.widgets.menus;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.ui.widgets.UIWidget;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.ui.widgets.WidgetPlacement;
import org.mozilla.vrbrowser.utils.ViewUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public class VideoProjectionMenuWidget extends MenuWidget implements WidgetManagerDelegate.FocusChangeListener {

    @IntDef(value = { VIDEO_PROJECTION_NONE, VIDEO_PROJECTION_3D_SIDE_BY_SIDE, VIDEO_PROJECTION_360,
                      VIDEO_PROJECTION_360_STEREO, VIDEO_PROJECTION_180,
                      VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT, VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM })
    public @interface VideoProjectionFlags {}

    public static final int VIDEO_PROJECTION_NONE = -1;
    public static final int VIDEO_PROJECTION_3D_SIDE_BY_SIDE = 0;
    public static final int VIDEO_PROJECTION_360 = 1;
    public static final int VIDEO_PROJECTION_360_STEREO = 2;
    public static final int VIDEO_PROJECTION_180 = 3;
    public static final int VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT = 4;
    public static final int VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM = 5;

    public interface Delegate {
        void onVideoProjectionClick(@VideoProjectionFlags int aProjection);
    }

    class ProjectionMenuItem extends MenuItem {
        @VideoProjectionFlags int projection;
        public ProjectionMenuItem(@VideoProjectionFlags int aProjection, String aString, int aImage) {
            super(aString, aImage, () -> handleClick(aProjection));
            projection = aProjection;
        }
    }

    ArrayList<MenuItem> mItems;
    Delegate mDelegate;
    @VideoProjectionFlags int mSelectedProjection = VIDEO_PROJECTION_3D_SIDE_BY_SIDE;

    public VideoProjectionMenuWidget(Context aContext) {
        super(aContext, R.layout.menu);
        createMenuItems();
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.video_projection_menu_width);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 1.0f;
        aPlacement.anchorX = 0.0f;
        aPlacement.anchorY = 0.0f;
        aPlacement.translationY = WidgetPlacement.dpDimension(getContext(), R.dimen.video_projection_menu_translation_y);
        aPlacement.translationZ = 2.0f;
    }

    @Override
    public void show(@ShowFlags int aShowFlags) {
        super.show(aShowFlags);

        mWidgetManager.addFocusChangeListener(VideoProjectionMenuWidget.this);
    }

    @Override
    public void hide(@HideFlags int aHideFlags) {
        super.hide(aHideFlags);

        mWidgetManager.removeFocusChangeListener(this);
    }

    public void setParentWidget(UIWidget aParent) {
        mWidgetPlacement.parentHandle = aParent.getHandle();
    }

    public void setDelegate(@Nullable Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    private void createMenuItems() {
        mItems = new ArrayList<>();

        mItems.add(new ProjectionMenuItem(VIDEO_PROJECTION_3D_SIDE_BY_SIDE, getContext().getString(R.string.video_mode_3d_side),
                R.drawable.ic_icon_videoplayback_3dsidebyside));

        mItems.add(new ProjectionMenuItem(VIDEO_PROJECTION_360, getContext().getString(R.string.video_mode_360),
                R.drawable.ic_icon_videoplayback_360));

        mItems.add(new ProjectionMenuItem(VIDEO_PROJECTION_360_STEREO, getContext().getString(R.string.video_mode_360_stereo),
                R.drawable.ic_icon_videoplayback_360_stereo));

        mItems.add(new ProjectionMenuItem(VIDEO_PROJECTION_180, getContext().getString(R.string.video_mode_180),
                R.drawable.ic_icon_videoplayback_180));

        mItems.add(new ProjectionMenuItem(VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT, getContext().getString(R.string.video_mode_180_left_right),
                R.drawable.ic_icon_videoplayback_180_stereo_leftright));

        mItems.add(new ProjectionMenuItem(VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM, getContext().getString(R.string.video_mode_180_top_bottom),
                R.drawable.ic_icon_videoplayback_180_stereo_topbottom));


        super.updateMenuItems(mItems);

        mWidgetPlacement.height = mItems.size() * WidgetPlacement.dpDimension(getContext(), R.dimen.menu_item_height);
        mWidgetPlacement.height += mBorderWidth * 2;
    }

    private void handleClick(@VideoProjectionFlags int aVideoProjection) {
        mSelectedProjection = aVideoProjection;
        if (mDelegate != null) {
            mDelegate.onVideoProjectionClick(aVideoProjection);
        }
    }

    public @VideoProjectionFlags int getSelectedProjection() {
        return mSelectedProjection;
    }

    public void setSelectedProjection(@VideoProjectionFlags int aProjection) {
        mSelectedProjection = aProjection;
        int index = IntStream.range(0, mItems.size())
                .filter(i -> ((ProjectionMenuItem)mItems.get(i)).projection == aProjection)
                .findFirst()
                .orElse(-1);
        setSelectedItem(index);
    }

    public static @VideoProjectionFlags int getAutomaticProjection(String aURL, AtomicBoolean autoEnter) {
        if (aURL == null) {
            return VIDEO_PROJECTION_NONE;
        }

        Uri uri = Uri.parse(aURL);
        if (uri == null) {
            return VIDEO_PROJECTION_NONE;
        }

        String projection = uri.getQueryParameter("mozVideoProjection");
        if (projection == null) {
            projection = uri.getQueryParameter("mozvideoprojection");
            if (projection == null) {
                return VIDEO_PROJECTION_NONE;
            }
        }
        projection = projection.toLowerCase();

        autoEnter.set(projection.endsWith("_auto"));

        if (projection.startsWith("360s")) {
            return VIDEO_PROJECTION_360_STEREO;
        } else if (projection.startsWith("360")) {
            return VIDEO_PROJECTION_360;
        } else if (projection.startsWith("180lr")) {
            return VIDEO_PROJECTION_180_STEREO_LEFT_RIGHT;
        } else if (projection.startsWith("180tb")) {
            return VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM;
        } else if (projection.startsWith("180")) {
            return VIDEO_PROJECTION_180;
        } else if (projection.startsWith("3d")) {
            return VIDEO_PROJECTION_3D_SIDE_BY_SIDE;
        }

        return VIDEO_PROJECTION_NONE;
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (!ViewUtils.isEqualOrChildrenOf(this, newFocus) && isVisible()) {
            onDismiss();
        }
    }

}
