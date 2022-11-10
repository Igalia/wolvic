package com.igalia.wolvic.ui.adapters;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.igalia.wolvic.utils.StringUtils;
import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Parses and encapsulates (a subset of) the fields in a Web App manifest.
 * See https://www.w3.org/TR/appmanifest/ for reference.
 */
public class WebApp {

    protected final String LOGTAG = SystemUtils.createLogtag(this.getClass());

    @NonNull
    private String mIdentity;
    @NonNull
    private String mName;
    @NonNull
    private String mShortName;
    @NonNull
    private String mScope;
    @NonNull
    private String mStartUrl;

    private int mThemeColor;
    private int mBackgroundColor;

    private OptionalInt mHashCode = OptionalInt.empty();

    // TODO icons, shortcuts, languages

    /**
     * @throws java.io.IOException if there is an error parsing the manifest.
     */
    public WebApp(JSONObject manifest) throws IOException {
        mStartUrl = manifest.optString("start_url");
        mName = manifest.optString("name");
        mShortName = manifest.optString("short_name");
        mScope = manifest.optString("scope");

        int themeColor = Color.TRANSPARENT;
        try {
            String themeColorString = manifest.optString("theme_color");
            themeColor = Color.parseColor(themeColorString);
        } catch (IllegalArgumentException e) {
            Log.w(LOGTAG, "Invalid theme_color value");
        }
        mThemeColor = themeColor;

        int backgroundColor = Color.TRANSPARENT;
        try {
            String backgroundColorString = manifest.optString("background_color");
            backgroundColor = Color.parseColor(backgroundColorString);
        } catch (IllegalArgumentException e) {
            Log.w(LOGTAG, "Invalid background_color value");
        }
        mBackgroundColor = backgroundColor;

        // Algorithm for calculating Identity at https://www.w3.org/TR/appmanifest/#id-member
        if (!StringUtils.isEmpty(mStartUrl)) {
            URL startUrl = new URL(mStartUrl);
            String id = manifest.optString("id");
            URL identityUrl = new URL(startUrl.getProtocol(), startUrl.getHost(), startUrl.getPort(), id);
            mIdentity = identityUrl.toString();
        } else {
            // Since Identity is used to uniquely identify the Web application,
            // we treat its absence as an error.
            throw new IOException("Unable to assign an identity to the Web App manifest");
        }
    }

    @NonNull
    public String getId() {
        return mIdentity;
    }

    public String getName() {
        return mName;
    }

    public String getShortName() {
        return mShortName;
    }

    public String getScope() {
        return mScope;
    }

    public String getStartUrl() {
        return mStartUrl;
    }

    public int getThemeColor() {
        return mThemeColor;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public void copyFrom(WebApp webApp) {
        mIdentity = webApp.mIdentity;
        mName = webApp.mName;
        mShortName = webApp.mShortName;
        mScope = webApp.mScope;
        mStartUrl = webApp.mStartUrl;
        mThemeColor = webApp.mThemeColor;
        mBackgroundColor = webApp.mBackgroundColor;

        mHashCode = OptionalInt.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WebApp webApp = (WebApp) o;
        return mIdentity.equals(webApp.mIdentity) &&
                mName.equals(webApp.mName) &&
                mShortName.equals(webApp.mShortName) &&
                mScope.equals(webApp.mScope) &&
                mStartUrl.equals(webApp.mStartUrl) &&
                mThemeColor == webApp.mThemeColor &&
                mBackgroundColor == webApp.mBackgroundColor;
    }

    @Override
    public int hashCode() {
        if (!mHashCode.isPresent()) {
            mHashCode = OptionalInt.of(
                    Objects.hash(mIdentity, mName, mShortName, mScope, mStartUrl, mThemeColor, mBackgroundColor));
        }
        return mHashCode.getAsInt();
    }

    @NonNull
    @Override
    public String toString() {
        return "WebApp{" +
                "mIdentity='" + mIdentity + '\'' +
                ", mName='" + mName + '\'' +
                ", mShortName='" + mShortName + '\'' +
                ", mScope='" + mScope + '\'' +
                ", mStartUrl='" + mStartUrl + '\'' +
                ", mThemeColor='" + mThemeColor + '\'' +
                ", mBackgroundColor='" + mBackgroundColor + '\'' +
                '}';
    }
}
