package org.knime.hub.client.sdk;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;

/**
 * Filter for g-zip encoded responses. The response will get de-compressed and the encoding header will be removed.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public class GZipResponseFilter implements ClientResponseFilter {

    private static final String GZIP_ENCODING = "gzip";

    @Override
    public void filter(final ClientRequestContext requestContext, final ClientResponseContext responseContext)
            throws IOException {
        final var encodingHeaderValue = responseContext.getHeaderString(HttpHeaders.CONTENT_ENCODING);
        if (GZIP_ENCODING.equalsIgnoreCase(encodingHeaderValue)) {
            final var entityStream = responseContext.getEntityStream();
            responseContext.setEntityStream(new GZIPInputStream(entityStream));
            responseContext.getHeaders().remove(HttpHeaders.CONTENT_ENCODING); // Prevent double decoding
        }
    }

}
