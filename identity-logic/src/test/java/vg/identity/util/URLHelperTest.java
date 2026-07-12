package vg.identity.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class URLHelperTest {

    @Test
    void addQueryParam_whenUrlHasQueryAndFragment_appendsEncodedParameterBeforeFragment() throws Exception {
        var source = new java.net.URL("https://example.com/path?existing=value#section");

        var result = URLHelper.addQueryParam(source, "start app", "id/value");

        assertThat(result).hasToString("https://example.com/path?existing=value&start+app=id%2Fvalue#section");
    }
}
