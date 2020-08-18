package org.mozilla.vrbrowser.addons.views;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.VRBrowserActivity;
import org.mozilla.vrbrowser.databinding.AddonOptionsDetailsBinding;
import org.mozilla.vrbrowser.ui.widgets.WidgetManagerDelegate;
import org.mozilla.vrbrowser.utils.SystemUtils;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Collectors;

import mozilla.components.feature.addons.Addon;
import mozilla.components.feature.addons.ui.ExtensionsKt;

public class AddonOptionsDetailsView extends RecyclerView.ViewHolder {

    private static final String LOGTAG = SystemUtils.createLogtag(AddonOptionsDetailsView.class);

    private static NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
    private static SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
    private static DateFormat dateFormatter = DateFormat.getDateInstance();

    private Context mContext;
    private AddonOptionsDetailsBinding mBinding;
    private WidgetManagerDelegate mWidgetManager;

    public AddonOptionsDetailsView(@NonNull Context context, @NonNull AddonOptionsDetailsBinding binding) {
        super(binding.getRoot());

        mContext = context;
        mBinding = binding;
        mWidgetManager = ((VRBrowserActivity)context);

        mBinding.setLifecycleOwner((VRBrowserActivity) mContext);
    }

    public void bind(Addon addon) {
        // Update addon
        if (addon != null) {
            // If the addon is not installed we set the homepage link
            mBinding.homepage.setOnClickListener(view -> mWidgetManager.openNewTabForeground(addon.getSiteUrl()));

            bindTranslatedDescription(mBinding.addonDescription, addon);
            bindAuthors(mBinding.authorsText, addon);
            bindRatingUsersCount(mBinding.ratingText, addon);
            bindRatingBar(mBinding.rating, addon);
            bindLastUpdated(mBinding.lastUpdatedText, addon);
            bindVersion(mBinding.versionText, addon);
        }
    }

    private void bindTranslatedDescription(@NonNull TextView view, Addon addon) {
        String detailsText = view.getContext().getString(R.string.addons_no_description);
        if (addon != null) {
            detailsText = ExtensionsKt.getTranslatedDescription(addon);
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
                view.setOnClickListener(view1 -> mWidgetManager.openNewTabForeground(link.getURL()));
            }
        };
        spannableStringBuilder.setSpan(clickable, start, end, flags);
        spannableStringBuilder.removeSpan(link);
    }

    private void bindAuthors(@NonNull TextView view, Addon addon) {
        String text = view.getContext().getString(R.string.addons_no_authors);
        if (addon != null) {
            String authors = addon.getAuthors().stream()
                    .map(Addon.Author::getName)
                    .collect(Collectors.joining( "," ));
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
                view.setOnClickListener(view1 -> {
                    // Show Updater status
                });

            } else {
                view.setOnLongClickListener(null);
            }
        }
    }

}
