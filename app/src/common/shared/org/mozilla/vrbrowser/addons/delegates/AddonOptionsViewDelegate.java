package org.mozilla.vrbrowser.addons.delegates;

import android.view.View;

import androidx.annotation.NonNull;

import mozilla.components.feature.addons.Addon;

public interface AddonOptionsViewDelegate {

    default void onAddonSettingsButtonClicked(@NonNull View view, @NonNull Addon addon) {}
    default void onAddonDetailsButtonClicked(@NonNull View view, @NonNull Addon addon) {}
    default void onAddonPermissionsButtonClicked(@NonNull View view, @NonNull Addon addon) {}
    default void onRemoveAddonButtonClicked(@NonNull View view, @NonNull Addon addon) {}

}
