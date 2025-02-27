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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.ent.Component;
import org.knime.hub.client.sdk.ent.Data;
import org.knime.hub.client.sdk.ent.RepositoryItem;
import org.knime.hub.client.sdk.ent.Space;
import org.knime.hub.client.sdk.ent.UploadManifest;
import org.knime.hub.client.sdk.ent.UploadStarted;
import org.knime.hub.client.sdk.ent.UploadStatus;
import org.knime.hub.client.sdk.ent.Workflow;
import org.knime.hub.client.sdk.ent.WorkflowGroup;
import org.knime.hub.client.sdk.testing.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests creation via Jackson deserialization of the entities in
 * {@link org.knime.hub.client.sdk.ent}.
 *
 * This class is in another package on purpose to test from outside package
 * scope.
 *
 * @author Jasper Krauter, KNIME GmbH, Konstanz, Germany
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
class EntityCreationTest {

    /** Expected values from JSON files */
    private static final String EXPECTED_COMPONENT_ID = "*iMLnw_ejyqC_DiiE";
    private static final String EXPECTED_DATA_ID = "*QSvc9nCKIaDzLI7z";
    private static final String EXPECTED_WORKFLOW_ID = "*Kl06C8NRxUPriipo";
    private static final String EXPECTED_WORKFLOW_GROUP_ID = "*CF2J49sBBfZBibC8";
    private static final String EXPECTED_SPACE_ID = "*mTRjZD2LAbPevhGj";

    private static final String EXPECTED_COMPONENT_PATH = "/Users/jdoe/Space/Component";
    private static final String EXPECTED_DATA_PATH = "/Users/jdoe/Space/Data";
    private static final String EXPECTED_WORKFLOW_PATH = "/Users/jdoe/Space/Workflow";
    private static final String EXPECTED_WORKFLOW_GROUP_PATH = "/Users/jdoe/Space/WorkflowGroup";
    private static final String EXPECTED_SPACE_PATH = "/Users/jdoe/Space";

    private static final String EXPECTED_OWNER = "jdoe";
    private static final String EXPECTED_DESCRIPTION = "This is a description";
    private static final long EXPECTED_SIZE = 1337L;
    private static final long EXPECTED_LARGE_SIZE = 5000000000L;

    private static final ObjectMapper MAPPER = new ApiClient(null, null, Duration.ofSeconds(0),
	        Duration.ofSeconds(0)).getObjectMapper();

	private static <T> T load(final String filename, final Class<T> clazz) throws IOException, URISyntaxException {
		// Path to the file inside test file folder.
	    final var filePath = IPath.forPosix(HubClientAPITest.RESOURCE_FOLDER_NAME)
	            .append(HubClientAPITest.TEST_FILE_FOLDER_NAME).append(filename);

		// Obtain path object from bundle activator class.
		final var path = TestUtil.resolvePath(filePath);
		return MAPPER.readValue(Files.readString(path), clazz);
	}

	@Test
	void testCreateComponent() throws IOException, URISyntaxException {
		final var component = load("component.json", Component.class);
		assertEquals(RepositoryItem.RepositoryItemType.COMPONENT, component.getType(), "Unexpected type");
		assertEquals(EXPECTED_COMPONENT_PATH, component.getPath(), "Unexpected path");
		assertEquals(EXPECTED_COMPONENT_ID, component.getId(), "Unexpected id");
		assertEquals(EXPECTED_OWNER, component.getOwner(), "Unexpected owner");
		assertEquals(EXPECTED_DESCRIPTION, component.getDescription().get(), "Unexpected description");
		assertTrue(component.getDetails().isEmpty(), "Unexpected details");
		assertFalse(component.getMasonControls().isEmpty(), "Mason Controls do not exist");
		assertEquals(EXPECTED_SIZE, component.getSize(), "Unexpected size");

        final var componentWithDetails = load("componentWithDetails.json", Component.class);
        assertEquals(RepositoryItem.RepositoryItemType.COMPONENT, componentWithDetails.getType(), "Unexpected type");
        assertEquals(EXPECTED_COMPONENT_PATH, componentWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_COMPONENT_ID, componentWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, componentWithDetails.getOwner(), "Unexpected owner");
        assertEquals(EXPECTED_DESCRIPTION, componentWithDetails.getDescription().get(), "Unexpected description");
        assertEquals(EXPECTED_SPACE_ID,
                componentWithDetails.getDetails().get().getSpace().getSpaceId(), "Unexpected space Id");
        assertFalse(componentWithDetails.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, componentWithDetails.getSize(), "Unexpected size");
	}

