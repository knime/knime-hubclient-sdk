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

package org.knime.hub.client.sdk.ent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.ent.catalog.Component;
import org.knime.hub.client.sdk.ent.catalog.Data;
import org.knime.hub.client.sdk.ent.catalog.DownloadStatus;
import org.knime.hub.client.sdk.ent.catalog.NamedItemVersionList;
import org.knime.hub.client.sdk.ent.catalog.PreparedDownload;
import org.knime.hub.client.sdk.ent.catalog.RepositoryItem;
import org.knime.hub.client.sdk.ent.catalog.Space;
import org.knime.hub.client.sdk.ent.catalog.UploadManifest;
import org.knime.hub.client.sdk.ent.catalog.UploadStarted;
import org.knime.hub.client.sdk.ent.catalog.UploadStatus;
import org.knime.hub.client.sdk.ent.catalog.Workflow;
import org.knime.hub.client.sdk.ent.catalog.WorkflowGroup;
import org.knime.hub.client.sdk.testing.TestUtil.EntityFolders;

/**
 * Tests creation via Jackson deserialization of the catalog service entities
 * in {@link org.knime.hub.client.sdk.ent.catalog}.
 *
 * This class is in another package on purpose to test from outside package scope.
 *
 * @author Jasper Krauter, KNIME GmbH, Konstanz, Germany
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
class CatalogServiceEntityTest extends AbstractTest {

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

    private static final String EXPECTED_COMPONENT_CANONICAL_PATH =
            "/Users/account:team:a885bb42-d808-4557-9a7f-9f10c5777739/Space/Component";
    private static final String EXPECTED_DATA_CANONICAL_PATH =
            "/Users/account:team:a885bb42-d808-4557-9a7f-9f10c5777739/Space/Data";
    private static final String EXPECTED_WORKFLOW_CANONICAL_PATH =
            "/Users/account:team:a885bb42-d808-4557-9a7f-9f10c5777739/Space/Workflow";
    private static final String EXPECTED_WORKFLOW_GROUP_CANONICAL_PATH =
            "/Users/account:team:a885bb42-d808-4557-9a7f-9f10c5777739/Space/WorkflowGroup";
    private static final String EXPECTED_SPACE_CANONICAL_PATH =
            "/Users/account:team:a885bb42-d808-4557-9a7f-9f10c5777739/Space";

    private static final String EXPECTED_OWNER = "jdoe";
    private static final String EXPECTED_OWNER_ACCOUNT_ID = "account:team:a885bb42-d808-4557-9a7f-9f10c5777739";
    private static final ZonedDateTime EXPECTED_CREATED_ON =
            ZonedDateTime.parse("2025-06-24T08:04:14+00:00").withZoneSameInstant(ZoneId.of("UTC"));
    private static final String EXPECTED_DESCRIPTION = "This is a description";
    private static final ZonedDateTime EXPECTED_LAST_UPLOADED_ON =
            ZonedDateTime.parse("2025-06-24T08:04:16+00:00").withZoneSameInstant(ZoneId.of("UTC"));
    private static final long EXPECTED_SIZE = 1337L;
    private static final long EXPECTED_LARGE_SIZE = 5000000000L;

    @Test
    void testCreateComponent() throws IOException {
        final var component = load(EntityFolders.CATALOG_ENTITES, "component.json", Component.class);
        assertEquals(RepositoryItem.RepositoryItemType.COMPONENT, component.getType(), "Unexpected type");
        assertEquals(EXPECTED_COMPONENT_PATH, component.getPath(), "Unexpected path");
        assertEquals(EXPECTED_COMPONENT_CANONICAL_PATH, component.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_COMPONENT_ID, component.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, component.getOwner(), "Unexpected owner");
        assertTrue(component.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID, component.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(component.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, component.getCreatedOn().get(), "Unexpected createdOn");
        assertEquals(EXPECTED_DESCRIPTION, component.getDescription().get(), "Unexpected description");
        assertTrue(component.getLastUploadedOn().isPresent(), "Expected lastUploadedOn");
        assertEquals(EXPECTED_LAST_UPLOADED_ON, component.getLastUploadedOn().get(), "Unexpected lastUploadedOn");
        assertTrue(component.getDetails().isEmpty(), "Unexpected details");
        assertFalse(component.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, component.getSize().orElseThrow(), "Unexpected size");

        final var componentWithDetails = load(EntityFolders.CATALOG_ENTITES,
            "componentWithDetails.json", Component.class);
        assertEquals(RepositoryItem.RepositoryItemType.COMPONENT, componentWithDetails.getType(), "Unexpected type");
        assertEquals(EXPECTED_COMPONENT_PATH, componentWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_COMPONENT_CANONICAL_PATH,
            componentWithDetails.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_COMPONENT_ID, componentWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, componentWithDetails.getOwner(), "Unexpected owner");
        assertTrue(componentWithDetails.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID,
            componentWithDetails.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(componentWithDetails.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, componentWithDetails.getCreatedOn().get(), "Unexpected createdOn");
        assertEquals(EXPECTED_DESCRIPTION, componentWithDetails.getDescription().get(), "Unexpected description");
        assertTrue(componentWithDetails.getLastUploadedOn().isPresent(), "Expected lastUploadedOn");
        assertEquals(EXPECTED_LAST_UPLOADED_ON, componentWithDetails.getLastUploadedOn().get(),
            "Unexpected lastUploadedOn");
        assertEquals(EXPECTED_SPACE_ID, componentWithDetails.getDetails().get().getSpace().getSpaceId(),
            "Unexpected space Id");
        assertFalse(componentWithDetails.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, componentWithDetails.getSize().orElseThrow(), "Unexpected size");
    }

    @Test
    void testCreateData() throws IOException {
        final var data = load(EntityFolders.CATALOG_ENTITES, "data.json", Data.class);
        assertEquals(RepositoryItem.RepositoryItemType.DATA, data.getType(), "Unexpected type");
        assertEquals(EXPECTED_DATA_PATH, data.getPath(), "Unexpected path");
        assertEquals(EXPECTED_DATA_CANONICAL_PATH, data.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_DATA_ID, data.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, data.getOwner(), "Unexpected owner");
        assertTrue(data.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID, data.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(data.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, data.getCreatedOn().get(), "Unexpected createdOn");
        assertTrue(data.getDescription().isEmpty(), "Unexpected description");
        assertTrue(data.getLastUploadedOn().isPresent(), "Expected lastUploadedOn");
        assertEquals(EXPECTED_LAST_UPLOADED_ON, data.getLastUploadedOn().get(), "Unexpected lastUploadedOn");
        assertTrue(data.getDetails().isEmpty(), "Unexpected details");
        assertFalse(data.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, data.getSize().orElseThrow(), "Unexpected size");

        final var dataWithDetails = load(EntityFolders.CATALOG_ENTITES, "dataWithDetails.json", Data.class);
        assertEquals(RepositoryItem.RepositoryItemType.DATA, data.getType(), "Unexpected type");
        assertEquals(EXPECTED_DATA_PATH, dataWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_DATA_CANONICAL_PATH, dataWithDetails.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_DATA_ID, dataWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, dataWithDetails.getOwner(), "Unexpected owner");
        assertTrue(dataWithDetails.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID,
            dataWithDetails.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(dataWithDetails.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, dataWithDetails.getCreatedOn().get(), "Unexpected createdOn");
        assertTrue(dataWithDetails.getDescription().isEmpty(), "Unexpected description");
        assertTrue(dataWithDetails.getLastUploadedOn().isPresent(), "Expected lastUploadedOn");
        assertEquals(EXPECTED_LAST_UPLOADED_ON, dataWithDetails.getLastUploadedOn().get(),
            "Unexpected lastUploadedOn");
        assertEquals(EXPECTED_SPACE_ID,
                dataWithDetails.getDetails().get().getSpace().getSpaceId(), "Unexpected space Id");
        assertEquals(EXPECTED_SIZE, dataWithDetails.getSize().orElseThrow(), "Unexpected size");
    }

    @Test
    void testCreateWorkflow() throws IOException {
        final var workflow = load(EntityFolders.CATALOG_ENTITES, "workflow.json", Workflow.class);
        assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW, workflow.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_PATH, workflow.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_CANONICAL_PATH, workflow.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_WORKFLOW_ID, workflow.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflow.getOwner(), "Unexpected owner");
        assertTrue(workflow.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID, workflow.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(workflow.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, workflow.getCreatedOn().get(), "Unexpected createdOn");
        assertEquals(EXPECTED_DESCRIPTION, workflow.getDescription().get(), "Unexpected description");
        assertTrue(workflow.getLastUploadedOn().isPresent(), "Expected lastUploadedOn");
        assertEquals(EXPECTED_LAST_UPLOADED_ON, workflow.getLastUploadedOn().get(), "Unexpected lastUploadedOn");
        assertTrue(workflow.getDetails().isEmpty(), "Unexpected details");
        assertFalse(workflow.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, workflow.getSize().orElseThrow(), "Unexpected size");

        final var workflowWithDetails = load(EntityFolders.CATALOG_ENTITES, "workflowWithDetails.json", Workflow.class);
        assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW, workflowWithDetails.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_PATH, workflowWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_CANONICAL_PATH,
            workflowWithDetails.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_WORKFLOW_ID, workflowWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflowWithDetails.getOwner(), "Unexpected owner");
        assertTrue(workflowWithDetails.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID,
            workflowWithDetails.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(workflowWithDetails.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, workflowWithDetails.getCreatedOn().get(), "Unexpected createdOn");
        assertEquals(EXPECTED_DESCRIPTION, workflowWithDetails.getDescription().get(), "Unexpected description");
        assertTrue(workflowWithDetails.getLastUploadedOn().isPresent(), "Expected lastUploadedOn");
        assertEquals(EXPECTED_LAST_UPLOADED_ON, workflowWithDetails.getLastUploadedOn().get(),
            "Unexpected lastUploadedOn");
        assertEquals(EXPECTED_SPACE_ID, workflowWithDetails.getDetails().get().getSpace().getSpaceId(),
            "Unexpected space Id");
        assertFalse(workflowWithDetails.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertEquals(EXPECTED_SIZE, workflowWithDetails.getSize().orElseThrow(), "Unexpected size");
    }

    @Test
    void testCreateLargeSizedItems() throws IOException {
        final var largeComponent = load(EntityFolders.CATALOG_ENTITES, "largeComponent.json", Component.class);
        assertEquals(EXPECTED_LARGE_SIZE, largeComponent.getSize().orElseThrow(), "Unexpected size");
        final var largeData = load(EntityFolders.CATALOG_ENTITES, "largeData.json", Data.class);
        assertEquals(EXPECTED_LARGE_SIZE, largeData.getSize().orElseThrow(), "Unexpected size");
        final var largeWorkflow = load(EntityFolders.CATALOG_ENTITES, "largeWorkflow.json", Workflow.class);
        assertEquals(EXPECTED_LARGE_SIZE, largeWorkflow.getSize().orElseThrow(), "Unexpected size");
    }

    @Test
    void testCreateWorkflowGroup() throws IOException {
        final var workflowGroup = load(EntityFolders.CATALOG_ENTITES, "workflowGroup.json", WorkflowGroup.class);
        assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW_GROUP, workflowGroup.getType(), "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_GROUP_PATH, workflowGroup.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_GROUP_CANONICAL_PATH,
            workflowGroup.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_WORKFLOW_GROUP_ID, workflowGroup.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflowGroup.getOwner(), "Unexpected owner");
        assertTrue(workflowGroup.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID, workflowGroup.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(workflowGroup.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, workflowGroup.getCreatedOn().get(), "Unexpected createdOn");
        assertTrue(workflowGroup.getDescription().isEmpty(), "Unexpected description");
        assertTrue(workflowGroup.getLastUploadedOn().isEmpty(), "Unexpected lastUploadedOn");
        assertTrue(workflowGroup.getDetails().isEmpty(), "Unexpected details");
        assertFalse(workflowGroup.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertFalse(workflowGroup.getChildren().isEmpty(), "Children are missing");

        final var workflowGroupWithDetails = load(EntityFolders.CATALOG_ENTITES,
            "workflowGroupWithDetails.json", WorkflowGroup.class);
        assertEquals(RepositoryItem.RepositoryItemType.WORKFLOW_GROUP, workflowGroupWithDetails.getType(),
            "Unexpected type");
        assertEquals(EXPECTED_WORKFLOW_GROUP_PATH, workflowGroupWithDetails.getPath(), "Unexpected path");
        assertEquals(EXPECTED_WORKFLOW_GROUP_CANONICAL_PATH,
            workflowGroupWithDetails.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_WORKFLOW_GROUP_ID, workflowGroupWithDetails.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, workflowGroupWithDetails.getOwner(), "Unexpected owner");
        assertTrue(workflowGroupWithDetails.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID,
            workflowGroupWithDetails.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(workflowGroupWithDetails.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, workflowGroupWithDetails.getCreatedOn().get(), "Unexpected createdOn");
        assertTrue(workflowGroupWithDetails.getDescription().isEmpty(), "Unexpected description");
        assertTrue(workflowGroupWithDetails.getLastUploadedOn().isEmpty(), "Unexpected lastUploadedOn");
        assertEquals(EXPECTED_SPACE_ID, workflowGroupWithDetails.getDetails().get().getSpace().getSpaceId(),
            "Unexpected space Id");
        assertFalse(workflowGroupWithDetails.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertFalse(workflowGroupWithDetails.getChildren().isEmpty(), "Children are missing");
    }

    @Test
    void testCreateSpace() throws IOException {
        final var space = load(EntityFolders.CATALOG_ENTITES, "space.json", Space.class);
        assertEquals(RepositoryItem.RepositoryItemType.SPACE, space.getType(), "Unexpected type");
        assertEquals(EXPECTED_SPACE_PATH, space.getPath(), "Unexpected path");
        assertEquals(EXPECTED_SPACE_CANONICAL_PATH, space.getCanonicalPath(), "Unexpected canonical path");
        assertEquals(EXPECTED_SPACE_ID, space.getId(), "Unexpected id");
        assertEquals(EXPECTED_OWNER, space.getOwner(), "Unexpected owner");
        assertTrue(space.getOwnerAccountId().isPresent(), "Expected owner account ID");
        assertEquals(EXPECTED_OWNER_ACCOUNT_ID, space.getOwnerAccountId().get(), "Unexpected owner account ID");
        assertTrue(space.getCreatedOn().isPresent(), "Expected createdOn");
        assertEquals(EXPECTED_CREATED_ON, space.getCreatedOn().get(), "Unexpected createdOn");
        assertEquals(EXPECTED_DESCRIPTION, space.getDescription().get(), "Unexpected description");
        assertTrue(space.getLastUploadedOn().isEmpty(), "Unexpected lastUploadedOn");
        assertEquals(EXPECTED_DESCRIPTION, space.getDescription().get(), "Unexpected description");
        assertTrue(space.getDetails().isEmpty(), "Unexpected details");
        assertFalse(space.getMasonControls().isEmpty(), "Mason Controls do not exist");
        assertFalse(space.getChildren().isEmpty(), "Children are missing");
    }

    @Test
    void testCreateUploadManifest() throws IOException {
        final var uploadManifest = load(EntityFolders.CATALOG_ENTITES, "uploadManifest.json", UploadManifest.class);
        final var items = uploadManifest.getItems();
        assertEquals(3, items.size(), "Unexpected number of items");
        assertTrue(items.containsKey("/my-workflow"), "Expected key");
        assertTrue(items.containsKey("/my-group/my-data-file"), "Expected key");
        assertTrue(items.containsKey("/my-group/my-empty-group"), "Expected key");

        final var itemUploadRequest1 = items.get("/my-workflow");
        assertEquals("application/vnd.knime.workflow+zip", itemUploadRequest1.getItemContentType(),
            "Unexpected item content type");
        assertNull(itemUploadRequest1.getInitialPartCount(), "Unexpected initial part count");
        final var itemUploadRequest2 = items.get("/my-group/my-data-file");
        assertEquals("text/plain", itemUploadRequest2.getItemContentType(), "Unexpected item content type");
        assertEquals(3, itemUploadRequest2.getInitialPartCount(), "Unexpected part count");
        final var itemUploadRequest3 = items.get("/my-group/my-empty-group");
        assertEquals("application/vnd.knime.workflow-group", itemUploadRequest3.getItemContentType(),
            "Unexpected item content type");
        assertNull(itemUploadRequest3.getInitialPartCount(), "Unexpected initial part count");
    }

    @Test
    void testCreateUploadStarted() throws IOException {
        final var uploadStarted = load(EntityFolders.CATALOG_ENTITES, "uploadStarted.json", UploadStarted.class);
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
        assertEquals(3, uploadParts2.size(), "Unexpected number of upload parts");
        assertTrue(uploadParts2.containsKey(1), "Expected key");
        assertTrue(uploadParts2.containsKey(2), "Expected key");
        assertTrue(uploadParts2.containsKey(3), "Expected key");
        final var expectedHeaders = new HashMap<String, List<String>>();
        expectedHeaders.put("Host", List.of("example.com"));
        final var uploadTargetPart21 = uploadParts2.get(1);
        assertEquals("PUT", uploadTargetPart21.getMethod(), "Unexpected method");
        assertEquals(new URL("https://example.com/foo?X-Amz-Algorithm=..."), uploadTargetPart21.getUrl(),
            "Unexpected url");
        assertEquals(expectedHeaders, uploadTargetPart21.getHeader(), "Unexpected header");
        final var uploadTargetPart22 = uploadParts2.get(2);
        assertEquals("PUT", uploadTargetPart22.getMethod(), "Unexpected method");
        assertEquals(new URL("https://example.com/foo?X-Amz-Algorithm=..."), uploadTargetPart22.getUrl(),
            "Unexpected url");
        assertEquals(expectedHeaders, uploadTargetPart22.getHeader(), "Unexpected header");
        final var uploadTargetPart23 = uploadParts2.get(3);
        assertEquals("PUT", uploadTargetPart23.getMethod(), "Unexpected method");
        assertEquals(new URL("https://example.com/foo?X-Amz-Algorithm=..."), uploadTargetPart23.getUrl(),
            "Unexpected url");
        assertEquals(expectedHeaders, uploadTargetPart23.getHeader(), "Unexpected header");

        final var itemUploadInstructions3 = items.get("/my-group/my-empty-group");
        assertNull(itemUploadInstructions3.getUploadId(), "Unexpected upload ID");
        assertTrue(itemUploadInstructions3.getParts().isEmpty(), "Unexpected upload parts");
    }

    @Test
    void testCreateUploadStatus() throws IOException {
        final var uploadStatus = load(EntityFolders.CATALOG_ENTITES, "uploadStatus.json", UploadStatus.class);
        assertEquals("d63ec9e1-24a7-4015-82b4-476ca4f4a57b~7fbeb904-2272-4dd7-b2e1-d3f49b8373a0",
            uploadStatus.getUploadId(), "Unexpected upload ID");
        assertEquals("account:user:c1df863a-0410-4b27-97fc-5ceb3a515176", uploadStatus.getInitiatorAccountId(),
            "Unexpected initiator account ID");
        assertEquals("PREPARED", uploadStatus.getStatus().toString(), "Unexpected status");
        assertEquals("Waiting for item to be fully uploaded.", uploadStatus.getStatusMessage(),
            "Unexpected status message");
        assertEquals(Instant.parse("2023-08-22T09:11:02+00:00"), uploadStatus.getLastUpdated(),
            "Unexpected last updated");
        assertEquals("/Users/account:user:c1df863a-0410-4b27-97fc-5ceb3a515176/my-space/my-workflow",
            uploadStatus.getTargetCanonicalPath(), "Unexpected target canonical path");
    }

    @Test
    void testPreparedDownloadCreation() throws IOException {
        final var preparedDownloadWithId =
                load(EntityFolders.CATALOG_ENTITES, "preparedDownloadWithId.json", PreparedDownload.class);
        var downloadIdOpt = preparedDownloadWithId.getDownloadId();
        var downloadUrlOpt = preparedDownloadWithId.getDownloadUrl();
        assertTrue(downloadIdOpt.isPresent(), "Expected download Id");
        assertTrue(downloadUrlOpt.isEmpty(), "Unexpected download url");
        assertEquals("f9226530-f9e6-4e1c-9944-82baf1d43cb3", downloadIdOpt.get(), "Unexpected download ID");

        final var preparedDownloadWithUrl =
                load(EntityFolders.CATALOG_ENTITES, "preparedDownloadWithURL.json", PreparedDownload.class);
        downloadIdOpt = preparedDownloadWithUrl.getDownloadId();
        downloadUrlOpt = preparedDownloadWithUrl.getDownloadUrl();
        assertTrue(downloadIdOpt.isEmpty(), "Unexpected download Id");
        assertTrue(downloadUrlOpt.isPresent(), "Expected download url");
        assertEquals("https://s3.us-east-1.amazonaws.com/", downloadUrlOpt.get().toString(), "Unexpected download URL");
    }

    @Test
    void testDownloadStatusCreation() throws IOException {
        final var downloadStatus = load(EntityFolders.CATALOG_ENTITES, "downloadStatus.json", DownloadStatus.class);
        assertEquals("f9226530-f9e6-4e1c-9944-82baf1d43cb3",
                downloadStatus.getDownloadId(), "Unexpected download ID");
        assertEquals(DownloadStatus.StatusEnum.READY,
            downloadStatus.getStatus(), "Unexpected status");
        assertEquals("Download is ready.",
            downloadStatus.getStatusMessage(), "Unexpected status message");
        assertEquals(Instant.parse("2025-04-29T10:03:16+00:00"),
            downloadStatus.getLastUpdated(), "Unexpected last updated");
        assertTrue(downloadStatus.getDownloadUrl().isPresent(), "Expected download url");
        assertEquals("https://s3.us-east-1.amazonaws.com/",
            downloadStatus.getDownloadUrl().get().toString(), "Unexpected download url");
    }

    @Test
    void testNamedItemVersionsListCreation() throws IOException {
        var namedItemVersionsList =
                load(EntityFolders.CATALOG_ENTITES, "emptyNamedItemVersionsList.json", NamedItemVersionList.class);
        assertEquals(List.of(), namedItemVersionsList.getVersions(), "Unexpected versions");
        assertEquals(0, namedItemVersionsList.getTotalCount(), "Unexpected total count");

        namedItemVersionsList =
                load(EntityFolders.CATALOG_ENTITES, "nonEmptyNamedItemVersionsList.json", NamedItemVersionList.class);
        assertEquals(2, namedItemVersionsList.getTotalCount(), "Unexpected total count");
        final var versions = namedItemVersionsList.getVersions();
        assertEquals(2, versions.size(), "Unexpected number of named item versions");
        var version = versions.get(0);
        assertEquals(2, version.getVersion(), "Unexpected version");
        assertEquals("2", version.getTitle(), "Unexpected title");
        assertEquals("This is a description", version.getDescription().get(), "Unexpected description");
        assertEquals("jdoe", version.getAuthor(), "Unexpected author");
        assertEquals("account:team:a885bb42-d808-4557-9a7f-9f10c5777739", version.getAuthorAccountId(),
            "Unexpected authorAccountId");
        assertEquals(Instant.parse("2025-06-23T08:55:42+00:00"), version.getCreatedOn(), "Unexpected createdOn");
        version = versions.get(1);
        assertEquals(1, version.getVersion(), "Unexpected version");
        assertEquals("1", version.getTitle(), "Unexpected title");
        assertEquals("", version.getDescription().get(), "Unexpected description");
        assertEquals("jdoe", version.getAuthor(), "Unexpected author");
        assertEquals("account:team:a885bb42-d808-4557-9a7f-9f10c5777739", version.getAuthorAccountId(),
            "Unexpected authorAccountId");
        assertEquals(Instant.parse("2025-06-23T08:55:16+00:00"), version.getCreatedOn(), "Unexpected createdOn");
    }

}
