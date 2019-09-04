package org.mozilla.vrbrowser.ui.adapters;

public class Language {

    public Language(String id, String name) {
        this.id = id;
        this.name = name;
        this.isPreferred = false;
    }

    private String name;
    private String id;
    private boolean isPreferred;

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setPreferred(boolean isPreferred) {
        this.isPreferred = isPreferred;
    }

    public boolean isPreferred() {
        return isPreferred;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