	@Test
	void testCreateData() throws IOException, URISyntaxException {
		final var data = load("data.json", Data.class);
		assertEquals(RepositoryItem.RepositoryItemType.DATA, data.getType(), "Unexpected type");
        assertEquals(EXPECTED_DATA_PATH, data.getPath(), "Unexpected path");
        assertEquals(EXPECTED_DATA_ID, data.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, data.getOwner(), "Unexpected owner");
        assertTrue(data.getDescription().isEmpty(), "Unexpected description");
        assertTrue(data.getDetails().isEmpty(), "Unexpected details");
        assertFalse(data.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, data.getSize(), "Unexpected size");

		final var dataWithDetails = load("dataWithDetails.json", Data.class);
		assertEquals(RepositoryItem.RepositoryItemType.DATA, data.getType(), "Unexpected type");
        assertEquals(EXPECTED_DATA_PATH, data.getPath(), "Unexpected path");
        assertEquals(EXPECTED_DATA_ID, data.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, data.getOwner(), "Unexpected owner");
        assertTrue(data.getDescription().isEmpty(), "Unexpected description");
        assertTrue(data.getDetails().isEmpty(), "Unexpected details");
        assertEquals(EXPECTED_SPACE_ID,
                dataWithDetails.getDetails().get().getSpace().getSpaceId(), "Unexpected space Id");
        assertEquals(EXPECTED_SIZE, data.getSize(), "Unexpected size");
	}

	@Test
	void testCreateWorkflow() throws IOException, URISyntaxException {
        final var workflow = load("workflow.json", Workflow.class);
        assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW, workflow.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_PATH, workflow.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_ID, workflow.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflow.getOwner(), "Unexpected owner");
        assertEquals(EXPECTED_DESCRIPTION, workflow.getDescription().get(), "Unexpected description");
        assertTrue(workflow.getDetails().isEmpty(), "Unexpected details");
        assertFalse(workflow.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, workflow.getSize(), "Unexpected size");

