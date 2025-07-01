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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NotOwning;
import org.eclipse.jdt.annotation.Owning;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.util.auth.Authenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.hub.client.sdk.ent.ProblemDescription;
import org.knime.hub.client.sdk.transfer.internal.URLConnectionUploader;
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
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * API client used to make REST requests.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public class ApiClient implements AutoCloseable {

    private static final String HTTP_CONNECTION_TIMEOUT_PROP = "http.connection.timeout";
    private static final String HTTP_RECEIVE_TIMEOUT_PROP = "http.receive.timeout";
    private static final String HTTP_AUTOREDIRECT_PROP = "http.autoredirect";

    /** Media type for an RFC 9457 problem description. */
    public static final MediaType APPLICATION_PROBLEM_JSON_TYPE = MediaType.valueOf("application/problem+json");

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

    private final String m_userAgent;

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiClient.class);

    /**
     * Builds the {@link ApiClient} with the given base URL of the server
     * and specified connection and read timeouts.
     *
     * @param baseURI base URL of the server.
     * @param auth the {@link Authenticator}
     * @param userAgent the value for the user-agent header set for every request
     * @param connectionTimeout the time (in ms) it takes until the connection gets a timeout
     * @param readTimeout the time (in ms) it takes until the read process gets a timeout
     */
    public ApiClient(final URI baseURI, final Authenticator auth, final String userAgent,
            final Duration connectionTimeout, final Duration readTimeout) {
        // Set base path and user agent
        m_baseURI = baseURI;
        m_userAgent = userAgent;

        // Set authentication
        m_auth = auth;

        // Configure the object mapper for the JSON provider
        m_objectMapper = createObjectMapper();

        // Set timeouts
        m_connectionTimeout = connectionTimeout;
        m_readTimeout = readTimeout;

        // Create a http client builder and register the defined JSON provider
        final var clientBuilder = ClientBuilder.newBuilder();

        // Register a GZIP response filter for compressed responses
        clientBuilder.register(GZipResponseFilter.class);

        // Add object mapper for deserialization
        clientBuilder.register(new JacksonJsonProvider(m_objectMapper));

        // Enable automatic redirects (e.g. for download)
        clientBuilder.property(HTTP_AUTOREDIRECT_PROP, true);

        // Set timeouts for connection
        if (m_connectionTimeout != null) {
            clientBuilder.property(HTTP_CONNECTION_TIMEOUT_PROP, m_connectionTimeout.toMillis());
        }
        if (m_readTimeout != null) {
            clientBuilder.property(HTTP_RECEIVE_TIMEOUT_PROP, m_readTimeout.toMillis());
        }

        m_httpClient = clientBuilder.build();
    }

    private static ObjectMapper createObjectMapper() {
        // Configure the object mapper for the JSON provider
        return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module()); // Needed for Optional support.
    }

    /**
     * Creates the {@link JacksonJsonProvider} which is needed for JSON de-serialization.
     *
     * @return {@link JacksonJsonProvider}
     * @since 0.1
     */
    public static JacksonJsonProvider createJsonProvider() {
        return new JacksonJsonProvider(createObjectMapper());
    }

    /**
     * Creates an invocation builder for non-Hub calls (e.g. to MinIO or S3) that don't use our authentication.
     *
     * @param url target URL
     * @return the invocation builder
     */
    public Invocation.Builder nonApiInvocationBuilder(final String url) {
        return m_httpClient.target(url).request();
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
        private MediaType m_contentType;
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
            m_queryParams.values().removeIf(Objects::isNull);
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
            if (contentType != null) {
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
            if (accepts == null || accepts.length == 0 || accepts[0] == null) {
                m_headerAccept = null;
            } else {
                m_headerAccept = String.join(", ", Arrays.asList(accepts).stream().map(Object::toString).toList());
            }
            return this;
        }

        /**
         * Sets the read timeout.
         *
         * @param readTimeout the read timeout for a request
         *
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
         * @throws HubFailureIOException
         */
        private @Owning Response getAPIResponse(final URI uri, final Method method, final Object requestBody)
            throws HubFailureIOException {
            // Build the invocation builder which makes the request
            var builder = createInvocationBuilder(uri, requestBody);

            // Execute the request and retrieve the response
            return executeHttpRequest(builder, method, requestBody);
        }

        /**
         * Creates the {@link Invocation.Builder} for the HTTP request.
         *
         * @param uri the request URL
         * @param body the request body
         *
         * @return the {@link Invocation.Builder}
         *
         * @throws HubFailureIOException
         */
        private Invocation.Builder createInvocationBuilder(final URI uri, final Object body)
            throws HubFailureIOException {
            updateHeaderParameters();

            CheckUtils.checkState(hasRequiredContentType(body),
                "Missing required content type for '%s'".formatted(uri));

            WebTarget target = m_httpClient.target(uri);
            if (m_requestReadTimeout != null) {
                target.property(HTTP_RECEIVE_TIMEOUT_PROP, m_requestReadTimeout.toMillis());
            }

            final Invocation.Builder builder;
            if (m_headerAccept == null) {
                builder = target.request();
            } else {
                builder = target.request(m_headerAccept);
            }

            // Set headers and cookies
            m_headerParams.forEach(builder::header);
            m_cookieParams.forEach(builder::cookie);

            return builder;
        }

        private void updateHeaderParameters() throws HubFailureIOException {
            try {
                m_headerParams.put(HttpHeaders.AUTHORIZATION, m_auth.getAuthorization());
            } catch (final ProcessingException pe) {
                throw new HubFailureIOException(FailureValue.fromProcessingException(pe));
            } catch (CouldNotAuthorizeException cnae) {
                throw new HubFailureIOException(FailureValue.fromAuthFailure(cnae));
            }

            m_headerParams.remove(HttpHeaders.USER_AGENT);
            if (m_userAgent != null) {
                m_headerParams.put(HttpHeaders.USER_AGENT, m_userAgent);
            }

            // Update accept header and set content-type header
            // which got possibly modified through the additional headers
            if (m_headerParams.containsKey(HttpHeaders.ACCEPT)) {
                m_headerAccept = m_headerParams.get(HttpHeaders.ACCEPT);
            } else if (m_headerAccept != null) {
                m_headerParams.put(HttpHeaders.ACCEPT, m_headerAccept);
            }

            if (m_headerParams.containsKey(HttpHeaders.CONTENT_TYPE)) {
                m_contentType = MediaType.valueOf(m_headerParams.get(HttpHeaders.CONTENT_TYPE));
            } else if (m_contentType != null) {
                m_headerParams.put(HttpHeaders.CONTENT_TYPE, m_contentType.toString());
            }
        }

        /**
         * Executes the HTTP request.
         *
         * @param builder     the {@link Invocation.Builder}
         * @param method      the request method
         * @param requestBody the request body
         * @return the {@link Response}
         */
        @SuppressWarnings("java:S1166")
        private Response executeHttpRequest(final Invocation.Builder builder, final Method method,
            final Object requestBody) throws HubFailureIOException {
            try {
                return switch (method) {
                    case POST, PUT, PATCH -> builder
                        .build(method.name(), requestBody != null ? Entity.entity(requestBody, m_contentType) : null)
                        .invoke();
                    case GET, DELETE, HEAD -> builder.build(method.name()).invoke();
                };
            } catch (ProcessingException e) {
                throw new HubFailureIOException(FailureValue.fromProcessingException(e));
            }
        }

        private boolean hasRequiredContentType(final Object body) {
            return body == null || m_contentType != null;
        }

        /**
         * Invoke API by sending HTTP request with the given options.
         *
         * @param path              The sub-path of the HTTP URL.
         * @param method            The request method, one of "GET", "POST", "PUT" and "DELETE".
         * @param body              The request body object - if it is not binary, otherwise null.
         *
         * @return {@link ApiResponse}
         *
         * @throws HubFailureIOException if the request failed before a response was received
         */
        public ApiResponse<Void> invokeAPI(final IPath path, final Method method, final Object body)
            throws HubFailureIOException {
            return invokeAPI(path, method, body, (GenericType<Void>)null);
        }

        /**
         * Invoke API by sending HTTP request with the given options.
         *
         * @param path              The sub-path of the HTTP URL.
         * @param method            The request method, one of "GET", "POST", "PUT" and "DELETE".
         * @param body              The request body object - if it is not binary, otherwise null.
         * @param returnType        The {@link GenericType} which should be returned, {@code null} ignores the response
         *
         * @return {@link ApiResponse}
         *
         * @throws HubFailureIOException if the request failed before a response was received
         */
        public <R> ApiResponse<R> invokeAPI(final IPath path, final Method method, final Object body,
                final GenericType<R> returnType) throws HubFailureIOException {

            // Retrieve the API response
            final var t0 = System.currentTimeMillis();
            final var uri = buildUrl(path);
            try (final var response = getAPIResponse(uri, method, body)) {

                final var httpStatus = response.getStatusInfo();
                final  Result<R, FailureValue> result = switch (httpStatus.getFamily()) {
                    case SUCCESSFUL -> returnType == null ? Result.success(null)
                        : readEntity(method, uri, returnType, response);
                    case REDIRECTION -> createFailureForRedirect(response); // auto-redirect failed
                    case CLIENT_ERROR, SERVER_ERROR -> createFailureForResponse(response);
                    case INFORMATIONAL, OTHER -> throw new IllegalStateException("Unexpected HTTP response code: %d %s"
                        .formatted(httpStatus.getStatusCode(), httpStatus.getReasonPhrase()));
                };

                return new ApiResponse<>(response.getHeaders(), httpStatus.getStatusCode(),
                    httpStatus.getReasonPhrase(), Optional.ofNullable(response.getEntityTag()), result);
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
         * @param method            The request method, one of "GET", "POST", "PUT" and "DELETE".
         * @param body              The request body object - if it is not binary, otherwise null.
         * @param contentHandler    The {@link DownloadContentHandler}.
         *
         * @return {@link ApiResponse}
         * @throws IOException if an I/O error occurs during the request
         * @throws CancelationException if the user cancelled the process
         */
        public <R> ApiResponse<R> invokeAPI(final IPath path, final Method method, final Object body,
            final DownloadContentHandler<R> contentHandler) throws IOException, CancelationException {
            final var t0 = System.currentTimeMillis();

            // Retrieve the API response
            final var uri = buildUrl(path);
            try (final var response = getAPIResponse(uri, method, body)) {

                final var httpStatus = response.getStatusInfo();
                final Result<R, FailureValue> result = switch (httpStatus.getFamily()) {
                    case SUCCESSFUL -> downloadContent(method, uri, response, contentHandler);
                    case REDIRECTION -> createFailureForRedirect(response); // auto-redirect failed
                    case CLIENT_ERROR, SERVER_ERROR -> createFailureForResponse(response);
                    case INFORMATIONAL, OTHER -> throw new IllegalStateException("Unexpected HTTP response code: %d %s"
                        .formatted(httpStatus.getStatusCode(), httpStatus.getReasonPhrase()));
                };

                return new ApiResponse<>(response.getHeaders(), httpStatus.getStatusCode(),
                    httpStatus.getReasonPhrase(), Optional.ofNullable(response.getEntityTag()), result);
            } finally {
                logDuration(uri, t0, System.currentTimeMillis());
            }
        }

        /**
         * Invoke API by sending HTTP request with the given options. This method handles uploads of binary request
         * bodies.
         *
         * @param path The sub-path of the HTTP URL.
         * @param method The request method, one of "GET", "POST", "PUT" and "DELETE".
         * @param data input stream for the data to be uploaded
         * @param numBytes number of bytes to be written
         * @param returnType the {@link GenericType} of the entity to be returned, {@code null} ignores the response
         *
         * @return {@link ApiResponse}
         * @throws IOException if an I/O error occurred during the request
         */
        public <T> ApiResponse<T> invokeAPI(final IPath path, final Method method, final @Owning InputStream data,
            final long numBytes, final GenericType<T> returnType) throws IOException {
            if (numBytes >= 0) {
                m_headerParams.put(HttpHeaders.CONTENT_LENGTH, Long.toString(numBytes));
            }

            // Retrieve the API response
            final var t0 = System.currentTimeMillis();
            final var uri = buildUrl(path);
            try (final var response = getAPIResponse(uri, method, data)) {

                final var httpStatus = response.getStatusInfo();
                final Result<T, FailureValue> result = switch (httpStatus.getFamily()) {
                    case SUCCESSFUL -> returnType == null ? Result.success(null)
                        : readEntity(method, uri, returnType, response);
                    case REDIRECTION -> createFailureForRedirect(response); // auto-redirect failed
                    case CLIENT_ERROR, SERVER_ERROR -> createFailureForResponse(response);
                    case INFORMATIONAL, OTHER -> throw new IllegalStateException("Unexpected HTTP response code: %d %s"
                        .formatted(httpStatus.getStatusCode(), httpStatus.getReasonPhrase()));
                };

                return new ApiResponse<>(response.getHeaders(), httpStatus.getStatusCode(),
                    httpStatus.getReasonPhrase(), Optional.ofNullable(response.getEntityTag()), result);
            } finally {
                logDuration(uri, t0, System.currentTimeMillis());
            }
        }

        /**
         * Creates an invocation builder for hub calls.
         *
         * @param path The sub-path of the HTTP URL.
         * @param requestBody The request body
         *
         * @return the invocation builder
         *
         * @throws HubFailureIOException if an I/O error occurred during the creation of the builder
         * @since 0.1
         */
        public Invocation.Builder apiInvocationBuilder(final IPath path, final Object requestBody)
                throws HubFailureIOException {
            return createInvocationBuilder(buildUrl(path), requestBody);
        }

        /**
         * Creates an HTTP URL connection given the HTTP URL sub-path.
         *
         * @param httpMethod The HTTP request method
         * @param path The sub-path of the HTTP URL
         * @param chunkSize The chunk size for the upload connection
         * @return The {@link HttpURLConnection}
         *
         * @throws IOException If an I/O error occurred during opening of the connection
         * @since 0.1
         */
        public HttpURLConnection createAPIURLConnection(final String httpMethod,
            final IPath path, final int chunkSize) throws IOException {
            try {
                return URLConnectionUploader.prepareConnection(
                    buildUrl(path).toURL(), httpMethod, m_headerParams, chunkSize,
                    m_requestReadTimeout, m_requestReadTimeout);
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Unexpected URL syntax violation", ex);
            }
        }

        private static <R> Result<R, FailureValue> readEntity(final Method method, final URI uri,
            final GenericType<R> returnType, final @NotOwning Response response) {
            if (!response.hasEntity()) {
                throw new IllegalStateException("Hub response with missing body: %s %s".formatted(method, uri));
            }

            try {
                return Result.success(response.readEntity(returnType));
            } catch (final ProcessingException pe) {
                return Result.failure(FailureValue.fromProcessingException(pe));
            }
        }

        private static <R> Result<R, FailureValue> downloadContent(final Method method, final URI uri,
            final @NotOwning Response response, final DownloadContentHandler<R> contentHandler)
            throws IOException, CancelationException {
            if (!response.hasEntity()) {
                throw new IllegalStateException("Hub response with missing body: %s %s".formatted(method, uri));
            }

            try (final var inStream = response.readEntity(InputStream.class)) {
                final var length = response.getLength();
                final var contentLength = length < 0 ? OptionalLong.empty() : OptionalLong.of(length);
                return Result.success(contentHandler.handleDownload(inStream, contentLength));
            }
        }

        private static <R> Result<R, FailureValue> createFailureForResponse(final Response response) {
            final var contentType = response.getMediaType();
            final var responseStatusInfo = response.getStatusInfo();
            final var statusCode = responseStatusInfo.getStatusCode();
            if (contentType != null && contentType.isCompatible(APPLICATION_PROBLEM_JSON_TYPE)
                    && response.hasEntity()) {
                try {
                    final var problemDesc = response.readEntity(ProblemDescription.class);
                    return Result
                        .failure(FailureValue.fromRFC9457(FailureType.HUB_FAILURE_RESPONSE, statusCode, problemDesc));
                } catch (final ProcessingException pe) {
                    LOGGER.atError() //
                        .setCause(pe) //
                        .addArgument(responseStatusInfo.getStatusCode()) //
                        .addArgument(responseStatusInfo.getReasonPhrase()) //
                        .log("Hub request failed: {} {}");
                    return Result.failure(
                        FailureValue.fromHTTP(FailureType.HUB_FAILURE_RESPONSE, statusCode,
                            "Hub request failed: " + responseStatusInfo.getReasonPhrase()));
                }
            }

            // Create a exception failure value
            final var message = StringUtils.getIfBlank(response.hasEntity() ?
                response.readEntity(String.class) : null, responseStatusInfo::getReasonPhrase);
            if (statusCode >= 500 && LOGGER.isErrorEnabled()) {
                LOGGER.atError() //
                    .setCause(new IOException()) //
                    .addArgument(statusCode) //
                    .addArgument(message) //
                    .log("Hub request failed: {} {}");
            }
            return Result.failure(
                FailureValue.fromHTTP(FailureType.HUB_FAILURE_RESPONSE, statusCode, "Hub request failed: " + message));
        }

        private static <R> Result<R, FailureValue> createFailureForRedirect(final Response response) {
            // A redirect is not a failure response, so we don't expect `application/problem+json` here
            final String message;
            if (response instanceof org.apache.cxf.jaxrs.impl.ResponseImpl cxfResponse) {
                final var location = cxfResponse.getOutMessage().get("transport.retransmit.url");
                message = "Redirect failed (firewall?): '%s'".formatted(location);
            } else {
                message = StringUtils.getIfBlank(response.hasEntity() ? response.readEntity(String.class) :
                    null, () -> response.getStatusInfo().getReasonPhrase());
            }
            return Result.failure(FailureValue.fromHTTP(FailureType.REDIRECT_FAILED, response.getStatus(), message));
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
     * @since 0.1
     */
    public Optional<Duration> getConnectTimeout() {
        return Optional.ofNullable(m_connectionTimeout);
    }

    /**
     * Retrieves the read timeout duration
     *
     * @return {@link Duration} read timeout
     * @since 0.1
     */
    public Optional<Duration> getReadTimeout() {
        return Optional.ofNullable(m_readTimeout);
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

    @Override
    public void close() {
        m_httpClient.close();
    }
}
