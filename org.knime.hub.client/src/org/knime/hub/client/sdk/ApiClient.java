/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * -------------------------------------------------------------------
 *
 * History
 *   Nov 6, 2024 (magnus): created
 */

package org.knime.hub.client.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.KNIMEServerHostnameVerifier;
import org.knime.core.util.auth.Authenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.proxy.URLConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status.Family;

/**
 * API client used to make REST requests.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public class ApiClient implements AutoCloseable {

    private static final String HTTP_CONNECTION_TIMEOUT_PROP = "http.connection.timeout";
    private static final String HTTP_RECEIVE_TIMEOUT_PROP = "http.receive.timeout";
    private static final String HTTP_AUTOREDIRECT_PROP = "http.autoredirect";

    /**
     * HTTP request method
     */
    public enum Method {
        /** GET request method */
        GET,

        /** POST request method */
        POST,

        /** PUT request method */
        PUT,

        /** HEAD request method */
        HEAD,

        /** DELETE request method */
        DELETE,

        /** PATCH request method */
        PATCH
    }

    private final URI m_baseURI;
    private @Owning Client m_httpClient;

    private final ObjectMapper m_objectMapper;
    private final Duration m_connectionTimeout;
    private final Duration m_readTimeout;

    private final Authenticator m_auth;

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiClient.class);

    /**
     * Builds the {@link ApiClient} with the given base URL of the server
     * and specified connection and read timeouts.
     *
     * @param baseURI base URL of the server.
     * @param auth the {@link Authenticator}
     * @param connectionTimeout the time (in ms) it takes until the connection gets a timeout
     * @param readTimeout the time (in ms) it takes until the read process gets a timeout
     */
    public ApiClient(final URI baseURI, final Authenticator auth,
            final Duration connectionTimeout, final Duration readTimeout) {
        // Set base path
        m_baseURI = baseURI;

        // Set authentication
        m_auth = auth;

        // Configure the object mapper for the JSON provider
        m_objectMapper = new ObjectMapper();
        m_objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        m_objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        m_objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        m_objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m_objectMapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        m_objectMapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        m_objectMapper.registerModule(new JavaTimeModule());
        m_objectMapper.registerModule(new Jdk8Module()); // Needed for Optional support.

        // Set timeouts
        m_connectionTimeout = connectionTimeout;
        m_readTimeout = readTimeout;

        // Create a http client builder and register the defined JSON provider
        final var clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.hostnameVerifier(KNIMEServerHostnameVerifier.getInstance());

        // Register a GZIP response filter for compressed responses
        clientBuilder.register(GZipResponseFilter.class);

        // Add object mapper for deserialization
        final var jsonProvider = new JacksonJsonProvider(m_objectMapper);
        clientBuilder.register(jsonProvider);

        // Enable automatic redirects (e.g. for download)
        clientBuilder.property(HTTP_AUTOREDIRECT_PROP, true);

        // Set timeouts for connection
        clientBuilder.property(HTTP_CONNECTION_TIMEOUT_PROP, m_connectionTimeout.toMillis());
        clientBuilder.property(HTTP_RECEIVE_TIMEOUT_PROP, m_readTimeout.toMillis());

        m_httpClient = clientBuilder.build();
    }

    /**
     * Creates and retrieves a new {@link ApiRequest}.
     *
     * @return {@link ApiRequest}
     */
    public ApiRequest createApiRequest() {
        return new ApiRequest();
    }

    /**
     * Request builder for the API client
     *
     * @author Magnus Gohm, KNIME AG, Konstanz, Germany
     */
    public final class ApiRequest {

        private final Map<String, String> m_headerParams = new HashMap<>();
        private final Map<String, String> m_queryParams = new HashMap<>();
        private final Map<String, String> m_cookieParams = new HashMap<>();
        private static final MediaType DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_JSON_TYPE;
        private MediaType m_contentType = DEFAULT_CONTENT_TYPE;
        private String m_headerAccept;

        private Duration m_requestReadTimeout;

        private ApiRequest() {
        }

        /**
         * Adds a header parameter to the request.
         *
         * @param key the name of the header parameter
         * @param value the value of the header parameter
         * @return {@link ApiRequest}
         */
        public ApiRequest withHeader(final String key, final String value) {
            if (value != null) {
                m_headerParams.put(key, value);
            }
            return this;
        }

        /**
         * Adds header parameters to the request.
         *
         * @param headerMap the map of headers
         * @return {@link ApiRequest}
         */
        public ApiRequest withHeaders(final Map<String, String> headerMap) {
            if (headerMap != null) {
                m_headerParams.putAll(headerMap);
            }
            return this;
        }

        /**
         * Adds a query parameter to the request.
         *
         * @param name the name of the query parameter
         * @param value the value of the query parameter
         * @return {@link ApiRequest}
         */
        public ApiRequest withQueryParam(final String name, final String value) {
            if (value != null) {
                m_queryParams.put(name, value);
            }
            return this;
        }

        /**
         * Adds a query parameter to the request, can be {@code null}.
         *
         * @param param the {@link HTTPQueryParameter} to add to the request
         * @return {@link ApiRequest} builder instance
         */
        public ApiRequest withQueryParam(final HTTPQueryParameter param) {
            if (param != null) {
                m_queryParams.put(param.name(), param.value());
            }
            return this;
        }

        /**
         * Adds query parameters to the request.
         *
         * @param queryParamMap the map of query parameters
         * @return {@link ApiRequest}
         */
        public ApiRequest withQueryParams(final Map<String, String> queryParamMap) {
            if (queryParamMap != null) {
                m_queryParams.putAll(queryParamMap);
            }
            return this;
        }

        /**
         * Adds a cookie parameter to the request.
         *
         * @param key the key of the cookie parameter
         * @param value the value of the cookie parameter
         * @return {@link ApiRequest}
         */
        public ApiRequest withCookieParam(final String key, final String value) {
            if (value != null) {
                m_cookieParams.put(key, value);
            }
            return this;
        }

        /**
         * Adds cookie parameters to the request.
         *
         * @param cookieParamMap the map of cookie parameters
         * @return {@link ApiRequest}
         */
        public ApiRequest withCookieParams(final Map<String, String> cookieParamMap) {
            if (cookieParamMap != null) {
                m_cookieParams.putAll(cookieParamMap);
            }
            return this;
        }

        /**
         * Adds the content type to the request.
         *
         * @param contentType the content type
         * @return {@link ApiResponse}
         */
        public ApiRequest withContentTypeHeader(final MediaType contentType) {
            if (contentType == null) {
                m_contentType = DEFAULT_CONTENT_TYPE;
            } else {
                m_contentType = contentType;
            }
            return this;
        }

        /**
         * Set the accept headers to the request.
         *
         * @param accepts the accept header values
         * @return {@link ApiRequest}
         */
        public ApiRequest withAcceptHeaders(final MediaType... accepts) {
            if (accepts == null) {
                m_headerAccept = null;
            } else if (accepts[0] == null) {
                m_headerAccept = null;
            } else {
                m_headerAccept = accepts.length == 0 ? null :
                    String.join(", ", Arrays.asList(accepts).stream().map(Object::toString).toList());
            }
            return this;
        }

        /**
         * Set the read timeout
         *
         * @param readTimeout
         * @return {@link ApiRequest}
         */
        public ApiRequest withReadTimeout(final Duration readTimeout) {
            m_requestReadTimeout = readTimeout;
            return this;
        }

        /**
         * Build full URL by concatenating base path, the given sub path and query
         * parameters.
         *
         * @param path The sub path.
         * @return The full URL
         * @throws UnsupportedEncodingException
         * @throws URISyntaxException
         */
        private URI buildUrl(final IPath path) {
            var uriBuilder = new URIBuilder(m_baseURI);
            final var segments = new ArrayList<>(uriBuilder.getPathSegments());
            segments.addAll(Arrays.asList(path.segments()));
            uriBuilder.setPathSegments(segments);

            for (var entry : m_queryParams.entrySet()) {
                uriBuilder.addParameter(entry.getKey(), entry.getValue());
            }

            try {
                return uriBuilder.build();
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Unexpected URI syntax violation", e);
            }
        }

        /**
         * Retrieves the API response of the Hub API.
         *
         * @param path        the API path
         * @param method      the API method
         * @param requestBody the request body
         * @param accept      the accepted response type
         * @param contentType the content type
         * @param authNames   the valid authentication names
         * @return the {@link Response} of the Hub API
         * @throws UnsupportedEncodingException
         * @throws URISyntaxException
         * @throws CouldNotAuthorizeException
         * @throws IOException
         */
        private @Owning Response getAPIResponse(final URI uri, final Method method, final Object requestBody,
            final Authenticator auth) throws CouldNotAuthorizeException, IOException {
            // Build the invocation builder which makes the request
            var builder = createInvocationBuilder(uri, auth);

            // Execute the request and retrieve the response
            return executeHttpRequest(builder, method, requestBody);
        }

        /**
         * Creates the {@link Invocation.Builder} for the HTTP request.
         *
         * @param uri the request URL
         * @return the {@link Invocation.Builder}
         * @throws CouldNotAuthorizeException
         */
        private Invocation.Builder createInvocationBuilder(final URI uri, final Authenticator auth)
                throws CouldNotAuthorizeException {

            m_headerParams.put(HttpHeaders.AUTHORIZATION, auth.getAuthorization());

            // Update accept header and set content-type header
            // which got possibly modified through the additional headers
            if (m_headerParams.containsKey(HttpHeaders.ACCEPT)) {
                m_headerAccept = m_headerParams.get(HttpHeaders.ACCEPT);
            }

            if (!m_headerParams.containsKey(HttpHeaders.CONTENT_TYPE)) {
                m_headerParams.put(HttpHeaders.CONTENT_TYPE, m_contentType.toString());
            }

            WebTarget target = m_httpClient.target(uri);
            if (m_requestReadTimeout != null) {
                target.property(HTTP_RECEIVE_TIMEOUT_PROP, m_requestReadTimeout.toMillis());
            }

            final Invocation.Builder builder;
            if (m_headerAccept == null) {
                builder = target.request();
            } else {
                builder = target.request(m_headerAccept);
                m_headerParams.put(HttpHeaders.ACCEPT, m_headerAccept);
            }

            for (final Entry<String, String> keyValue : m_headerParams.entrySet()) {
                builder.header(keyValue.getKey(), keyValue.getValue());
            }

            for (final Entry<String, String> keyValue : m_cookieParams.entrySet()) {
                builder.cookie(keyValue.getKey(), keyValue.getValue());
            }

            return builder;
        }

        /**
         * Executes the HTTP request.
         *
         * @param builder     the {@link Invocation.Builder}
         * @param method      the request method
         * @param requestBody the request body
         * @return the {@link Response}
         * @throws IOException
         */
        @SuppressWarnings("java:S1166")
        private static Response executeHttpRequest(final Invocation.Builder builder, final Method method,
            final Object requestBody) throws IOException {
            try {
                return switch (method) {
                    case POST, PUT, PATCH -> builder.build(method.name(),
                        requestBody != null ? Entity.entity(requestBody, DEFAULT_CONTENT_TYPE) : null).invoke();
                    case GET, DELETE, HEAD -> builder.build(method.name()).invoke();
                };
            } catch (ResponseProcessingException e) {
                throw new IOException("Response processing failed", e.getCause());
            } catch (ProcessingException e) {
                final var cause = e.getCause();
                if (cause instanceof IOException ioexception) {
                    throw ioexception;
                }
                // Handle additional kinds of processing exceptions here
                throw new IOException("Processing failed", cause);
            }
        }

        /**
         * Invoke API by sending HTTP request with the given options.
         *
         * @param path              The sub-path of the HTTP URL.
         * @param method            The request method, one of "GET", "POST",
         * @param body              The request body object - if it is not binary, otherwise
         *                          null.
         * @param returnType        The {@link GenericType} which should be returned
         *
         * @return {@link ApiResponse}
         * @throws CouldNotAuthorizeException
         * @throws IOException
         */
        public <R> ApiResponse<R> invokeAPI(final IPath path, final Method method, final Object body,
                final GenericType<R> returnType) throws CouldNotAuthorizeException, IOException {
            CheckUtils.checkNotNull(returnType, "Missing return type for '%s' '%s'".formatted(method, path));

            // Retrieve the API response
            final var t0 = System.currentTimeMillis();
            final var uri = buildUrl(path);
            try (final var response = getAPIResponse(uri, method, body, m_auth)) {
                final var responseHeaders = response.getHeaders();

                // Get the status info and set the response content-type to
                // null in case the response does not have any content.
                final var responseStatusInfo = response.getStatusInfo();
                String responseContentType = null;
                if (responseHeaders.get(HttpHeaders.CONTENT_TYPE) != null) {
                    responseContentType = responseHeaders.get(HttpHeaders.CONTENT_TYPE).get(0).toString();
                }

                R responseEntity = null;
                final var responseStatusFamily = responseStatusInfo.getFamily();
                final var responseStatusCode = responseStatusInfo.getStatusCode();
                final var responseStatusMessage = responseStatusInfo.getReasonPhrase();
                final var responseEtag = response.getEntityTag();
                if (Family.SUCCESSFUL == responseStatusFamily) {
                    if (responseContentType == null) {
                        return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage,
                                Optional.ofNullable(responseEtag), Result.success(responseEntity));
                    }
                    return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage,
                            Optional.ofNullable(responseEtag), Result.success(response.readEntity(returnType)));
                } else if(Family.CLIENT_ERROR == responseStatusFamily ||
                        Family.SERVER_ERROR == responseStatusFamily) {
                    // TODO JSON error handling
                    final var message = StringUtils.getIfBlank(response.hasEntity() ?
                        response.readEntity(String.class) : null, responseStatusInfo::getReasonPhrase);
                    return new ApiResponse<>(responseHeaders, responseStatusCode,
                            responseStatusMessage, Optional.ofNullable(responseEtag), Result.failure(message, null));
                } else if (Family.REDIRECTION == responseStatusFamily) {
                    String message;
                    if (response instanceof org.apache.cxf.jaxrs.impl.ResponseImpl cxfResponse) {
                        final var location = cxfResponse.getOutMessage().get("transport.retransmit.url");
                        message = "Redirect failed (firewall?): '%s'".formatted(location);
                    } else {
                        message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) :
                                null, responseStatusInfo::getReasonPhrase);
                    }
                    return new ApiResponse<>(responseHeaders, responseStatusCode,
                            responseStatusMessage, Optional.ofNullable(responseEtag), Result.failure(message, null));
                } else {
                    return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage,
                            Optional.ofNullable(responseEtag), null);
                }
            } finally {
                logDuration(uri, t0, System.currentTimeMillis());
            }

        }

        /**
         * Invoke API by sending HTTP request with the given options. This method
         * handles binary response downloads.
         * @param <R>
         *
         * @param path              The sub-path of the HTTP URL.
         * @param method            The request method, one of "GET", "POST", "PUT" and
         *                          "DELETE".
         * @param body              The request body object - if it is not binary,
         *                          otherwise null.
         * @param contentHandler    The {@link DownloadContentHandler}.
         *
         * @return {@link ApiResponse}
         * @throws IOException
         * @throws CancelationException
         * @throws CouldNotAuthorizeException
         */
        public <R> ApiResponse<R> invokeAPI(final IPath path, final Method method, final Object body,
                final DownloadContentHandler<R> contentHandler)
                        throws IOException, CancelationException, CouldNotAuthorizeException {

            // Retrieve the API response
            final var t0 = System.currentTimeMillis();
            final var uri = buildUrl(path);
            try(final var response = getAPIResponse(uri, method, body, m_auth)) {
                final var responseHeaders = response.getHeaders();

                // Get the status info and set the response content-type to
                // null in case the response does not have any content.
                final var responseStatusInfo = response.getStatusInfo();
                String responseContentType = null;
                if (responseHeaders.get(HttpHeaders.CONTENT_TYPE) != null) {
                    responseContentType = responseHeaders.get(HttpHeaders.CONTENT_TYPE).toString();
                }

                R responseEntity = null;
                final var responseStatusFamily = responseStatusInfo.getFamily();
                final var responseStatusCode = responseStatusInfo.getStatusCode();
                final var responseStatusMessage = responseStatusInfo.getReasonPhrase();
                final var responseEtag = response.getEntityTag();
                if (Family.SUCCESSFUL == responseStatusFamily) {
                    if (responseContentType == null) {
                        return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage,
                                Optional.ofNullable(responseEtag), Result.success(responseEntity));
                    }

                    // Read the content from the response input stream
                    final var length = response.getLength();
                    try (final var inStream = response.readEntity(InputStream.class)) {
                        responseEntity = contentHandler.handleDownload(inStream,
                            length < 0 ? OptionalLong.empty() : OptionalLong.of(length));
                        return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage,
                                Optional.ofNullable(responseEtag), Result.success(responseEntity));
                    }
                } else if(Family.CLIENT_ERROR == responseStatusFamily ||
                        Family.SERVER_ERROR == responseStatusFamily) {
                    // TODO JSON error handling
                    final var message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) :
                            null, responseStatusInfo::getReasonPhrase);
                    return new ApiResponse<>(responseHeaders, responseStatusCode,
                            responseStatusMessage, Optional.ofNullable(responseEtag), Result.failure(message, null));
                } else if (Family.REDIRECTION == responseStatusFamily) {
                    String message;
                    if (response instanceof org.apache.cxf.jaxrs.impl.ResponseImpl cxfResponse) {
                        final var location = cxfResponse.getOutMessage().get("transport.retransmit.url");
                        message = "Redirect failed (firewall?): '%s'".formatted(location);
                    } else {
                        message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) :
                                null, responseStatusInfo::getReasonPhrase);
                    }
                    return new ApiResponse<>(responseHeaders, responseStatusCode,
                            responseStatusMessage, Optional.ofNullable(responseEtag), Result.failure(message, null));
                } else {
                    return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage,
                            Optional.ofNullable(responseEtag), null);
                }
            } finally {
                logDuration(uri, t0, System.currentTimeMillis());
            }

        }

        /**
         * Invoke API by sending HTTP request with the given options. This method
         * handles uploads of zip files (binary request bodies).
         *
         * @param <R>
         *
         * @param path              The sub-path of the HTTP URL.
         * @param method            The request method, one of "GET", "POST", "PUT" and
         *                          "DELETE".
         * @param contentHandler    The {@link UploadContentHandler}.
         *
         * @return {@link ApiResponse}
         * @throws IOException
         * @throws CancelationException if the invocation was canceled through the
         *                                    content handler
         * @throws CouldNotAuthorizeException
         */
        public <R> ApiResponse<R> invokeAPI(final IPath path, final Method method,
                final UploadContentHandler<R> contentHandler)
                        throws IOException, CancelationException, CouldNotAuthorizeException {

            m_headerParams.put(HttpHeaders.AUTHORIZATION, m_auth.getAuthorization());
            m_headerParams.put(HttpHeaders.CONTENT_TYPE, m_contentType.toString());
            if (m_headerAccept != null) {
                m_headerParams.put(HttpHeaders.ACCEPT,
                        String.join(", ", Arrays.asList(m_headerAccept).stream().map(Object::toString).toList()));
            }

            // Create http url connection for upload stream.
            final var conn = prepareAuthenticatedConnection(buildUrl(path).toURL(), method);
            conn.connect();

            // Perform upload
            R responseEntity = null;
            try (final var out = conn.getOutputStream()) {
                responseEntity = contentHandler.handleUpload(out);
            } catch (final CancelationException e) {
                conn.disconnect();
                throw e;
            }

            // Get the response headers
            final var connHeaders = conn.getHeaderFields();
            Map<String, List<Object>> responseHeaders = new HashMap<>();
            for (final var entry : connHeaders.entrySet()) {
                responseHeaders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            // Set the response content-type to null
            // in case the response does not have any content.
            String responseContentType = null;
            if (responseHeaders.get(HttpHeaders.CONTENT_TYPE) != null) {
                responseContentType = responseHeaders.get(HttpHeaders.CONTENT_TYPE).toString();
            }

            final var responseStatusInfo = Response.Status.fromStatusCode(conn.getResponseCode());
            final var responseStatusFamily = responseStatusInfo.getFamily();
            final var responseStatusCode = responseStatusInfo.getStatusCode();
            final var responseStatusMessage = responseStatusInfo.getReasonPhrase();
            if (Family.SUCCESSFUL == responseStatusFamily) {
                if (responseContentType == null) {
                    return new ApiResponse<>(responseHeaders, responseStatusCode,
                            responseStatusMessage, null, Result.success(responseEntity));
                }
                return new ApiResponse<>(responseHeaders, responseStatusCode,
                            responseStatusMessage, null, Result.success(responseEntity));

            } else if (Family.CLIENT_ERROR == responseStatusFamily ||
                    Family.SERVER_ERROR == responseStatusFamily) {
                // TODO JSON error handling
                String message;
                try (final var errStream = conn.getErrorStream()) {
                    message = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8)
                        : responseStatusMessage;
                }
                conn.disconnect();
                return new ApiResponse<>(responseHeaders, responseStatusCode,
                        responseStatusMessage, null, Result.failure(message, null));
            } else {
                return new ApiResponse<>(responseHeaders, responseStatusCode, responseStatusMessage, null, null);
            }
        }

        /**
         * Prepares the authenticated HTTP connection to perform an upload.
         *
         * @param url    the request URL
         * @param method the request method
         * @return the {@link HttpURLConnection}
         * @throws IOException
         */
        private HttpURLConnection prepareAuthenticatedConnection(final URL url, final Method method)
                throws IOException {
            final var conn = (HttpURLConnection) URLConnectionFactory.getConnection(url);
            conn.setRequestMethod(method.name());
            conn.setDoOutput(true);
            conn.setConnectTimeout(Math.toIntExact(m_connectionTimeout.toMillis()));
            conn.setReadTimeout(Math.toIntExact(m_readTimeout.toMillis()));
            m_headerParams.forEach(conn::addRequestProperty);
            return conn;
        }

        private static void logDuration(final URI uri, final long fromMillis, final long toMillis) {
            if (!LOGGER.isDebugEnabled()) {
                return;
            }
            LOGGER.atDebug() //
                .addArgument(uri) //
                .addArgument(() -> "%.3f".formatted((toMillis - fromMillis) / 1000.0)) //
                .setMessage("Request '{}' took {}s") //
                .log();
        }

    }

    /**
     * Returns the associated http client.
     *
     * @return {@link Client}
     */
    public @NotOwning Client getHttpClient() {
        return m_httpClient;
    }

    /**
     * Retrieves the base path of the API client.
     *
     * @return the base URI
     */
    public URI getBaseURI() {
        return m_baseURI;
    }

    /**
     * Returns the current object mapper used for JSON
     * serialization/deserialization.
     *
     * @return Object mapper
     */
    public ObjectMapper getObjectMapper() {
        return m_objectMapper;
    }

    /**
     * Retrieves the connection timeout duration.
     *
     * @return {@link Duration} connection timeout
     */
    public Duration getConnectTimeout() {
        return m_connectionTimeout;
    }

    /**
     * Retrieves the read timeout duration
     *
     * @return {@link Duration} read timeout
     */
    public Duration getReadTimeout() {
        return m_readTimeout;
    }

    /**
     * Callback interface for endpoints returning larger binary results.
     *
     * @param <R> type of the result value
     */
    @FunctionalInterface
    public interface DownloadContentHandler<R> {
        /**
         * Called if the request succeeded, may consume the input stream and return a
         * result, which is passed out of the method doing the request. The input stream
         * is closed after this method returns.
         *
         * @param data          response data as input stream
         * @param contentLength content length if available
         * @return result value
         * @throws IOException
         * @throws CancelationException in case the download was canceled
         */
        R handleDownload(@Owning InputStream data, OptionalLong contentLength) // NOSONAR `OptionalLong` is fine
                throws IOException, CancelationException;
    }

    /**
     * Callback interface for endpoints uploading larger binary results.
     *
     * @param <R> type of the upload result
     */
    @FunctionalInterface
    public interface UploadContentHandler<R> {
        /**
         * Called if the request succeeded, may consume the input stream, which is
         * passed out of the method doing the request. The input stream is closed after
         * this method returns.
         *
         * @param out output data stream
         * @return result of upload
         * @throws IOException                if an I/O error occurred
         * @throws CancelationException if the upload was canceled
         */
        R handleUpload(@Owning OutputStream out) throws IOException, CancelationException;
    }

    @Override
    public void close() {
        m_httpClient.close();
    }

}
