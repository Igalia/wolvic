package org.mozilla.vrbrowser.addons.delegates;

import androidx.annotation.NonNull;

import mozilla.components.feature.addons.Addon;

public interface AddonsDelegate {

    void showAddonsList();
    void showAddonOptions(@NonNull Addon addon);
    void showAddonOptionsDetails(@NonNull Addon addon, int page);
    void showAddonOptionsPermissions(@NonNull Addon addon);
}
