package com.igalia.wolvic.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.igalia.wolvic.BuildConfig;
import com.igalia.wolvic.R;
import com.igalia.wolvic.browser.SettingsStore;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EnvironmentUtils {

    public static final String ENVS_FOLDER = "envs";
    public static final String BUILTIN_ENVS_PREFIX = "cubemap/";
    public static String[] SUPPORTED_ENV_EXTENSIONS = (DeviceType.isPicoXR()) ?
            new String[]{".jpg", ".png"} : new String[]{".ktx", ".jpg", ".png"};

    /**
     * Gets the ouput path for a given environment id.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property
     *              for external environments and the environment value list in option_value.xml for builtin ones.
     * @param versionName The target version name string.
     * @return The path to the env. It will be a relate path for builtin environments and a absolute path
     * for external environments.
     */
    @Nullable
    public static String getEnvPath(@NonNull Context context, @NonNull String envId, @NonNull String versionName) {
        if (EnvironmentUtils.isExternalEnvironment(context, envId, versionName)) {
            return EnvironmentUtils.getExternalEnvPath(context, envId);

        } else {
            return EnvironmentUtils.getBuiltinEnvPath(envId);
        }
    }


    /**
     * Gets the output path for a given environment id.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property
     *              for external environments and the environment value list in option_value.xml for builtin ones.
     * @return The path to the env. It will be a relate path for builtin environments and a absolute path
     * for external environments.
     */
    @Nullable
    public static String getEnvPath(@NonNull Context context, @NonNull String envId) {
        if (EnvironmentUtils.isExternalEnvironment(context, envId)) {
            return EnvironmentUtils.getExternalEnvPath(context, envId);

        } else {
            return EnvironmentUtils.getBuiltinEnvPath(envId);
        }
    }

    /**
     * Returns a path in the cache for the remote environments unzipping.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property.
     * @return The location of the environment in the devices memory.
     */
    @Nullable
    public static String getExternalEnvPath(@NonNull Context context, @NonNull String envId) {
        File outputFolder = new File(context.getCacheDir().getAbsolutePath(), ENVS_FOLDER + "/" + envId);
        if (outputFolder.exists())
            return outputFolder.getAbsolutePath();
        return outputFolder.mkdirs() ? outputFolder.getAbsolutePath() : null;
    }

    /**
     * Returns a path for a builtin environment.
     * @param envId The environment id. This maps to the environment value list in option_value.xml.
     * @return The relative path to the builtin environment
     */
    @NonNull
    public static String getBuiltinEnvPath(@NonNull String envId) {
        return BUILTIN_ENVS_PREFIX + envId;
    }

    /**
     * Check whether or not an external environment is ready to be used. Checks if the directory exists
     * and if it contains at least 6 supported files. We make an assumption that those files are the
     * right images and that they follow the naming convention.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property.
     * @return true is the environment is ready, false otherwise
     */
    public static boolean isExternalEnvReady(@NonNull Context context, @NonNull String envId) {
        String envOutputPath = getExternalEnvPath(context, envId);
        if (envOutputPath != null) {
            File envDirectory = new File(envOutputPath);
            if (envDirectory.exists() && envDirectory.isDirectory()) {
                File[] envFiles = envDirectory.listFiles(file -> {
                    String fileName = file.getName().toLowerCase();
                    return file.isFile() && Arrays.stream(SUPPORTED_ENV_EXTENSIONS).anyMatch(fileName::endsWith);
                });
                return envFiles != null && envFiles.length >= 6;
            }
        }
        return false;
    }

    /**
     * Check if the environment is builtin.
     * @param context An activity context.
     * @param envId The environment id. This maps to the environment value list in option_value.xml for builtin ones.
     * @return true if the environment is builtin, false otherwise.
     */
    public static boolean isBuiltinEnvironment(@NonNull Context context, @NonNull String envId) {
        String[] items = context.getResources().getStringArray(R.array.developer_options_environments_values);
        return Arrays.stream(items).anyMatch(item -> item.equals(envId));
    }

    /**
     * Check if the environment is external.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property
     *              for external environments and the environment value list in option_value.xml for builtin ones.
     * @return true if the environment is external, false otherwise.
     */
    public static boolean isExternalEnvironment(@NonNull Context context, @NonNull String envId) {
        return getExternalEnvironmentById(context, envId) != null;
    }

    /**
     * Check if the environment is external.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property
     *              for external environments and the environment value list in option_value.xml for builtin ones.
     * @param versionName The target version name string.
     * @return true if the environment is external, false otherwise.
     */
    public static boolean isExternalEnvironment(@NonNull Context context, @NonNull String envId, @NonNull String versionName) {
        return getExternalEnvironmentById(context, envId, versionName) != null;
    }

    /**
     * Get the environments defined in the remote JSON properties file. It returns the environments
     * for the current version if it exists and it has environments, otherwise it returns environments
     * for the most recent previous version.
     * @param context An activity context.
     * @return The Remote environments list or null if they couldn't be found.
     */
    public static Environment[] getExternalEnvironments(@NonNull Context context) {
        return getExternalEnvironments(context, BuildConfig.VERSION_NAME);
    }

    /**
     * Get the environments defined in the remote JSON properties file. It returns the environments
     * for the current version if it exists and it has environments, otherwise it returns environments
     * for the most recent previous version.
     * @param context An activity context.
     * @param versionName The target version name string.
     * @return The Remote environments list or null if they couldn't be found.
     */
    public static Environment[] getExternalEnvironments(@NonNull Context context, @NonNull String versionName) {
        Map<String, RemoteProperties> properties = SettingsStore.getInstance(context).getRemoteProperties();
        if (properties != null) {
            // If there are environments for the current version we return those,
            // otherwise return the ones from the most recent version
            if (properties.containsKey(versionName)) {
                RemoteProperties versionProperties = properties.get(versionName);
                if (versionProperties != null && versionProperties.getEnvironments() != null) {
                    return versionProperties.getEnvironments();
                }
            }

            Set<String> keys = properties.keySet();
            List<String> keysList = keys.stream()
                    .sorted((o1, o2) -> o2.compareTo(o1))
                    .collect(Collectors.toList());
            for (String key : keysList) {
                RemoteProperties props = properties.get(key);
                if (props != null && props.getEnvironments() != null) {
                    return props.getEnvironments();
                }
            }
        }

        return null;
    }

    /**
     * Retuns the external environment based for a given environment id. It returns the environment
     * for the current version if the environment exists, otherwise it returns the environment with that id
     * for the most recent previous version.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property.
     * @return The Remote environments list or null if they couldn't be found.
     */
    @Nullable
    public static Environment getExternalEnvironmentById(@NonNull Context context, @NonNull String envId) {
        return getExternalEnvironmentById(context, envId, BuildConfig.VERSION_NAME);
    }

    /**
     * Returns the URL to the environment's payload, with the '_alt' suffix for the devices requiring an
     * alternative compressed texture format (only JPG and PNG are allowed as alternative to KTX).
     * @param env An Environment data structure
     * @return The appropriated URL to the environment's payload .
     */
    @Nullable
    public static String getEnvironmentPayload(Environment env) {
        // Pico4x and Meta Quest (after v69) do not support compressed textures for the cubemap.
        if (DeviceType.isPicoXR() || DeviceType.isOculusBuild()) {
            String payload = env.getPayload();
            String format = "_misc";
            int at = payload.lastIndexOf(".");
            return payload.substring(0, at) + format + "_srgb" + payload.substring(at);
        }
        return env.getPayload(); // default is 'rgb' and 'ktx'
    }

    /**
     * Retuns the external environment based for a given environment id. It returns the environment
     * for the current version if the environment exists, otherwise it returns the environment with that id
     * for the most recent previous version.
     * @param context An activity context.
     * @param envId The environment id. This maps to the Remote properties JSON "value" environment property.
     * @param versionName The target version name string.
     * @return The Remote environments list or null if they couldn't be found.
     */
    @Nullable
    public static Environment getExternalEnvironmentById(@NonNull Context context, @NonNull String envId, @NonNull String versionName) {
        Map<String, RemoteProperties> properties = SettingsStore.getInstance(context).getRemoteProperties();
        if (properties != null) {
            // If there are environments for the current version we return those,
            // otherwise return the ones from the most recent version
            if (properties.containsKey(versionName)) {
                RemoteProperties versionProperties = properties.get(versionName);
                if (versionProperties != null && versionProperties.getEnvironments() != null) {
                    return Arrays.stream(versionProperties.getEnvironments())
                            .filter(environment -> envId.equals(environment.getValue()))
                            .findFirst()
                            .orElse(null);
                }
            }

            Set<String> keys = properties.keySet();
            List<String> keysList = keys.stream()
                    .sorted((o1, o2) -> o2.compareTo(o1))
                    .collect(Collectors.toList());
            for (String key : keysList) {
                RemoteProperties props = properties.get(key);
                if (props != null && props.getEnvironments() != null) {
                    return Arrays.stream(props.getEnvironments())
                            .filter(environment -> envId.equals(environment.getValue()))
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        return null;
    }

    /**
     * Retuns the external environment based for a given environment payload url. It returns the environment
     * for the current version if the environment exists, otherwise it returns the environment with that payload url
     * for the most recent previous version.
     * @param context An activity context.
     * @param payloadUrl The payload url for the environment. This maps to the Remote properties JSON "payload" environment property.
     * @return The Remote environments list or null if they couldn't be found.
     */
    @Nullable
    public static Environment getExternalEnvironmentByPayload(@NonNull Context context, @NonNull String payloadUrl) {
        return getExternalEnvironmentByPayload(context, payloadUrl, BuildConfig.VERSION_NAME);
    }

    /**
     * Retuns the external environment based for a given environment payload url. It returns the environment
     * for the current version if the environment exists, otherwise it returns the environment with that payload url
     * for the most recent previous version.
     * @param context An activity context.
     * @param payloadUrl The payload url for the environment. This maps to the Remote properties JSON "payload" environment property.
     * @param versionName The target version name string.
     * @return The Remote environments list or null if they couldn't be found.
     */
    @Nullable
    public static Environment getExternalEnvironmentByPayload(@NonNull Context context, @NonNull String payloadUrl, @NonNull String versionName) {
        Map<String, RemoteProperties> properties = SettingsStore.getInstance(context).getRemoteProperties();
        if (properties != null) {
            // If there are environments for the current version we return those,
            // otherwise return the ones from the most recent version
            if (properties.containsKey(versionName)) {
                RemoteProperties versionProperties = properties.get(versionName);
                if (versionProperties != null && versionProperties.getEnvironments() != null) {
                    return Arrays.stream(versionProperties.getEnvironments())
                            .filter(environment -> payloadUrl.equals(getEnvironmentPayload(environment)))
                            .findFirst()
                            .orElse(null);
                }
            }

            Set<String> keys = properties.keySet();
            List<String> keysList = keys.stream()
                    .sorted((o1, o2) -> o2.compareTo(o1))
                    .collect(Collectors.toList());
            for (String key : keysList) {
                RemoteProperties props = properties.get(key);
                if (props != null && props.getEnvironments() != null) {
                    return Arrays.stream(props.getEnvironments())
                            .filter(environment -> payloadUrl.equals(getEnvironmentPayload(environment)))
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        return null;
    }
}
