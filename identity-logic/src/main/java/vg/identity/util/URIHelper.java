package vg.identity.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class URIHelper {

    private URIHelper() {
    }

    public static URI addQueryParam(URI src, String paramName, String paramValue) {
        var externalUrl = src.toString();
        var fragmentIndex = externalUrl.indexOf('#');
        var baseUrl = fragmentIndex >= 0 ? externalUrl.substring(0, fragmentIndex) : externalUrl;
        var fragment = fragmentIndex >= 0 ? externalUrl.substring(fragmentIndex) : "";
        var separator = baseUrl.contains("?") ? "&" : "?";
        return URI.create(baseUrl + separator + encode(paramName) + "=" + encode(paramValue) + fragment);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