		final var workflowWithDetails = load("workflowWithDetails.json", Workflow.class);
		assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW, workflowWithDetails.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_PATH, workflowWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_ID, workflowWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflowWithDetails.getOwner(), "Unexpected owner");
        assertEquals(EXPECTED_DESCRIPTION, workflowWithDetails.getDescription().get(), "Unexpected description");
        assertEquals(EXPECTED_SPACE_ID,
                workflowWithDetails.getDetails().get().getSpace().getSpaceId(), "Unexpected space Id");
        assertFalse(workflowWithDetails.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, workflowWithDetails.getSize(), "Unexpected size");
	}

	@Test
    void testCreateLargeSizedItems() throws IOException, URISyntaxException {
        final var largeComponent = load("largeComponent.json", Component.class);
        assertEquals(EXPECTED_LARGE_SIZE, largeComponent.getSize(), "Unexpected size");
        final var largeData = load("largeData.json", Data.class);
        assertEquals(EXPECTED_LARGE_SIZE, largeData.getSize(), "Unexpected size");
        final var largeWorkflow = load("largeWorkflow.json", Workflow.class);
        assertEquals(EXPECTED_LARGE_SIZE, largeWorkflow.getSize(), "Unexpected size");
    }

	@Test
	void testCreateWorkflowGroup() throws IOException, URISyntaxException {
		final var workflowGroup = load("workflowGroup.json", WorkflowGroup.class);
		assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW_GROUP, workflowGroup.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_GROUP_PATH, workflowGroup.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_GROUP_ID, workflowGroup.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflowGroup.getOwner(), "Unexpected owner");
        assertTrue(workflowGroup.getDescription().isEmpty(), "Unexpected description");
        assertTrue(workflowGroup.getDetails().isEmpty(), "Unexpected details");
        assertFalse(workflowGroup.getMasonControls().isEmpty(), "Mason Controls do not exist");
		assertFalse(workflowGroup.getChildren().isEmpty(), "Children are missing");

		final var workflowGroupWithDetails = load("workflowGroupWithDetails.json", WorkflowGroup.class);
        assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW_GROUP,
                workflowGroupWithDetails.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_GROUP_PATH, workflowGroupWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_GROUP_ID, workflowGroupWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflowGroupWithDetails.getOwner(), "Unexpected owner");
        assertTrue(workflowGroupWithDetails.getDescription().isEmpty(), "Unexpected description");
        assertEquals(EXPECTED_SPACE_ID,
                workflowGroupWithDetails.getDetails().get().getSpace().getSpaceId(), "Unexpected space Id");
        assertFalse(workflowGroupWithDetails.getMasonControls().isEmpty(), "Mason Controls do not exist");
		assertFalse(workflowGroupWithDetails.getChildren().isEmpty(), "Children are missing");
	}

	@Test
	void testCreateSpace() throws IOException, URISyntaxException {
		final var space = load("space.json", Space.class);
        assertEquals(RepositoryItem.RepositoryItemType.SPACE, space.getType(), "Unexpected type");
        assertEquals(EXPECTED_SPACE_PATH, space.getPath(), "Unexpected path");
        assertEquals(EXPECTED_SPACE_ID, space.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, space.getOwner(), "Unexpected owner");
        assertEquals(EXPECTED_DESCRIPTION, space.getDescription().get(), "Unexpected description");
        assertTrue(space.getDetails().isEmpty(), "Unexpected details");
        assertFalse(space.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertFalse(space.getChildren().isEmpty(), "Children are missing");
	}

	@Test
	void testCreateUploadManifest() throws IOException, URISyntaxException {
	    final var uploadManifest = load("uploadManifest.json", UploadManifest.class);
	    final var items = uploadManifest.getItems();
	    assertEquals(3, items.size(), "Unexpected number of items");
	    assertTrue(items.containsKey("/my-workflow"), "Expected key");
	    assertTrue(items.containsKey("/my-group/my-data-file"), "Expected key");
	    assertTrue(items.containsKey("/my-group/my-empty-group"), "Expected key");

	    final var itemUploadRequest1 = items.get("/my-workflow");
	    assertEquals("application/vnd.knime.workflow+zip",
	            itemUploadRequest1.getItemContentType(), "Unexpected item content type");
	    assertNull(itemUploadRequest1.getInitialPartCount(), "Unexpected initial part count");
	    final var itemUploadRequest2 = items.get("/my-group/my-data-file");
	    assertEquals("text/plain", itemUploadRequest2.getItemContentType(), "Unexpected item content type");
	    assertEquals(3,  itemUploadRequest2.getInitialPartCount(), "Unexpected part count");
	    final var itemUploadRequest3 = items.get("/my-group/my-empty-group");
	    assertEquals("application/vnd.knime.workflow-group",
	            itemUploadRequest3.getItemContentType(), "Unexpected item content type");
	    assertNull(itemUploadRequest3.getInitialPartCount(), "Unexpected initial part count");
	}

	@Test
	void testCreateUploadStarted() throws IOException, URISyntaxException {
	    final var uploadStarted = load("uploadStarted.json", UploadStarted.class);
	    final var items = uploadStarted.getItems();
	    assertEquals(3, items.size(), "Unexpected number of items");

        assertTrue(items.containsKey("/my-workflow"), "Expected key");
        assertTrue(items.containsKey("/my-group/my-data-file"), "Expected key");
        assertTrue(items.containsKey("/my-group/my-empty-group"), "Expected key");

        final var itemUploadInstructions1 = items.get("/my-workflow");
        assertEquals("d63ec9e1-24a7-4015-82b4-476ca4f4a57b~7fbeb904-2272-4dd7-b2e1-d3f49b8373a0",
                itemUploadInstructions1.getUploadId(), "Unexpected upload ID");
        final var uploadParts1 = itemUploadInstructions1.getParts();
        assertTrue(uploadParts1.isEmpty(), "Unexpected upload parts");

        final var itemUploadInstructions2 = items.get("/my-group/my-data-file");
        assertEquals("7227362d-c1ae-452c-a53c-cf0d609caef4~874d4805-f51f-4c83-81c1-10919e3dee48",
                itemUploadInstructions2.getUploadId(), "Unexpected upload ID");
        final var uploadParts2 = itemUploadInstructions2.getParts().get();
        assertEquals(3,  uploadParts2.size(), "Unexpected number of upload parts");
        assertTrue(uploadParts2.containsKey(1), "Expected key");
        assertTrue(uploadParts2.containsKey(2), "Expected key");
        assertTrue(uploadParts2.containsKey(3), "Expected key");
        final var expectedHeaders = new HashMap<String, List<String>>();
        expectedHeaders.put("Host", List.of("example.com"));
        final var uploadTargetPart21 = uploadParts2.get(1);
        assertEquals("PUT", uploadTargetPart21.getMethod(), "Unexpected method");
        assertEquals(new URL("https://example.com/foo?X-Amz-Algorithm=..."),
                uploadTargetPart21.getUrl(), "Unexpected url");
        assertEquals(expectedHeaders, uploadTargetPart21.getHeader(), "Unexpected header");
        final var uploadTargetPart22 = uploadParts2.get(2);
        assertEquals("PUT", uploadTargetPart22.getMethod(), "Unexpected method");
        assertEquals(new URL("https://example.com/foo?X-Amz-Algorithm=..."),
                uploadTargetPart22.getUrl(), "Unexpected url");
        assertEquals(expectedHeaders, uploadTargetPart22.getHeader(), "Unexpected header");
        final var uploadTargetPart23 = uploadParts2.get(3);
        assertEquals("PUT", uploadTargetPart23.getMethod(), "Unexpected method");
        assertEquals(new URL("https://example.com/foo?X-Amz-Algorithm=..."),
                uploadTargetPart23.getUrl(), "Unexpected url");
        assertEquals(expectedHeaders, uploadTargetPart23.getHeader(), "Unexpected header");

        final var itemUploadInstructions3 = items.get("/my-group/my-empty-group");
        assertNull(itemUploadInstructions3.getUploadId(), "Unexpected upload ID");
        assertTrue(itemUploadInstructions3.getParts().isEmpty(), "Unexpected upload parts");
	}

	@Test
	void testCreateUploadStatus() throws IOException, URISyntaxException {
	    final var uploadStatus = load("uploadStatus.json", UploadStatus.class);
	    assertEquals("d63ec9e1-24a7-4015-82b4-476ca4f4a57b~7fbeb904-2272-4dd7-b2e1-d3f49b8373a0",
	            uploadStatus.getUploadId(), "Unexpected upload ID");
	    assertEquals("account:user:c1df863a-0410-4b27-97fc-5ceb3a515176",
	            uploadStatus.getInitiatorAccountId(), "Unexpected initiator account ID");
	    assertEquals("PREPARED", uploadStatus.getStatus().toString(), "Unexpected status");
	    assertEquals("Waiting for item to be fully uploaded.",
	            uploadStatus.getStatusMessage(), "Unexpected status message");
	    assertEquals(Instant.parse("2023-08-22T09:11:02+00:00"),
	            uploadStatus.getLastUpdated(), "Unexpected last updated");
	    assertEquals("/Users/account:user:c1df863a-0410-4b27-97fc-5ceb3a515176/my-space/my-workflow",
	            uploadStatus.getTargetCanonicalPath(), "Unexpected target canonicla path");
	}

}
