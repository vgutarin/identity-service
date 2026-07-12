package vg.identity.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class URLHelper {

    private URLHelper() {
    }

    public static URL addQueryParam(URL src, String paramName, String paramValue) {
        var externalUrl = src.toExternalForm();
        var fragmentIndex = externalUrl.indexOf('#');
        var baseUrl = fragmentIndex >= 0 ? externalUrl.substring(0, fragmentIndex) : externalUrl;
        var fragment = fragmentIndex >= 0 ? externalUrl.substring(fragmentIndex) : "";
        var separator = baseUrl.contains("?") ? "&" : "?";
        return toUrl(baseUrl + separator + encode(paramName) + "=" + encode(paramValue) + fragment);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static URL toUrl(String url) {
        try {
            return URI.create(url).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }
    }
}
