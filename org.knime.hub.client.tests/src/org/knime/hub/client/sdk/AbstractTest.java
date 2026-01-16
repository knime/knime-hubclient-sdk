/*
 * ------------------------------------------------------------------------
 *
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
 * ---------------------------------------------------------------------
 *
 * History
 *   May 20, 2025 (magnus): created
 */
package org.knime.hub.client.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IPath;
import org.knime.core.util.auth.Authenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.testing.HttpMockServiceFactory;
import org.knime.hub.client.sdk.testing.TestUtil;
import org.knime.hub.client.sdk.testing.TestUtil.EntityFolders;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Abstract test class for unit tests.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@SuppressWarnings({"java:S1694", "java:S1118"}) // S1694, S1118: abstract test base class
public abstract class AbstractTest {

    private static ApiClient apiClient;
    private static ObjectMapper mapper;

    private static WireMockServer serverMock;
    private static HubClientAPI hubClientAPIMock;
    private static Configuration jsonPathConfig;
    private static String authToken;

    /**
     * Initializes the server mocks.
     *
     * @throws CouldNotAuthorizeException if an I/O error occurred during authorization
     */
    public static void initializeServerMockTests() throws CouldNotAuthorizeException {
        // Initialize wiremock server.
        serverMock = HttpMockServiceFactory.createMockServer();
        serverMock.start();
        final var serverAdress = HttpMockServiceFactory.getBaseUri(serverMock);

        // Create the HUB client API.
        final var authMock = Mockito.mock(Authenticator.class);
        authToken = UUID.randomUUID().toString();
        when(authMock.getAuthorization()).thenReturn(authToken);
        apiClient = new ApiClient(serverAdress, authMock, "junit-test", Duration.ofSeconds(0), Duration.ofSeconds(0));
        mapper = apiClient.getObjectMapper();
        hubClientAPIMock = new HubClientAPI(apiClient);

        // Configure JsonPath to use Jackson for JsonNode compatibility
        // Returns a null node if a JSON property is missing.
        jsonPathConfig = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build();
    }

    /**
     * Returns the shared {@link ApiClient} used by tests.
     *
     * @return api client instance
     */
    protected static ApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Returns the WireMock server instance.
     *
     * @return server mock
     */
    protected static WireMockServer getServerMock() {
        return serverMock;
    }

    /**
     * Creates a mocked GET /repository/pathOrId request
     *
     * @param testFileName file name of the JSON body
     * @param repoItemPathOrId path or ID of the repository item
     * @param queryParams the query parameters for the request
     * @return {@link JsonNode}
     * @throws IOException if an I/O error occurred
     */
    public static JsonNode retrieveRepositoryItemMetaData(final String testFileName, final String repoItemPathOrId,
            final Map<String, String> queryParams) throws IOException {
        // Path to the file inside test file folder.
        final var filePath = IPath.forPosix(TestUtil.RESOURCE_FOLDER_NAME)
                .append(EntityFolders.CATALOG_ENTITES.toString()).append(testFileName);

        // Obtain file object from bundle activator class.
        final URL resourceUrl = TestUtil.resolveToURL(filePath);

        try (final InputStream is = resourceUrl.openStream()) {
            // Create a json node from the resource.
            JsonNode jsonNode = mapper.readTree(is);

            // Create query parameter string.
            String queryParamString = StringUtils.EMPTY;
            if (!queryParams.isEmpty()) {
                var queryParamPairs = queryParams.keySet().stream()
                        .map(key -> "%s=%s".formatted(key, queryParams.get(key))).toList();
                queryParamString = "?%s".formatted(String.join("&", queryParamPairs));
            }

            // Create a stub for the getRepositoryItemMetaData request.
            serverMock.stubFor(get(urlEqualTo("/repository/%s%s".formatted(repoItemPathOrId, queryParamString)))
                    .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .withBody(jsonNode.toString())));

            return jsonNode;
        }
    }

    /**
     * Terminates the server mocks.
     */
    public static void terminateServerMockTests() {
        serverMock.stop();
        hubClientAPIMock.getApiClient().close();
    }

    private static final ObjectMapper MAPPER =
        new ApiClient(null, null, "junit-test", Duration.ofSeconds(0), Duration.ofSeconds(0)).getObjectMapper();

    /**
     * Loads the JSON resource file for entity creation tests.
     *
     * @param <T> entity type
     * @param entityFolder {@link EntityFolders}
     * @param filename the name of the file in the resource folder
     * @param clazz the {@link Class} of the entity
     * @return the created entity
     * @throws IOException if the fixture cannot be read
     */
    public static <T> T load(final EntityFolders entityFolder, final String filename, final Class<T> clazz)
        throws IOException {
        // Path to the file inside test file folder.
        final var filePath =
            IPath.forPosix(TestUtil.RESOURCE_FOLDER_NAME).append(entityFolder.toString()).append(filename);

        // Obtain path object from bundle activator class.
        final var url = TestUtil.resolveToURL(filePath);
        return MAPPER.readValue(url, clazz);
    }

    /**
     * Retrieves the {@link ObjectMapper} which is used by the {@link ApiClient}.
     *
     * @return mapper
     */
    public static ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * Retrieves the {@link HubClientAPI} mock.
     *
     * @return hubClientAPIMock
     */
    public static HubClientAPI getHubClientAPIMock() {
        return hubClientAPIMock;
    }

    /**
     * Retrieves the JSON configuration needed to query JSON entities for keys.
     *
     * @return jsonPathConfig
     */
    public static Configuration getJsonPathConfig() {
        return jsonPathConfig;
    }

}
