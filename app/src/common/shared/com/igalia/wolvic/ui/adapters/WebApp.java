package com.igalia.wolvic.ui.adapters;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.igalia.wolvic.utils.StringUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import mozilla.components.browser.icons.IconRequest;
import mozilla.components.concept.engine.manifest.WebAppManifest;
import mozilla.components.concept.engine.manifest.WebAppManifestParser;

/**
 * Parses and encapsulates the fields in a Web App manifest.
 * See https://www.w3.org/TR/appmanifest/ for reference.
 */
public class WebApp {
    @NonNull private String mIdentity;
    @NonNull private long mLastOpenTime;
    private WebAppManifest mManifest;
    private OptionalInt mHashCode = OptionalInt.empty();

    // TODO icons, shortcuts, languages

    /**
     * @throws java.io.IOException if there is an error parsing the manifest.
     */
    public WebApp(JSONObject manifest) throws IOException {
        WebAppManifestParser webAppManifestParser = new WebAppManifestParser();
        WebAppManifestParser.Result result = webAppManifestParser.parse(manifest);
        if (result instanceof WebAppManifestParser.Result.Success) {
            WebAppManifestParser.Result.Success successResult = (WebAppManifestParser.Result.Success) result;
            mManifest = successResult.getManifest();
        } else {
            String reason = "unknown reason";
            if (result instanceof WebAppManifestParser.Result.Failure) {
                WebAppManifestParser.Result.Failure failure = (WebAppManifestParser.Result.Failure) result;
                reason = failure.toString();
            }
            throw new IOException("Unable to parse the Web App manifest: " + reason);
        }

        // Algorithm for calculating Identity at https://www.w3.org/TR/appmanifest/#id-member
        if (!StringUtils.isEmpty(mManifest.getStartUrl())) {
            URL startUrl = new URL(mManifest.getStartUrl());
            String id = manifest.optString("id");
            URL identityUrl = new URL(startUrl.getProtocol(), startUrl.getHost(), startUrl.getPort(), id);
            mIdentity = identityUrl.toString();
            mLastOpenTime = 0;
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

    @NonNull
    public long getLastOpenTime() {
        return mLastOpenTime;
    }

    @NonNull
    public void setLastOpenTime() {
        mLastOpenTime = System.currentTimeMillis();
    }

    public String getName() {
        return mManifest.getName();
    }

    public String getShortName() {
        return mManifest.getShortName();
    }

    public String getScope() {
        return mManifest.getScope();
    }

    public String getStartUrl() {
        return mManifest.getStartUrl();
    }

    public int getThemeColor() {
        return mManifest.getThemeColor() != null ? mManifest.getThemeColor() : Color.TRANSPARENT;
    }

    public int getBackgroundColor() {
        return mManifest.getBackgroundColor() != null ? mManifest.getBackgroundColor() : Color.TRANSPARENT;
    }

    // Returns the list of icons in a format suitable to be used with BrowserIcons.
    public List<IconRequest.Resource> getIconResources() {
        return mManifest.getIcons().stream().map(icon ->
                new IconRequest.Resource(
                        icon.getSrc(),
                        IconRequest.Resource.Type.MANIFEST_ICON,
                        icon.getSizes(),
                        icon.getType(),
                        icon.getPurpose().contains(WebAppManifest.Icon.Purpose.MASKABLE) || icon.getPurpose().contains(WebAppManifest.Icon.Purpose.ANY)
                )
        ).collect(Collectors.toList());
    }

    public void copyFrom(WebApp webApp) {
        mIdentity = webApp.mIdentity;
        mLastOpenTime = webApp.mLastOpenTime;
        mManifest = webApp.mManifest;
        mHashCode = OptionalInt.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WebApp webApp = (WebApp) o;
        return mIdentity.equals(webApp.mIdentity) && mManifest.equals(webApp.mManifest);
    }

    @Override
    public int hashCode() {
        if (!mHashCode.isPresent()) {
            mHashCode = OptionalInt.of(
                    Objects.hash(mIdentity, mManifest));
        }
        return mHashCode.getAsInt();
    }

    @NonNull
    @Override
    public String toString() {
        return "WebApp{" +
                "mIdentity='" + mIdentity + '\'' +
                ", mLastOpenTime='" + mLastOpenTime + '\'' +
                ", mName='" + getName() + '\'' +
                ", mShortName='" + getShortName() + '\'' +
                ", mScope='" + getScope() + '\'' +
                ", mStartUrl='" + getStartUrl() + '\'' +
                ", mThemeColor='" + getThemeColor() + '\'' +
                ", mBackgroundColor='" + getBackgroundColor() + '\'' +
                '}';
    }
}
