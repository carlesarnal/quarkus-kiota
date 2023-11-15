package com.github.andreatp.quarkus.kiota;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ConfigRoot(name = KiotaCodeGenConfig.KIOTA_CONFIG_PREFIX, phase = ConfigPhase.BUILD_TIME)
public class KiotaCodeGenConfig {
    static final String KIOTA_CONFIG_PREFIX = "quarkus.kiota";
    // overwrite the automatically detected Operating System
    private static final String OS = KIOTA_CONFIG_PREFIX + ".os";
    // overwrite the automatically detected system architecture
    private static final String ARCH = KIOTA_CONFIG_PREFIX + ".arch";
    // Path to a kiota executable file to be used
    private static final String PROVIDED = KIOTA_CONFIG_PREFIX + ".provided";
    // Kiota release url
    private static final String DEFAULT_RELEASE_URL = "https://github.com/microsoft/kiota/releases";
    private static final String RELEASE_URL = KIOTA_CONFIG_PREFIX + ".release.url";
    // Kiota version, will try to resolve latest if not provided
    private static final String VERSION = KIOTA_CONFIG_PREFIX + ".version";
    // Timout, in seconds, used when executing the Kiota CLI
    private static final int DEFAULT_TIMEOUT = 5;
    private static final String TIMEOUT = KIOTA_CONFIG_PREFIX + ".timeout";

    // Kiota generate parameters
    private static final String DEFAULT_CLIENT_NAME = "ApiClient";
    private static final String CLIENT_CLASS_NAME = KIOTA_CONFIG_PREFIX + ".client-class-name";

    private static final String DEFAULT_CLIENT_PACKAGE = "io.apisdk";
    private static final String CLIENT_PACKAGE_NAME = KIOTA_CONFIG_PREFIX + ".client-package-name";
    private static final String INCLUDE_PATH = KIOTA_CONFIG_PREFIX + ".include-path";
    private static final String EXCLUDE_PATH = KIOTA_CONFIG_PREFIX + ".exclude-path";

    public static io.quarkus.utilities.OS getOs() {
        String os = System.getProperties().getProperty(OS);
        if (os == null) {
            return io.quarkus.utilities.OS.determineOS();
        }
        return io.quarkus.utilities.OS.valueOf(os);
    }

    public static String getArch() {
        String arch = System.getProperties().getProperty(ARCH);
        if (arch == null) {
            return io.quarkus.utilities.OS.getArchitecture();
        }
        return arch;
    }

    public static String getProvided() {
        return System.getProperties().getProperty(PROVIDED);
    }

    public static String getReleaseUrl() {
        String releaseUrl = System.getProperties().getProperty(RELEASE_URL);
        if (releaseUrl != null) {
            return releaseUrl;
        }
        return DEFAULT_RELEASE_URL;
    }

    public static String getVersion() {
        String version = System.getProperties().getProperty(VERSION);
        if (version == null) {
            // Dynamically retrieve latest for convenience
            Log.warn("No Kiota version specified, trying to retrieve it from the GitHub API");
            try {
                URI releaseURI = new URI(getReleaseUrl());
                URI latestVersionURI =
                        new URI(
                                releaseURI.getScheme(),
                                "api." + releaseURI.getHost(),
                                "/repos" + releaseURI.getPath() + "/latest",
                                releaseURI.getFragment());

                HttpRequest request = HttpRequest.newBuilder().uri(latestVersionURI).GET().build();

                HttpResponse<String> response =
                        HttpClient.newBuilder()
                                .build()
                                .send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    Log.warn(
                            "Failed to retrieve the latest Kiota version, please provide it"
                                    + " explicitly.");
                    return null;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode latestVersionJson = mapper.readTree(response.body());
                String latestVersion = latestVersionJson.get("name").asText();
                if (latestVersion.startsWith("v")) {
                    latestVersion = latestVersion.substring(1);
                }
                return latestVersion;
            } catch (URISyntaxException e) {
                Log.warn(
                        "Failed to retrieve the latest Kiota version, please provide it"
                                + " explicitly.",
                        e);
                return null;
            } catch (IOException e) {
                Log.warn(
                        "Failed to retrieve the latest Kiota version, please provide it"
                                + " explicitly.",
                        e);
                return null;
            } catch (InterruptedException e) {
                Log.warn(
                        "Failed to retrieve the latest Kiota version, please provide it"
                                + " explicitly.",
                        e);
                return null;
            }
        }
        return version;
    }

    public static String getClientClassName() {
        String clientName = System.getProperties().getProperty(CLIENT_CLASS_NAME);
        if (clientName != null) {
            return clientName;
        }
        return DEFAULT_CLIENT_NAME;
    }

    public static String getClientPackageName() {
        String packageName = System.getProperties().getProperty(CLIENT_PACKAGE_NAME);
        if (packageName != null) {
            return packageName;
        }
        return DEFAULT_CLIENT_PACKAGE;
    }

    public static String getIncludePath() {
        return System.getProperties().getProperty(INCLUDE_PATH);
    }

    public static String getExcludePath() {
        return System.getProperties().getProperty(EXCLUDE_PATH);
    }

    public static int getTimeout() {
        String timeout = System.getProperties().getProperty(TIMEOUT);
        if (timeout != null) {
            return Integer.valueOf(timeout);
        }
        return DEFAULT_TIMEOUT;
    }
}
