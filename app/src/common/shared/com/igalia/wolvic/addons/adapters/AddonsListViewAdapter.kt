/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.igalia.wolvic.addons.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.igalia.wolvic.R
import com.igalia.wolvic.utils.LocaleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.amo.AMOAddonsProvider
import mozilla.components.feature.addons.ui.AddonsManagerAdapterDelegate
import mozilla.components.feature.addons.ui.CustomViewHolder
import mozilla.components.feature.addons.ui.CustomViewHolder.AddonViewHolder
import mozilla.components.feature.addons.ui.CustomViewHolder.SectionViewHolder
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.res.resolveAttribute
import java.io.IOException
import java.text.NumberFormat
import java.util.*
import kotlin.collections.ArrayList

private const val VIEW_HOLDER_TYPE_SECTION = 0
private const val VIEW_HOLDER_TYPE_ADDON = 1

/**
 * An adapter for displaying add-on items. This will display information related to the state of
 * an add-on such as recommended or installed. In addition, it will perform actions
 * such as installing an add-on.
 *
 * @property addonCollectionProvider Provider of AMO collection API.
 * @property addonsManagerDelegate Delegate that will provides method for handling the add-on items.
 * @param addons The list of add-on based on the AMO store.
 * @property style Indicates how items should look like.
 */
