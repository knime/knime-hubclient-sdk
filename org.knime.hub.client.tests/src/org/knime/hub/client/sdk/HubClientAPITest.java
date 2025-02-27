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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.knime.core.util.auth.Authenticator;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.testing.HttpMockServiceFactory;
import org.knime.hub.client.sdk.testing.TestUtil;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Integration tests for {@link HubClientAPI}.
 *
 * This only includes enough test cases to test the de-serialization of the response entities.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
class HubClientAPITest {

	public static final String RESOURCE_FOLDER_NAME = "resources";
    public static final String TEST_FILE_FOLDER_NAME = "repositoryMetaDataTestFiles";

	public static final String DEFAULT_USERNAME = "knime";
	public static final String DEFAULT_PUBLIC_SPACE_NAME = "PublicSpace";
	public static final String DEFAULT_PRIVATE_SPACE_NAME = "PrivateSpace";
	public static final String DEFAULT_PUBLIC_SPACE_PATH = "Users/%s/%s".
			formatted(DEFAULT_USERNAME, DEFAULT_PUBLIC_SPACE_NAME);
    public static final String DEFAULT_PRIVATE_SPACE_PATH = "Users/%s/%s".
    		formatted(DEFAULT_USERNAME, DEFAULT_PRIVATE_SPACE_NAME);

    public static final String JSON_PATH_PATH = "$['path']";
    public static final String JSON_PATH_ID = "$['id']";
    public static final String JSON_PATH_TYPE = "$['type']";
    public static final String JSON_PATH_OWNER = "$['owner']";
    public static final String JSON_PATH_DESCRIPTION = "$['description']";
    public static final String JSON_PATH_DETAILS_SPACE_ID = "$['details']['space']['spaceId']";
    public static final String JSON_PATH_MASON_CONTROLS_HREF = "$['@controls'][*]['href']";
    public static final String JSON_PATH_MASON_CONTROLS_METHOD = "$['@controls'][*]['method']";

    public static final String JSON_PATH_CHILD_ITEM_PATH = "$['children'][*]['path']";
    public static final String JSON_PATH_CHILD_ITEM_ID = "$['children'][*]['id']";
    public static final String JSON_PATH_CHILD_ITEM_TYPE = "$['children'][*]['type']";
    public static final String JSON_PATH_CHILD_ITEM_OWNER = "$['children'][*]['owner']";
    public static final String JSON_PATH_CHILD_ITEM_DESCRIPTION = "$['children'][*]['description']";

    public static final String JSON_PATH_CHILD_ITEM_MASON_CONTROLS_HREF =
    		"$['children'][*]['@controls'][*]['href']";
    public static final String JSON_PATH_CHILD_ITEM_MASON_CONTROLS_METHOD =
    		"$['children'][*]['@controls'][*]['method']";

    private static ApiClient apiClient;
    private static ObjectMapper mapper;

	private static WireMockServer serverMock;
	private static HubClientAPI hubClientAPIMock;
	private static Configuration jsonPathConfig;
	private static String authToken;

