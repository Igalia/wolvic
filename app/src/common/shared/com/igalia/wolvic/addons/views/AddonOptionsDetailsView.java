package com.igalia.wolvic.addons.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.igalia.wolvic.R;
import com.igalia.wolvic.VRBrowserActivity;
import com.igalia.wolvic.VRBrowserApplication;
import com.igalia.wolvic.addons.delegates.AddonsDelegate;
import com.igalia.wolvic.browser.Addons;
import com.igalia.wolvic.databinding.AddonOptionsDetailsBinding;
import com.igalia.wolvic.ui.widgets.WidgetManagerDelegate;
import com.igalia.wolvic.utils.SystemUtils;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonOptionsDetailsView extends RecyclerView.ViewHolder implements Addons.AddonsListener {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonOptionsDetailsView.class);

    private static NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
    private static SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
    private static DateFormat dateFormatter = DateFormat.getDateInstance();

    private Context mContext;
    private AddonOptionsDetailsBinding mBinding;
    private WidgetManagerDelegate mWidgetManager;
    private AddonsDelegate mDelegate;
    private Executor mUIThreadExecutor;

    @SuppressLint("ClickableViewAccessibility")
    public AddonOptionsDetailsView(@NonNull Context context, @NonNull AddonOptionsDetailsBinding binding, @NonNull AddonsDelegate delegate) {
        super(binding.getRoot());

        mContext = context;
        mBinding = binding;
        mDelegate = delegate;
        mWidgetManager = ((VRBrowserActivity)context);
        mUIThreadExecutor = ((VRBrowserApplication)context.getApplicationContext()).getExecutors().mainThread();

        mBinding.setLifecycleOwner((VRBrowserActivity) mContext);

        mBinding.scrollview.setOnTouchListener((v, event) -> {
            v.requestFocusFromTouch();
            return false;
        });
    }

    public void bind(Addon addon) {
        mBinding.setAddon(addon);

        mWidgetManager.getServicesProvider().getAddons().addListener(this);

        // Update addon
        if (addon != null) {
            // If the addon is not installed we set the homepage link
            mBinding.homepage.setOnClickListener(view -> {
                view.requestFocusFromTouch();
                mWidgetManager.openNewTabForeground(mBinding.getAddon().getHomepageUrl());
            });

            bindTranslatedDescription(mBinding.addonDescription, addon);
            bindAuthors(mBinding.authorsText, addon);
            bindRatingUsersCount(mBinding.ratingText, addon);
            bindRatingBar(mBinding.rating, addon);
            bindLastUpdated(mBinding.lastUpdatedText, addon);
            bindVersion(mBinding.versionText, addon);
        }
    }

    public void unbind() {
        mWidgetManager.getServicesProvider().getAddons().removeListener(this);
    }

    private void bindTranslatedDescription(@NonNull TextView view, Addon addon) {
        String detailsText = view.getContext().getString(R.string.addons_no_description);
        if (addon != null) {
            detailsText = ExtensionsKt.translateDescription(addon, mContext);
        }
        String parsedText = detailsText.replace("\n", "<br/>");
        Spanned text = HtmlCompat.fromHtml(parsedText, HtmlCompat.FROM_HTML_MODE_COMPACT);

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
        URLSpan[] links = spannableStringBuilder.getSpans(0, parsedText.length(), URLSpan.class);
        for (URLSpan link : links) {
            addActionToLinks(spannableStringBuilder, link);
        }
        view.setText(spannableStringBuilder);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void addActionToLinks(
            @NonNull SpannableStringBuilder spannableStringBuilder,
            @NonNull URLSpan link
    ) {
        int start = spannableStringBuilder.getSpanStart(link);
        int end = spannableStringBuilder.getSpanEnd(link);
        int flags = spannableStringBuilder.getSpanFlags(link);
        ClickableSpan clickable = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                view.requestFocusFromTouch();
                view.setOnClickListener(view1 -> mWidgetManager.openNewTabForeground(link.getURL()));
            }
        };
        spannableStringBuilder.setSpan(clickable, start, end, flags);
        spannableStringBuilder.removeSpan(link);
    }

    private void bindAuthors(@NonNull TextView view, Addon addon) {
        String text = view.getContext().getString(R.string.addons_no_authors);
        if (addon != null) {
            String authors = addon.getAuthor().getName();
            if (!authors.isEmpty()) {
                text = authors;
            }
        }
        view.setText(text);
    }

    private void bindRatingUsersCount(@NonNull TextView view, Addon addon) {
        if (addon != null && addon.getRating() != null) {
            view.setText(numberFormat.format(addon.getRating().getReviews()));
        }
    }

    private void bindRatingBar(@NonNull RatingBar view, Addon addon) {
        if (addon != null && addon.getRating() != null) {
            view.setRating(addon.getRating().getAverage());
            view.setContentDescription(String.format(view.getResources().getString(R.string.mozac_feature_addons_rating_content_description),
                    addon.getRating().getAverage()));
        }
    }

    private void bindLastUpdated(@NonNull TextView view, Addon addon) {
        if (addon != null) {
            try {
                Date parsed = dateParser.parse(addon.getUpdatedAt());
                if (parsed != null) {
                    view.setText(dateFormatter.format(parsed));
                }

            } catch (ParseException e) {
                view.setText("?");
            }
        }
    }

    private void bindVersion(@NonNull TextView view, Addon addon) {
        if (addon != null) {
            String version = addon.getVersion();
            if (addon.getInstalledState() != null) {
                version = addon.getInstalledState().getVersion();
            }
            view.setText(version);

            if (addon.isInstalled()) {
                // Show Updater status
                view.setOnClickListener(View::requestFocusFromTouch);

            } else {
                view.setOnLongClickListener(null);
            }
        }
    }

    @Override
    public void onAddonsUpdated() {
        mWidgetManager.getServicesProvider().getAddons().getAddons(true).thenAcceptAsync(addons -> {
            Addon addon = addons.stream()
                    .filter(item -> item.getId().equals(mBinding.getAddon().getId()))
                    .findFirst().orElse(null);

            if (addon == null || !addon.isInstalled()) {
                mDelegate.showAddonsList();

            } else {
                bind(addon);
            }

        }, mUIThreadExecutor).exceptionally(throwable -> {
            Log.d(LOGTAG, String.valueOf(throwable.getMessage()));
            return null;
        });
    }

}