@Suppress("TooManyFunctions", "LargeClass")
class AddonsManagerAdapter(
        private val addonCollectionProvider: AMOAddonsProvider,
        private val addonsManagerDelegate: AddonsManagerAdapterDelegate,
        addons: List<Addon>,
        private val style: Style? = null
) : ListAdapter<Any, CustomViewHolder>(DifferCallback) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logger = Logger("AddonsManagerAdapter")
    /**
     * Represents all the add-ons that will be distributed in multiple headers like
     * enabled, recommended, this help have the data source of the items,
     * displayed in the UI.
     */
    @VisibleForTesting
    internal var addonsMap: MutableMap<String, Addon> = addons.associateBy({ it.id }, { it }).toMutableMap()

    init {
        submitList(createListWithSections(addons))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        return when (viewType) {
            VIEW_HOLDER_TYPE_ADDON -> createAddonViewHolder(parent)
            VIEW_HOLDER_TYPE_SECTION -> createSectionViewHolder(parent)
            else -> throw IllegalArgumentException("Unrecognized viewType")
        }
    }

    private fun createSectionViewHolder(parent: ViewGroup): CustomViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.addons_section_item, parent, false)
        val titleView = view.findViewById<TextView>(R.id.title)
        val divider = view.findViewById<View>(R.id.divider)
        return SectionViewHolder(view, titleView, divider)
    }

    private fun createAddonViewHolder(parent: ViewGroup): AddonViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.addons_item, parent, false)
        val iconView = view.findViewById<ImageView>(R.id.add_on_icon)
        val titleView = view.findViewById<TextView>(R.id.add_on_name)
        val summaryView = view.findViewById<TextView>(R.id.add_on_description)
        val ratingView = view.findViewById<RatingBar>(R.id.rating)
        val ratingAccessibleView = view.findViewById<TextView>(R.id.rating_accessibility)
        val userCountView = view.findViewById<TextView>(R.id.users_count)
        val addButton = view.findViewById<ImageView>(R.id.add_button)
        val allowedInPrivateBrowsingLabel = view.findViewById<ImageView>(R.id.allowed_in_private_browsing_label)
        val statusErrorView = view.findViewById<TextView>(R.id.add_on_status_error_message)
        return AddonViewHolder(
            view,
            iconView,
            titleView,
            summaryView,
            ratingView,
            ratingAccessibleView,
            userCountView,
            addButton,
            allowedInPrivateBrowsingLabel,
            statusErrorView
        )
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Addon -> VIEW_HOLDER_TYPE_ADDON
            is Section -> VIEW_HOLDER_TYPE_SECTION
            else -> throw IllegalArgumentException("items[position] has unrecognized type")
        }
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is SectionViewHolder -> bindSection(holder, item as Section)
            is AddonViewHolder -> bindAddon(holder, item as Addon)
            else -> {}
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bindSection(holder: SectionViewHolder, section: Section) {
        holder.titleView.setText(section.title)
        style?.maybeSetSectionsTextColor(holder.titleView)
        style?.maybeSetSectionsTypeFace(holder.titleView)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun bindAddon(holder: AddonViewHolder, addon: Addon) {
        val context = holder.itemView.context
        if (addon.rating != null) {
            val userCount = context.getString(R.string.mozac_feature_addons_user_rating_count_2)
            val ratingContentDescription =
                String.format(
                    context.getString(R.string.mozac_feature_addons_rating_content_description),
                    addon.rating!!.average
                )
            holder.ratingView.contentDescription = ratingContentDescription
            // Android RatingBar is not very accessibility-friendly, we will use non visible TextView
            // for contentDescription for the TalkBack feature
            holder.ratingAccessibleView.text = ratingContentDescription
            holder.ratingView.rating = addon.rating!!.average

            holder.ratingView.visibility = View.VISIBLE
            holder.ratingAccessibleView.visibility = View.VISIBLE
        } else {
            holder.ratingView.visibility = View.GONE
            holder.ratingAccessibleView.visibility = View.GONE
        }

        val displayLanguage = LocaleUtils.getDisplayLanguage(context).locale.language

        holder.titleView.text =
            if (addon.translatableName.isNotEmpty()) {
                when {
                    addon.translatableName.containsKey(displayLanguage) ->
                        addon.translatableName[displayLanguage]
                    addon.translatableName.containsKey(addon.defaultLocale) ->
                        addon.translatableName[addon.defaultLocale]
                    addon.translatableName.containsKey(Addon.DEFAULT_LOCALE) ->
                        addon.translatableName[Addon.DEFAULT_LOCALE]
                    else -> addon.id
                }
            } else {
                addon.id
            }

        if (addon.translatableSummary.isNotEmpty()) {
            holder.summaryView.text =
                when {
                    addon.translatableSummary.containsKey(displayLanguage) ->
                        addon.translatableSummary[displayLanguage]
                    addon.translatableSummary.containsKey(addon.defaultLocale) ->
                        addon.translatableSummary[addon.defaultLocale]
                    addon.translatableSummary.containsKey(Addon.DEFAULT_LOCALE) ->
                        addon.translatableSummary[Addon.DEFAULT_LOCALE]
                    else -> ""
                }
            holder.summaryView.visibility = View.VISIBLE
        } else {
            holder.summaryView.visibility = View.GONE
        }

        holder.itemView.tag = addon
        holder.itemView.setOnClickListener {
            addonsManagerDelegate.onAddonItemClicked(addon)
        }

        holder.addButton.visibility = if (!addon.isInstalled()) View.VISIBLE else View.GONE
        holder.addButton.setOnClickListener {
            if (!addon.isInstalled()) {
                addonsManagerDelegate.onInstallAddonButtonClicked(addon)
            }
        }

        holder.allowedInPrivateBrowsingLabel.visibility = if (addon.isAllowedInPrivateBrowsing()) View.VISIBLE else View.GONE
        style?.maybeSetPrivateBrowsingLabelDrawale(holder.allowedInPrivateBrowsingLabel)

        style?.addonBackgroundIconColor?.let {
            val backgroundColor = ContextCompat.getColor(holder.iconView.context, it)
            holder.iconView.setBackgroundColor(backgroundColor)
        }
        fetchIcon(addon, holder.iconView)
        style?.maybeSetAddonNameTextColor(holder.titleView)
        style?.maybeSetAddonSummaryTextColor(holder.summaryView)
    }

    @Suppress("MagicNumber")
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun fetchIcon(addon: Addon, iconView: ImageView, scope: CoroutineScope = this.scope): Job {
        return scope.launch {
            var iconDrawable = iconView.context.getDrawable(R.drawable.ic_icon_addons)
            try {
                // We calculate how much time takes to fetch an icon,
                // if takes less than a second, we assume it comes
                // from a cache and we don't show any transition animation.
                val startTime = System.currentTimeMillis()
                val iconBitmap = addon.icon?: addon.installedState?.icon
                val timeToFetch: Double = (System.currentTimeMillis() - startTime) / 1000.0
                val isFromCache = timeToFetch < 1
                if (iconBitmap != null) iconDrawable = BitmapDrawable(iconView.resources, iconBitmap)
                scope.launch(Main) {
                    if (isFromCache) {
                        iconView.setImageDrawable(iconDrawable)
                    } else {
                        setWithCrossFadeAnimation(iconView, iconDrawable!!)
                    }
                }
            } catch (e: IOException) {
                scope.launch(Main) {
                    val context = iconView.context
                    val att = context.theme.resolveAttribute(android.R.attr.textColorPrimary)
                    iconView.setColorFilter(ContextCompat.getColor(context, att))
                    iconView.setImageDrawable(iconDrawable)
                }
                logger.error("Attempt to fetch the ${addon.id} icon failed", e)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Suppress("ComplexMethod")
    internal fun createListWithSections(addons: List<Addon>): List<Any> {
        val itemsWithSections = ArrayList<Any>()
        val installedAddons = ArrayList<Addon>()
        val recommendedAddons = ArrayList<Addon>()
        val disabledAddons = ArrayList<Addon>()
        val experimentalAddons = ArrayList<Addon>()

        addons.forEach { addon ->
            when {
                addon.inRecommendedSection() -> recommendedAddons.add(addon)
                addon.inInstalledSection() -> installedAddons.add(addon)
                addon.inDisabledSection() -> disabledAddons.add(addon)
                addon.inExperimentalSection() -> experimentalAddons.add(addon)
            }
        }

        // Add installed section and addons if available
        if (installedAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_enabled))
            itemsWithSections.addAll(installedAddons)
        }

        // Add disabled section and addons if available
        if (disabledAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_disabled_section))
            itemsWithSections.addAll(disabledAddons)
        }

        // Add recommended section and addons if available
        if (recommendedAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.mozac_feature_addons_recommended_section))
            itemsWithSections.addAll(recommendedAddons)
        }

        // Add experimental section and addons if available
        if (experimentalAddons.isNotEmpty()) {
            itemsWithSections.add(Section(R.string.addons_experimental_section_title))
            itemsWithSections.addAll(experimentalAddons)
        }

        return itemsWithSections
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal data class Section(@StringRes val title: Int)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal data class NotYetSupportedSection(@StringRes val title: Int)

    /**
     * Allows to customize how items should look like.
     */
    data class Style(
        @ColorRes
        val sectionsTextColor: Int? = null,
        @ColorRes
        val addonNameTextColor: Int? = null,
        @ColorRes
        val addonSummaryTextColor: Int? = null,
        val sectionsTypeFace: Typeface? = null,
        @ColorRes
        val addonBackgroundIconColor: Int? = null,
        @DrawableRes
        val addonAllowPrivateBrowsingLabelDrawableRes: Int? = null
    ) {
        internal fun maybeSetSectionsTextColor(textView: TextView) {
            sectionsTextColor?.let {
                val color = ContextCompat.getColor(textView.context, it)
                textView.setTextColor(color)
            }
        }

        internal fun maybeSetSectionsTypeFace(textView: TextView) {
            sectionsTypeFace?.let {
                textView.typeface = it
            }
        }

        internal fun maybeSetAddonNameTextColor(textView: TextView) {
            addonNameTextColor?.let {
                val color = ContextCompat.getColor(textView.context, it)
                textView.setTextColor(color)
            }
        }

        internal fun maybeSetAddonSummaryTextColor(textView: TextView) {
            addonSummaryTextColor?.let {
                val color = ContextCompat.getColor(textView.context, it)
                textView.setTextColor(color)
            }
        }

        internal fun maybeSetPrivateBrowsingLabelDrawale(imageView: ImageView) {
            addonAllowPrivateBrowsingLabelDrawableRes?.let {
                imageView.setImageDrawable(ContextCompat.getDrawable(imageView.context, it))
            }
        }
    }

    /**
     * Update the portion of the list that contains the provided [addon].
     * @property addon The add-on to be updated.
     */
    fun updateAddon(addon: Addon) {
        addonsMap[addon.id] = addon
        submitList(createListWithSections(addonsMap.values.toList()))
    }

    /**
     * Updates only the portion of the list that changes between the current list and the new provided [addons].
     * Be aware that updating a subset of the visible list is not supported, [addons] will replace
     * the current list, but only the add-ons that have been changed will be updated in the UI.
     * If you provide a subset it will replace the current list.
     * @property addons A list of add-on to replace the actual list.
     */
    fun updateAddons(addons: List<Addon>) {
        addonsMap = addons.associateBy({ it.id }, { it }).toMutableMap()
        submitList(createListWithSections(addons))
    }

    internal object DifferCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Addon && newItem is Addon -> oldItem.id == newItem.id
                oldItem is Section && newItem is Section -> oldItem.title == newItem.title
                oldItem is NotYetSupportedSection && newItem is NotYetSupportedSection -> oldItem.title == newItem.title
                else -> false
            }
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }

    internal fun setWithCrossFadeAnimation(image: ImageView, iconDrawable: Drawable, durationMillis: Int = 1700) {
        with(image) {
            val animation = TransitionDrawable(arrayOf(drawable, iconDrawable))
            animation.isCrossFadeEnabled = true
            setImageDrawable(animation)
            animation.startTransition(durationMillis)
        }
    }
}

private fun Addon.inRecommendedSection() = !isInstalled()
private fun Addon.inInstalledSection() = isInstalled() && isSupported() && isEnabled()
private fun Addon.inDisabledSection() = isInstalled() && isSupported() && !isEnabled()
private fun Addon.inExperimentalSection() = isInstalled() && !isSupported()

/**
 * Get the formatted number amount for the current default locale.
 */
internal fun getFormattedAmount(amount: Int): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).format(amount)
}
