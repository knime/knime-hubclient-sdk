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
 *   Jun 27, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.ent.execution.DataAppDeployment;
import org.knime.hub.client.sdk.ent.execution.Deployment;
import org.knime.hub.client.sdk.ent.execution.DeploymentList;
import org.knime.hub.client.sdk.ent.execution.ExecutionContextList;
import org.knime.hub.client.sdk.ent.execution.RestDeployment;
import org.knime.hub.client.sdk.ent.execution.ScheduleDeployment;
import org.knime.hub.client.sdk.ent.execution.TriggerDeployment;
import org.knime.hub.client.sdk.testing.TestUtil.EntityFolders;

/**
 * Tests creation via Jackson deserialization of the execution service entities
 * in {@link org.knime.hub.client.sdk.ent.execution}.
 *
 * This class is in another package on purpose to test from outside package scope.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
class ExecutionServiceEntityTest extends AbstractTest {

    @Test
    void testCreateDeploymentList() throws IOException {
        final var deploymentList = load(EntityFolders.EXECUTION_ENTITIES, "deploymentList.json", DeploymentList.class);

        assertTrue(deploymentList.getPagination().isPresent(), "Expected pagination");
        assertEquals(111, deploymentList.getPagination().get().getTotalItemCount(), "Unexpected total item count");
        assertEquals(14, deploymentList.getPagination().get().getTotalPageCount(), "Unexpected total page count");
        assertFalse(deploymentList.getControls().isEmpty(), "Expected controls");
        assertTrue(deploymentList.getControls().containsKey("self"), "Expected 'self' control");
        assertEquals("https://api.business-hubdev.cloudops.knime.com/deployments/" +
                "account:team:e3133141-8fb3-4d4a-99cc-70519a90149b?pagelen=8",
                deploymentList.getControls().get("self").getHref(), "Expected 'self' control href");
        assertEquals("GET", deploymentList.getControls().get("self").getMethod(), "Expected 'self' control method");

        final var deployments = deploymentList.getDeployments();
        assertEquals(4, deployments.size(), "Unexpected number of deployments");

        for (var deployment : deployments) {
            switch (deployment.getType()) {
                case DATA_APP:
                    assertDeploymentType(DataAppDeployment.class, deployment, new DeploymentInfo(
                        "data-app:31f07f4f-75e9-4ea4-8b52-1547328d790b",
                        "Service Caller (Assign Labels; user scoped)",
                        "https://api.business-hubdev.cloudops.knime.com/deployments/" +
                        "data-app:31f07f4f-75e9-4ea4-8b52-1547328d790b"));
                    break;
                case REST:
                    assertDeploymentType(RestDeployment.class, deployment,
                        new DeploymentInfo("rest:d7dc336a-a0db-464c-aaae-cbdf29fc8f53",
                            "Notify User Service",
                            "https://api.business-hubdev.cloudops.knime.com/deployments/"
                                + "rest:d7dc336a-a0db-464c-aaae-cbdf29fc8f53"));
                    break;
                case SCHEDULE:
                    assertDeploymentType(ScheduleDeployment.class, deployment,
                        new DeploymentInfo("schedule:fff92b21-441d-4ed3-868f-ce61f5fb8369",
                            "[Siemens] Collect Usage Metrics",
                            "https://api.business-hubdev.cloudops.knime.com/deployments/"
                                + "schedule:fff92b21-441d-4ed3-868f-ce61f5fb8369"));
                    break;
                case TRIGGER:
                    assertDeploymentType(TriggerDeployment.class, deployment,
                        new DeploymentInfo("trigger:582f19c1-0135-4ef1-b1f8-2bce87bf7d6e",
                            "Workflow Labeling Assistant",
                            "https://api.business-hubdev.cloudops.knime.com/deployments/"
                                + "trigger:582f19c1-0135-4ef1-b1f8-2bce87bf7d6e"));
                    break;
                default:
                    throw new IllegalStateException("Unexpected deployment type: " + deployment.getType());
            }
        }
    }

    @Test
    void testCreateExecutionContextList() throws IOException {
        final var executionContextList =
            load(EntityFolders.EXECUTION_ENTITIES, "executionContextList.json", ExecutionContextList.class);

        final var executionContexts = executionContextList.getExecutionContexts();
        assertEquals(1, executionContexts.size(), "Unexpected number of execution contexts");
        final var executionContext = executionContexts.get(0);

        assertEquals("ed1dd538-253f-4749-8636-078a3a5fc5db", executionContext.getId(),
            "Unexpected execution context ID");
        assertEquals("AP 5.5 Editor", executionContext.getName(), "Unexpected execution context name");
        assertFalse(executionContext.getDefaultForSpaces().isEmpty(), "Expected a default space ID");
        assertEquals("*ghKFrLNw4DrjixF0", executionContext.getDefaultForSpaces().get(0),
            "Unexpected default for spaces");
    }

    private static <R extends Deployment> void assertDeploymentType(final Class<R> clazz,
        final Deployment actualDeployment, final DeploymentInfo expectedDeploymentInfo) {
        final var deployment = clazz.cast(actualDeployment);
        assertEquals(expectedDeploymentInfo.deploymentId(), deployment.getId(),
            "Unexpected ID for data-app deployment");
            assertEquals(expectedDeploymentInfo.deploymentName(), deployment.getName(),
                "Unexpected name for deployment class: %s".formatted(clazz.getName()));
            assertFalse(deployment.getMasonControls().isEmpty(),
                "Expected controls for deployment class: %s".formatted(clazz.getName()));
            assertEquals(expectedDeploymentInfo.selfControlHref(), deployment.getMasonControls().get("self").getHref(),
                "Unexpected 'self' control href for deployment class: %s".formatted(clazz.getName()));
            assertEquals("GET", deployment.getMasonControls().get("self").getMethod(),
                "Unexpected 'self' control method for deployment class: %s".formatted(clazz.getName()));
    }

    private final record DeploymentInfo(String deploymentId, String deploymentName, String selfControlHref) {}

}