	@BeforeAll
	static void startServerMock() throws CouldNotAuthorizeException {
	    // Initialize wiremock server.
	    serverMock = HttpMockServiceFactory.createMockServer();
	    serverMock.start();
	    final var serverAdress = HttpMockServiceFactory.getBaseUri(serverMock);

	    // Create the HUB client API.
	    final var authMock = Mockito.mock(Authenticator.class);
	    authToken = UUID.randomUUID().toString();
	    when(authMock.getAuthorization()).thenReturn(authToken);
        apiClient = new ApiClient(serverAdress, authMock, Duration.ofSeconds(0), Duration.ofSeconds(0));
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

	private static JsonNode retrieveRepositoryItemMetaData(final String testFileName, final String repoItemPathOrId,
	        final Map<String, String> queryParams) throws IOException, URISyntaxException {
        // Path to the file inside test file folder.
	    final var filePath = IPath.forPosix(HubClientAPITest.RESOURCE_FOLDER_NAME)
	            .append(HubClientAPITest.TEST_FILE_FOLDER_NAME).append(testFileName);

        // Obtain file object from bundle activator class.
        File file = TestUtil.resolvePath(filePath).toFile();

        try (InputStream is = new FileInputStream(file);) {
            // Create a json node from the resource.
            JsonNode jsonNode = mapper.readTree(is);

            // Create query paramter string.
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
	 * Tests that the response entities are deserialized
	 * into the expected repository item meta data.
	 *
	 * @param testFileName the provided JSON response
	 * @param repoItemName the name of the repository item
	 * @throws IOException
	 * @throws CouldNotAuthorizeException
	 * @throws URISyntaxException
	 */
	@ParameterizedTest
    @CsvSource(value = {
        "workflow.json,        Workflow",
        "component.json,       Component",
        "data.json,            Data",
        "workflowGroup.json,   WorkflowGroup",
        "space.json,           Space"
    })
	void testGetRepositoryItemMetaDataWithoutDetails(final String testFileName, final String repoItemName)
			throws IOException, CouldNotAuthorizeException, URISyntaxException {
	    String path = "%s/%s".formatted(DEFAULT_PUBLIC_SPACE_PATH, repoItemName);
        String details = null;
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = null;
        String version = null;
        String spaceVersion = null;

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse =
                retrieveRepositoryItemMetaData(testFileName, path, new HashMap<String, String>());

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = hubClientAPIMock.getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, spaceVersion, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION,
                JSON_PATH_MASON_CONTROLS_HREF, JSON_PATH_MASON_CONTROLS_METHOD);
        assertJSONProperties(response, knimeHubJSONResponse, expectedJsonPaths);
	}

	/**
	 * Tests that the response entities are deserialized into the expected repository
	 * item meta data where "full" details are requested.
	 *
	 * @param testFileName the provided JSON response
     * @param repoItemName the name of the repository item
	 * @throws IOException
	 * @throws CouldNotAuthorizeException
	 * @throws URISyntaxException
	 */
	@ParameterizedTest
    @CsvSource(value = {
        "workflowWithDetails.json,        Workflow",
        "componentWithDetails.json,       Component",
        "dataWithDetails.json,            Data",
        "workflowGroupWithDetails.json,   WorkflowGroup",
        // Space does not provide details if requested.
    })
    void testGetRepositoryItemMetaDataWithDetails(final String testFileName, final String repoItemName)
            throws IOException, CouldNotAuthorizeException, URISyntaxException {
        String path = "%s/%s".formatted(DEFAULT_PUBLIC_SPACE_PATH, repoItemName);
        String details = "full";
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = null;
        String version = null;
        String spaceVersion = null;

        final var queryParams = new HashMap<String, String>();
        queryParams.put("details", details);

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse = retrieveRepositoryItemMetaData(testFileName, path, queryParams);

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = hubClientAPIMock.getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, spaceVersion, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION,
                JSON_PATH_MASON_CONTROLS_HREF, JSON_PATH_MASON_CONTROLS_METHOD, JSON_PATH_DETAILS_SPACE_ID);
        assertJSONProperties(response, knimeHubJSONResponse, expectedJsonPaths);
    }

	/**
	 * Tests that the response entities which have the children property
	 * are deserialized into the expected repository item meta data.
	 *
	 * @param testFileName the provided JSON response
     * @param repoItemName the name of the repository item
	 * @throws IOException
	 * @throws CouldNotAuthorizeException
	 * @throws URISyntaxException
	 */
	@ParameterizedTest
    @CsvSource(value = {
        "workflowGroup.json,   WorkflowGroup",
        "space.json,           Space"
    })
	void testGetRepositoryItemMetaDataWithChildren(final String testFileName, final String repoItemName)
	        throws IOException, CouldNotAuthorizeException, URISyntaxException {
	    String path = "%s/%s".formatted(DEFAULT_PUBLIC_SPACE_PATH, repoItemName);
        String details = null;
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = null;
        String version = null;
        String spaceVersion = null;

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse =
                retrieveRepositoryItemMetaData(testFileName, path, new HashMap<String, String>());

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = hubClientAPIMock.getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, spaceVersion, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION, JSON_PATH_MASON_CONTROLS_HREF,
                JSON_PATH_MASON_CONTROLS_METHOD, JSON_PATH_CHILD_ITEM_PATH, JSON_PATH_CHILD_ITEM_ID,
                JSON_PATH_CHILD_ITEM_TYPE, JSON_PATH_CHILD_ITEM_OWNER, JSON_PATH_CHILD_ITEM_DESCRIPTION);
        assertJSONProperties(response, knimeHubJSONResponse, expectedJsonPaths);
	}

	/**
	 * Tests that the response entities of user groups for "children" contributer spaces
     * are deserialized into the expected repository item meta data.
	 *
	 * @throws IOException
	 * @throws CouldNotAuthorizeException
	 * @throws URISyntaxException
	 */
	@Test
	void testGetRepositoryItemMetaDataWithContribSpaces() throws IOException, CouldNotAuthorizeException,
	URISyntaxException {
        String path = "Users/%s".formatted(DEFAULT_USERNAME);
        String details = null;
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = "children";
        String version = null;
        String spaceVersion = null;

        final var queryParams = new HashMap<String, String>();
        queryParams.put("contribSpaces", contribSpaces);

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse = retrieveRepositoryItemMetaData("userGroupWithContribSpaces.json",
        		path, queryParams);

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = hubClientAPIMock.getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, spaceVersion, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION, JSON_PATH_MASON_CONTROLS_HREF,
                JSON_PATH_MASON_CONTROLS_METHOD, JSON_PATH_CHILD_ITEM_PATH, JSON_PATH_CHILD_ITEM_ID,
                JSON_PATH_CHILD_ITEM_TYPE, JSON_PATH_CHILD_ITEM_OWNER, JSON_PATH_CHILD_ITEM_DESCRIPTION,
                JSON_PATH_CHILD_ITEM_MASON_CONTROLS_HREF, JSON_PATH_CHILD_ITEM_MASON_CONTROLS_METHOD);
        assertJSONProperties(response, knimeHubJSONResponse, expectedJsonPaths);
    }

	private static <R> void assertJSONProperties(final ApiResponse<R> actualApiResponse,
			final JsonNode knimeHubJSONResponse, final List<String> expectedJsonPaths) {
	    // Create the actual JSON node response object.
	    var responseEntity = actualApiResponse.result().toOptional().get();
        JsonNode actualJSONResponse = mapper.valueToTree(responseEntity);

        // Compare the JSON properties queried using the expected JSON paths.
        for (var jsonPath : expectedJsonPaths) {
            var actualJSONProperties = JsonPath.using(jsonPathConfig).parse(actualJSONResponse).read(jsonPath);
            var expectedJSONProperties = JsonPath.using(jsonPathConfig).parse(knimeHubJSONResponse).read(jsonPath);
            assertEquals(expectedJSONProperties, actualJSONProperties,
            		"Unexpected properties for JSON path '%s'".formatted(jsonPath));
        }
    }

    @AfterAll
	static void stopServerMock() {
	    serverMock.stop();
	    hubClientAPIMock.getApiClient().close();
	}

}
