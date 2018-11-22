package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;

import org.mozilla.vrbrowser.R;

import java.util.ArrayList;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

public class VideoProjectionMenuWidget extends MenuWidget {

    @IntDef(value = { VIDEO_PROJECTION_3D_SIDE_BY_SIDE, VIDEO_PROJECTION_360,
                      VIDEO_PROJECTION_360_STEREO, VIDEO_PROJECTION_180,
                      VIDEO_PROJECTION_180_STEREO_LEFT_RIGTH, VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM })
    public @interface VideoProjectionFlags {}

    public static final int VIDEO_PROJECTION_3D_SIDE_BY_SIDE = 0;
    public static final int VIDEO_PROJECTION_360 = 1;
    public static final int VIDEO_PROJECTION_360_STEREO = 2;
    public static final int VIDEO_PROJECTION_180 = 3;
    public static final int VIDEO_PROJECTION_180_STEREO_LEFT_RIGTH = 4;
    public static final int VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM = 5;

    public interface Delegate {
        void onVideoProjectionClick(@VideoProjectionFlags int aProjection);
    }

    ArrayList<MenuItem> mItems;
    Delegate mDelegate;
    @VideoProjectionFlags int mSelectedProjection = VIDEO_PROJECTION_3D_SIDE_BY_SIDE;

    public VideoProjectionMenuWidget(Context aContext) {
        super(aContext);
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
        aPlacement.translationZ = 1.0f;
    }

    public void setParentWidget(UIWidget aParent) {
        mWidgetPlacement.parentHandle = aParent.getHandle();
    }

    public void setDelegate(@Nullable Delegate aDelegate) {
        mDelegate = aDelegate;
    }

    private void createMenuItems() {
        mItems = new ArrayList<>();

        mItems.add(new MenuItem(R.string.video_mode_3d_side, R.drawable.ic_icon_videoplayback_3dsidebyside, new Runnable() {
            @Override
            public void run() {
                handleClick(VIDEO_PROJECTION_3D_SIDE_BY_SIDE);
            }
        }));

        mItems.add(new MenuItem(R.string.video_mode_360, R.drawable.ic_icon_videoplayback_360, new Runnable() {
            @Override
            public void run() {
                handleClick(VIDEO_PROJECTION_360);
            }
        }));

        mItems.add(new MenuItem(R.string.video_mode_360_stereo, R.drawable.ic_icon_videoplayback_360_stereo, new Runnable() {
            @Override
            public void run() {
                handleClick(VIDEO_PROJECTION_360_STEREO);
            }
        }));

        mItems.add(new MenuItem(R.string.video_mode_180, R.drawable.ic_icon_videoplayback_180, new Runnable() {
            @Override
            public void run() {
                handleClick(VIDEO_PROJECTION_180);
            }
        }));

        mItems.add(new MenuItem(R.string.video_mode_180_left_right, R.drawable.ic_icon_videoplayback_180_stereo_leftright, new Runnable() {
            @Override
            public void run() {
                handleClick(VIDEO_PROJECTION_180_STEREO_LEFT_RIGTH);
            }
        }));

        mItems.add(new MenuItem(R.string.video_mode_180_top_bottom, R.drawable.ic_icon_videoplayback_180_stereo_topbottom, new Runnable() {
            @Override
            public void run() {
                handleClick(VIDEO_PROJECTION_180_STEREO_TOP_BOTTOM);
            }
        }));


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
}
