package com.igalia.wolvic.browser;

import android.app.Activity;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.igalia.wolvic.utils.SystemUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class UriOverride {
    private final static String LOGTAG = SystemUtils.createLogtag(UriOverride.class);
    private static final String NO_OVERRIDE_FOUND = "NO OVERRIDE FOUND";
    private ArrayMap<String, String> mOverrideMap;
    private ArrayMap<String, String> mOverrideCache;
    private String mOverrideName;
    public UriOverride(String name) {
        mOverrideMap = new ArrayMap<>();
        mOverrideCache = new ArrayMap<>();
        mOverrideName = name;
    }

    public void loadOverridesFromAssets(Activity aActivity, String aFileName) {
        String json = null;
        try (InputStream is = aActivity.getAssets().open(aFileName)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            importJSONData(json);
        } catch (IOException e) {
            Log.e(LOGTAG, "Failed reading " + mOverrideName + " override file: " + aFileName + " Error: " + e.getMessage());
        }
    }

    public String lookupOverride(final String aUri) {
        if (aUri == null) {
            return null;
        }
        Log.d(LOGTAG, "lookupOverride for: " + aUri);
        URI uri;
        try {
            uri = new URI(aUri);
        } catch (URISyntaxException e) {
            Log.d(LOGTAG, "Error parsing URL: " + aUri + " " + e.getMessage());
            return null;
        }
        String fullDomain = uri.getHost();
        if (fullDomain == null) {
            return null;
        }

        fullDomain = fullDomain.toLowerCase();

        String override = mOverrideCache.get(fullDomain);

        if (override != null) {
            Log.d(LOGTAG, "Found override: " + override);
            return override.equals(NO_OVERRIDE_FOUND) ? null : override;
        }

        List<String> domains = Arrays.asList(fullDomain.split("\\."));
        final int domainCount = domains.size();
        String[] checkedDomains = new String[domainCount];

        for (int ix = 0; ix < domainCount; ix++) {
            String domain = TextUtils.join(".", domains.subList(ix, domainCount));
            checkedDomains[ix] = domain;
            override = mOverrideCache.get(domain);
            if (override != null) {
                Log.d(LOGTAG, "found cached override: " + override);
                addToCache(checkedDomains, override);
                return override.equals(NO_OVERRIDE_FOUND) ? null : override;
            }
            String domainHash = hashDomain(domain);
            if (domainHash == null) {
                Log.d(LOGTAG, "Failed to hash domain: " + domain);
                return null;
            }
            Log.d(LOGTAG, "hash: " + domainHash + " for domain: " + domain);
            override = mOverrideMap.get(domainHash);
            if (override != null) {
                Log.d(LOGTAG, "found override from hash: " + override);
                addToCache(checkedDomains, override);
                return override;
            }
        }
        addToCache(checkedDomains, NO_OVERRIDE_FOUND);
        return null;
    }

    private void addToCache(String[] aDomains, String aOverride) {
        for (String domain: aDomains) {
            if (domain == null) {
                Log.d(LOGTAG, "Found null domain in checked list");
                continue;
            } else {
                Log.d(LOGTAG, domain + " override: " + aOverride);
            }
            mOverrideCache.put(domain, aOverride);
        }
    }

    private String hashDomain(String aDomain) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(aDomain.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int value: digest) {
                sb.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(LOGTAG, "Error while trying to hash domain: " + e.getMessage());
        }
        return null;
    }

    private void importJSONData(final String aData) {
        try {
            JSONObject json = new JSONObject(aData);
            Iterator<String> iter = json.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    String value = json.getString(key);
                    Log.d(LOGTAG, mOverrideName + " override: " + key + " -> " + value);
                    mOverrideMap.put(key, value);
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Failed to find " + mOverrideName + " override while parsing file for key: " + key);
                }
            }

        } catch (JSONException e) {
            Log.e(LOGTAG, "Failed to import " + mOverrideName + " override JSON data: " + e.getMessage());
        }
    }
}
