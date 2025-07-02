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

package org.knime.hub.client.sdk.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.knime.core.util.auth.CouldNotAuthorizeException;
import org.knime.core.util.hub.ItemVersion;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.ent.catalog.RepositoryItem;
import org.knime.hub.client.sdk.testing.TestUtil;

/**
 * Integration tests for {@link HubClientAPI}.
 *
 * This only includes enough test cases to test the de-serialization of the response entities.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@SuppressWarnings("resource") // Mocked hub API client is closed after all tests ran through.
class HubClientAPITest extends AbstractTest {

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

	@BeforeAll
	static void startServerMock() throws CouldNotAuthorizeException {
	    initializeServerMockTests();
	}

	/**
	 * Tests that the response entities are deserialized
	 * into the expected repository item meta data.
	 *
	 * @param testFileName the provided JSON response
	 * @param repoItemName the name of the repository item
	 * @throws IOException
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
	    throws IOException {
	    String path = "%s/%s".formatted(DEFAULT_PUBLIC_SPACE_PATH, repoItemName);
        String details = null;
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = null;
        ItemVersion version = null;

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse =
                retrieveRepositoryItemMetaData(testFileName, path, new HashMap<String, String>());

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = getHubClientAPIMock().catalog().getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION,
                JSON_PATH_MASON_CONTROLS_HREF, JSON_PATH_MASON_CONTROLS_METHOD);
        TestUtil.assertJSONProperties(response, knimeHubJSONResponse,
            expectedJsonPaths, getMapper(), getJsonPathConfig());
	}

	/**
	 * Tests that the response entities are deserialized into the expected repository
	 * item meta data where "full" details are requested.
	 *
	 * @param testFileName the provided JSON response
     * @param repoItemName the name of the repository item
	 * @throws IOException
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
        throws IOException {
        String path = "%s/%s".formatted(DEFAULT_PUBLIC_SPACE_PATH, repoItemName);
        String details = "full";
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = null;
        ItemVersion version = null;

        final var queryParams = new HashMap<String, String>();
        queryParams.put("details", details);

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse = retrieveRepositoryItemMetaData(testFileName, path, queryParams);

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = getHubClientAPIMock().catalog().getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION,
                JSON_PATH_MASON_CONTROLS_HREF, JSON_PATH_MASON_CONTROLS_METHOD, JSON_PATH_DETAILS_SPACE_ID);
        TestUtil.assertJSONProperties(response, knimeHubJSONResponse,
            expectedJsonPaths, getMapper(), getJsonPathConfig());
    }

	/**
	 * Tests that the response entities which have the children property
	 * are deserialized into the expected repository item meta data.
	 *
	 * @param testFileName the provided JSON response
     * @param repoItemName the name of the repository item
	 * @throws IOException
	 */
	@ParameterizedTest
    @CsvSource(value = {
        "workflowGroup.json,   WorkflowGroup",
        "space.json,           Space"
    })
	void testGetRepositoryItemMetaDataWithChildren(final String testFileName, final String repoItemName)
	        throws IOException {
	    String path = "%s/%s".formatted(DEFAULT_PUBLIC_SPACE_PATH, repoItemName);
        String details = null;
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = null;
        ItemVersion version = null;

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse =
                retrieveRepositoryItemMetaData(testFileName, path, new HashMap<String, String>());

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = getHubClientAPIMock().catalog().getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION, JSON_PATH_MASON_CONTROLS_HREF,
                JSON_PATH_MASON_CONTROLS_METHOD, JSON_PATH_CHILD_ITEM_PATH, JSON_PATH_CHILD_ITEM_ID,
                JSON_PATH_CHILD_ITEM_TYPE, JSON_PATH_CHILD_ITEM_OWNER, JSON_PATH_CHILD_ITEM_DESCRIPTION);
        TestUtil.assertJSONProperties(response, knimeHubJSONResponse,
            expectedJsonPaths, getMapper(), getJsonPathConfig());
	}

	/**
	 * Tests that the response entities of user groups for "children" contributer spaces
     * are deserialized into the expected repository item meta data.
	 *
	 * @throws IOException
	 */
	@Test
	void testGetRepositoryItemMetaDataWithContribSpaces() throws IOException {
        String path = "Users/%s".formatted(DEFAULT_USERNAME);
        String details = null;
        boolean deep = false;
        boolean spaceDetails = false;
        String contribSpaces = "children";
        ItemVersion version = null;

        final var queryParams = new HashMap<String, String>();
        queryParams.put("contribSpaces", contribSpaces);

        // Create the stub for the repository item metadata.
        final var knimeHubJSONResponse = retrieveRepositoryItemMetaData("userGroupWithContribSpaces.json",
        		path, queryParams);

        // Perform actual API call.
        ApiResponse<RepositoryItem> response = getHubClientAPIMock().catalog().getRepositoryItemByPath(
        		new Path(path), details, deep, spaceDetails, contribSpaces, version, null);

        // Assert required json paths.
        final var expectedJsonPaths = List.of(
                JSON_PATH_PATH, JSON_PATH_ID, JSON_PATH_TYPE,
                JSON_PATH_OWNER, JSON_PATH_DESCRIPTION, JSON_PATH_MASON_CONTROLS_HREF,
                JSON_PATH_MASON_CONTROLS_METHOD, JSON_PATH_CHILD_ITEM_PATH, JSON_PATH_CHILD_ITEM_ID,
                JSON_PATH_CHILD_ITEM_TYPE, JSON_PATH_CHILD_ITEM_OWNER, JSON_PATH_CHILD_ITEM_DESCRIPTION,
                JSON_PATH_CHILD_ITEM_MASON_CONTROLS_HREF, JSON_PATH_CHILD_ITEM_MASON_CONTROLS_METHOD);
        TestUtil.assertJSONProperties(response, knimeHubJSONResponse,
            expectedJsonPaths, getMapper(), getJsonPathConfig());
    }

    @AfterAll
	static void stopServerMock() {
        terminateServerMockTests();
	}

}
