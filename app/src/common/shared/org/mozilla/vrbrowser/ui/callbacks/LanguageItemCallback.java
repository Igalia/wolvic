package org.mozilla.vrbrowser.ui.callbacks;

import android.view.View;

import org.mozilla.vrbrowser.ui.adapters.Language;

public interface LanguageItemCallback {
    void onAdd(View view, Language language);
    void onRemove(View view, Language language);
    void onMoveUp(View view, Language language);
    void onMoveDown(View view, Language language);
}
