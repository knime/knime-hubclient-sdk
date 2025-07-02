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
package org.knime.hub.client.sdk.ent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.testing.TestUtil.EntityFolders;

/**
 * Test for the problem JSON entity creation.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
class ProblemJSONEntityTest extends AbstractTest {

    @Test
    void testCreateRFC9457ErrorResponse() throws IOException {
        final var basic = load(EntityFolders.API_ENTITES, "rfc9457.json", ProblemDescription.class);
        assertEquals(Optional.empty(), basic.getType(), "Unexpected type");
        assertEquals(Optional.empty(), basic.getStatus(), "Unexpected status");
        assertEquals("Item '*k9uLSSInH1xt2UHo' does not exist.", basic.getTitle(), "Unexpected title");
        assertEquals(Optional.empty(), basic.getInstance(), "Unexpected instance");
        assertEquals(List.of(), basic.getDetails(), "Unexpected details");
        assertEquals(Optional.empty(), basic.getCode(), "Unexpected code");
        assertEquals(Map.of(), basic.getAdditionalProperties(), "Unexpected additional properties");

        final var additional = load(EntityFolders.API_ENTITES, "rfc9457AdditionalProps.json", ProblemDescription.class);
        assertEquals(Optional.of("https://example.com/probs/out-of-credit"), additional.getType(), "Unexpected type");
        assertEquals(Optional.empty(), additional.getStatus(), "Unexpected status");
        assertEquals("You do not have enough credit.", additional.getTitle(), "Unexpected title");
        assertEquals(Optional.of("/account/12345/msgs/abc"), additional.getInstance(), "Unexpected instance");
        assertEquals(List.of(), additional.getDetails(), "Unexpected details");
        assertEquals(Optional.empty(), additional.getCode(), "Unexpected code");
        final var additionalProps = Map.of("balance", 30, "accounts", List.of("/account/12345", "/account/67890"),
            "detail", "Your current balance is 30, but that costs 50.");
        assertEquals(additionalProps, additional.getAdditionalProperties(), "Unexpected additional properties");

        final var withCode = load(EntityFolders.API_ENTITES, "rfc9457WithCode.json", ProblemDescription.class);
        assertEquals(Optional.of("https://example.net/permission-error"), withCode.getType(), "Unexpected type");
        assertEquals(Optional.empty(), withCode.getStatus(), "Unexpected status");
        assertEquals("User does not have permission to see the requested workflow.", withCode.getTitle(),
            "Unexpected title");
        assertEquals(Optional.of("/workflows/blub"), withCode.getInstance(), "Unexpected instance");
        assertEquals(List.of("User '4711' does not have permission 'read-item' on resource 'blub'."),
            withCode.getDetails(), "Unexpected details");
        assertEquals(Optional.of("PermissionError"), withCode.getCode(), "Unexpected code");
        assertEquals(Map.of("foo", Map.of("bar", List.of("blub", "blah")), "fuddel", 1),
            withCode.getAdditionalProperties(), "Unexpected additional properties");
    }

}
