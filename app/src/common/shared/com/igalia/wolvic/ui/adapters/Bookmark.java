package com.igalia.wolvic.ui.adapters;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import mozilla.components.concept.storage.BookmarkNode;
import mozilla.components.concept.storage.BookmarkNodeType;

public class Bookmark {

    public enum Type {
        ITEM,
        FOLDER,
        SEPARATOR
    }

    private boolean mIsExpanded;
    private int mLevel;
    private @NonNull String mTitle;
    private @NonNull String mURL;
    private @NonNull String mGuid;
    private int mPosition;
    private Type mType;
    private boolean mHasChildren;

    public Bookmark(@NonNull BookmarkNode node, int level, boolean isExpanded) {
        mIsExpanded = isExpanded;
        mLevel = level;

        mTitle = node.getTitle() != null ? node.getTitle() : "";
        mURL = node.getUrl() != null ? node.getUrl() : "";
        mGuid = node.getGuid() != null ? node.getGuid() : "";
        // TODO: We shall get the position using `node.getPosition();` instead of 0.
        // However, position is now kotlin.UInt which is not supported by Java.
        mPosition = 0;
        mHasChildren = node.getChildren() != null;

        switch (node.getType()) {
            case SEPARATOR:
                mType = Type.SEPARATOR;
                break;
            case FOLDER:
                mType = Type.FOLDER;
                break;
            case ITEM:
                mType = Type.ITEM;
                break;
        }
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public Type getType() {
        return mType;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getUrl() {
        return mURL;
    }

    public String getGuid() {
        return mGuid;
    }

    public int getLevel() {
        return mLevel;
    }

    public void setLevel(int level) {
        mLevel = level;
    }

    // TODO: This method is broken because upstream now uses kotlin.UInt for mPosition.
    public int getPosition() {
        return mPosition;
    }

    public boolean hasChildren() {
        return mHasChildren;
    }

    static List<Bookmark> getRootDisplayListTree(@NonNull List<BookmarkNode> bookmarkNodes) {
        return getDisplayListTree(bookmarkNodes, 0, null);
    }

    static List<Bookmark> getDisplayListTree(@NonNull List<BookmarkNode> bookmarkNodes, List<String> openFolderGuid) {
        return getDisplayListTree(bookmarkNodes, 0, openFolderGuid);
    }

    /**
     * Returns a display tree for the current open folders
     * @param bookmarkNodes The bookmark nodes tree
     * @param level The hierarchy level to process
     * @param openFolderGuid The list of currently opened folders
     * @return A display list with all the visible bookmarks
     */
    private static List<Bookmark> getDisplayListTree(@NonNull List<BookmarkNode> bookmarkNodes, int level, List<String> openFolderGuid) {
        ArrayList<Bookmark> children = new ArrayList<>();
        for (BookmarkNode node : bookmarkNodes) {
            if (node.getType() == BookmarkNodeType.FOLDER) {
                if (openFolderGuid != null && openFolderGuid.contains(node.getGuid())) {
                    boolean canExpand = node.getChildren() != null && !node.getChildren().isEmpty();
                    Bookmark bookmark = new Bookmark(node, level, canExpand);
                    children.add(bookmark);
                    if (node.getChildren() != null) {
                        children.addAll(getDisplayListTree(node.getChildren(), level + 1, openFolderGuid));
                    }

                } else {
                    Bookmark bookmark = new Bookmark(node, level, false);
                    children.add(bookmark);
                }

            } else if (node.getTitle() != null &&
                    !node.getUrl().startsWith("place:") &&
                    !node.getUrl().startsWith("about:reader")){
                // Exclude "place" and "about:reader" items as we don't support them right now
                Bookmark bookmark = new Bookmark(node, level, false);
                children.add(bookmark);
            }
        }

        return children;
    }

    /**
     * Traverses the current display list looking for opened folders
     * @param displayList
     * @return A list with the currently opened folder guids
     */
    static List<String> getOpenFoldersGuid(@NonNull List<Bookmark> displayList) {
        ArrayList<String> result = new ArrayList<>();

        for (Bookmark item : displayList) {
            if (item.isExpanded()) {
                result.add(item.getGuid());
            }
        }

        return result;
    }

}
