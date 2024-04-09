package com.igalia.wolvic.browser.api;

import androidx.annotation.Nullable;

import com.igalia.wolvic.browser.api.impl.SettingsImpl;

import org.chromium.content_public.browser.WebContents;

public interface WSessionSettings {

    static WSessionSettings create(boolean usePrivateMode) {
        return new SettingsImpl(usePrivateMode);
    }

    int DISPLAY_MODE_BROWSER = 0;
    int DISPLAY_MODE_MINIMAL_UI = 1;
    int DISPLAY_MODE_STANDALONE = 2;
    int DISPLAY_MODE_FULLSCREEN = 3;
    int USER_AGENT_MODE_MOBILE = 0;
    int USER_AGENT_MODE_DESKTOP = 1;
    int USER_AGENT_MODE_VR = 2;

    // This needs to match BrowserSettingsChild.js
    /**
     * Mobile-friendly pages will be rendered using a viewport based on their &lt;meta&gt; viewport
     * tag. All other pages will be rendered using a special desktop mode viewport, which has a width
     * of 980 CSS px.
     */
    public static final int VIEWPORT_MODE_MOBILE = 0;

    /**
     * All pages will be rendered using the special desktop mode viewport, which has a width of 980
     * CSS px, regardless of whether the page has a &lt;meta&gt; viewport tag specified or not.
     */
    public static final int VIEWPORT_MODE_DESKTOP = 1;

    /**
     * Set whether tracking protection should be enabled.
     *
     * @param value A flag determining whether tracking protection should be enabled. Default is
     *     false.
     */
    void setUseTrackingProtection(final boolean value);

    /**
     * Set whether to suspend the playing of media when the session is inactive.
     *
     * @param value A flag determining whether media should be suspended. Default is false.
     */
    void setSuspendMediaWhenInactive(final boolean value);

    /**
     * Set whether JavaScript support should be enabled.
     *
     * @param value A flag determining whether JavaScript should be enabled. Default is true.
     */
    void setAllowJavascript(final boolean value);

    /**
     * Set whether the entire accessible tree should be exposed with no caching.
     *
     * @param value A flag determining full accessibility tree should be exposed. Default is false.
     */
    void setFullAccessibilityTree(final boolean value);


    /**
     * Whether tracking protection is enabled.
     *
     * @return true if tracking protection is enabled, false if not.
     */
    boolean getUseTrackingProtection();

    /**
     * Whether private mode is enabled.
     *
     * @return true if private mode is enabled, false if not.
     */
    boolean getUsePrivateMode();

    /**
     * The context ID for this session.
     *
     * @return The context ID for this session.
     */
    @Nullable String getContextId();

    /**
     * Whether media will be suspended when the session is inactice.
     *
     * @return true if media will be suspended, false if not.
     */
    boolean getSuspendMediaWhenInactive();

    /**
     * Whether javascript execution is allowed.
     *
     * @return true if javascript execution is allowed, false if not.
     */
    boolean getAllowJavascript();

    /**
     * Whether entire accessible tree is exposed with no caching.
     *
     * @return true if accessibility tree is exposed, false if not.
     */
    boolean getFullAccessibilityTree();


    /**
     * Specify which user agent mode we should use
     *
     * @param value One or more of the {@link WSessionSettings#USER_AGENT_MODE_MOBILE
     *     ISessionSettings.USER_AGENT_MODE_*} flags.
     */
    void setUserAgentMode(final int value);

    /**
     * Set the display mode.
     *
     * @param value The mode to set the display to. Use one or more of the {@link
     *     WSessionSettings#DISPLAY_MODE_BROWSER ISessionSettings.DISPLAY_MODE_*} flags.
     */
    void setDisplayMode(final int value);

    /**
     * Specify which viewport mode we should use
     *
     * @param value One or more of the {@link WSessionSettings#VIEWPORT_MODE_MOBILE
     *     ISessionSettings.VIEWPORT_MODE_*} flags.
     */
    void setViewportMode(final int value);

    /**
     * The current user agent Mode
     *
     * @return One or more of the {@link WSessionSettings#USER_AGENT_MODE_MOBILE
     *     ISessionSettings.USER_AGENT_MODE_*} flags.
     */
    int getUserAgentMode();

    /**
     * The current display mode.
     *
     * @return )One or more of the {@link WSessionSettings#DISPLAY_MODE_BROWSER
     *     ISessionSettings.DISPLAY_MODE_*} flags.
     */
    int getDisplayMode();

    /**
     * The current viewport Mode
     *
     * @return One or more of the ISessionSettings.VIEWPORT_MODE_*} flags.
     */
    int getViewportMode();


    /**
     * Specify the user agent override string. Set value to null to use the user agent specified by
     * USER_AGENT_MODE.
     *
     * @param value The string to override the user agent with.
     */
    void setUserAgentOverride(final @Nullable String value);

    /**
     * The user agent override string.
     *
     * @return The current user agent string or null if the agent is specified by ISessionSettings#USER_AGENT_MODE}
     */
    @Nullable String getUserAgentOverride();

    void setWebContents(final @Nullable WebContents webContents);
}
