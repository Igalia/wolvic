package org.mozilla.vrbrowser.ui.adapters;

public class Language {

    public Language(String id, String name) {
        this.id = id;
        this.name = name;
    }

    private String name;
    private String id;

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
