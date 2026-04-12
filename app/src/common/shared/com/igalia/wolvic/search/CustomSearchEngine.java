package com.igalia.wolvic.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public class CustomSearchEngine {

    public static final String ID_PREFIX = "custom_";

    private final String id;
    private final String name;
    private final String searchUrl;
    @Nullable
    private final String suggestUrl;

    public CustomSearchEngine(@NonNull String id, @NonNull String name, @NonNull String searchUrl, @Nullable String suggestUrl) {
        this.id = id;
        this.name = name;
        this.searchUrl = searchUrl;
        this.suggestUrl = suggestUrl;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getSearchUrl() {
        return searchUrl;
    }

    @Nullable
    public String getSuggestUrl() {
        return suggestUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomSearchEngine other = (CustomSearchEngine) o;
        return Objects.equals(id, other.id) &&
                Objects.equals(name, other.name) &&
                Objects.equals(searchUrl, other.searchUrl) &&
                Objects.equals(suggestUrl, other.suggestUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, searchUrl, suggestUrl);
    }

    @NonNull
    @Override
    public String toString() {
        return "CustomSearchEngine{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", searchUrl='" + searchUrl + '\'' +
                ", suggestUrl='" + suggestUrl + '\'' +
                '}';
    }

    public static class Builder {
        private String id;
        private String name;
        private String searchUrl;
        private String suggestUrl;

        public Builder setId(@NonNull String id) {
            this.id = id;
            return this;
        }

        public Builder setName(@NonNull String name) {
            this.name = name;
            return this;
        }

        public Builder setSearchUrl(@NonNull String searchUrl) {
            this.searchUrl = searchUrl;
            return this;
        }

        public Builder setSuggestUrl(@Nullable String suggestUrl) {
            this.suggestUrl = suggestUrl;
            return this;
        }

        public CustomSearchEngine build() {
            if (id == null || name == null || searchUrl == null) {
                throw new IllegalStateException("id, name, and searchUrl are required");
            }
            return new CustomSearchEngine(id, name, searchUrl, suggestUrl);
        }
    }
}