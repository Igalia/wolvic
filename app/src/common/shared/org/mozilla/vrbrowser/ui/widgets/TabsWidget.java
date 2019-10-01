package org.mozilla.vrbrowser.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.browser.SettingsStore;
import org.mozilla.vrbrowser.browser.engine.SessionStack;
import org.mozilla.vrbrowser.browser.engine.SessionStore;
import org.mozilla.vrbrowser.ui.views.UIButton;
import org.mozilla.vrbrowser.ui.views.UITextButton;
import org.mozilla.vrbrowser.ui.widgets.dialogs.UIDialog;

public class TabsWidget extends UIDialog {
    protected RecyclerView mTabsList;
    protected GridLayoutManager mLayoutManager;
    protected TabAdapter mAdapter;

    public TabsWidget(Context aContext, SessionStack aSessionStack) {
        super(aContext);
        initialize(aSessionStack);
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        Context context = getContext();
        aPlacement.width = WidgetPlacement.dpDimension(context, R.dimen.tabs_width);
        aPlacement.height = WidgetPlacement.dpDimension(context, R.dimen.tabs_height);
        aPlacement.worldWidth = WidgetPlacement.floatDimension(getContext(), R.dimen.window_world_width) * aPlacement.width/getWorldWidth();
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.floatDimension(context, R.dimen.context_menu_z_distance);
        aPlacement.visible = false;
    }

    private void initialize(SessionStack aSessionStack) {
        inflate(getContext(), R.layout.tabs, this);
        mTabsList = findViewById(R.id.tabsRecyclerView);
        mTabsList.setHasFixedSize(true);
        int spanCount = 4; // Columns
        int spacing = WidgetPlacement.pixelDimension(getContext(), R.dimen.tabs_spacing);
        mLayoutManager = new GridLayoutManager(getContext(), 4);
        mTabsList.setLayoutManager(mLayoutManager);
        mTabsList.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacing, false));

        // specify an adapter (see also next example)
        mAdapter = new TabAdapter(aSessionStack);
        mTabsList.setAdapter(mAdapter);


        UIButton backButton = findViewById(R.id.tabsBackButton);
        backButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            onDismiss();
        });

        UITextButton addNewTabButton = findViewById(R.id.tabsAddNewButton);
        addNewTabButton.setOnClickListener(view -> {
            view.requestFocusFromTouch();
            aSessionStack.newSessionWithUrl(SettingsStore.getInstance(getContext()).getHomepage());
            onDismiss();
        });
    }

    @Override
    public void show(int aShowFlags) {
        super.show(aShowFlags);
        mAdapter.notifyDataSetChanged();
    }

    public class TabAdapter extends RecyclerView.Adapter<TabAdapter.MyViewHolder> {
        private SessionStack mSessionStack;

        // Provide a reference to the views for each data item
        // Complex data items may need more than one view per item, and
        // you provide access to all the views for a data item in a view holder
        class MyViewHolder extends RecyclerView.ViewHolder {
            // each data item is just a string in this case
            CardView cardView;
            MyViewHolder(CardView v) {
                super(v);
                cardView = v;
            }

        }

        // Provide a suitable constructor (depends on the kind of dataset)
        TabAdapter(SessionStack aSessionStack) {
            mSessionStack = aSessionStack;
        }

        // Create new views (invoked by the layout manager)
        @Override
        public TabAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent,
                                                          int viewType) {
            // create a new view
            CardView cv = (CardView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.tab_view, parent, false);

            MyViewHolder vh = new MyViewHolder(cv);
            cv.setOnClickListener(v -> {
                SessionStack.Tab tab = mSessionStack.getTabAt(vh.getAdapterPosition());
                mSessionStack.setCurrentSession(tab.sessionId);
                TabsWidget.this.onDismiss();
            });
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            SessionStack.Tab tab = mSessionStack.getTabAt(position);
            ImageView image = holder.cardView.findViewById(R.id.tabPreview);
            TextView textView = holder.cardView.findViewById(R.id.tabUrl);
            textView.setText(mSessionStack.getUriFromSessionId(tab.sessionId));
            Bitmap bitmap = mSessionStack.getBitmapFromSessionId(tab.sessionId);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return mSessionStack.getTabCount();
        }
    }

    public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view); // item position
            int column = position % spanCount; // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount; // spacing - column * ((1f / spanCount) * spacing)
                outRect.right = (column + 1) * spacing / spanCount; // (column + 1) * ((1f / spanCount) * spacing)

                if (position < spanCount) { // top edge
                    outRect.top = spacing;
                }
                outRect.bottom = spacing; // item bottom
            } else {
                outRect.left = column * spacing / spanCount; // column * ((1f / spanCount) * spacing)
                outRect.right = spacing - (column + 1) * spacing / spanCount; // spacing - (column + 1) * ((1f /    spanCount) * spacing)
                if (position >= spanCount) {
                    outRect.top = spacing; // item top
                }
            }
        }
    }
}
