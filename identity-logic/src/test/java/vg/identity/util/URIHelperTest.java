package vg.identity.util;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class URIHelperTest {

    @Test
    void addQueryParam_whenUrlHasQueryAndFragment_appendsEncodedParameterBeforeFragment() {
        var source = URI.create("https://example.com/path?existing=value#section");

        var result = URIHelper.addQueryParam(source, "start app", "id/value");

        assertThat(result).hasToString("https://example.com/path?existing=value&start+app=id%2Fvalue#section");
    }
}
