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
 *   Feb 27, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.knime.hub.client.sdk.ApiClient;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.HubFailureIOException;
import org.knime.hub.client.sdk.api.HubClientAPI;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

/**
 * Test utility class for unit tests.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
public class TestUtil {

    /** resource folder name */
    public static final String RESOURCE_FOLDER_NAME = "resources";

    /**
     * Repository item type enum.
     */
    public enum EntityFolders {
        /** Catalog entity folder */
        CATALOG_ENTITES("catalogEntities"), //
        /** Account entity folder */
        ACCOUNT_ENTITIES("accountEntities"), //
        /** Execution entity folder */
        EXECUTION_ENTITIES("executionEntities"), //
        /** API entity folder */
        API_ENTITES("apiEntities"); //

        private final String m_value;

        EntityFolders(final String value) {
            this.m_value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(m_value);
        }
    }

    /**
     * Resolves a path relative to the plug-in or any fragment's root into an absolute path.
     *
     * @param relativePath a relative path
     * @return the resolved absolute path
     * @throws IOException
     */
    public static URL resolveToURL(final IPath relativePath) throws IOException {
        Bundle bundle = FrameworkUtil.getBundle(HubClientAPI.class);
        if (bundle != null) {
            return FileLocator.toFileURL(FileLocator.find(bundle, relativePath, null));
        }

        // allow for local OSGi-free testing
        return Path.of(relativePath.toOSString()).toUri().toURL();
    }

    /**
     * Asserts the JSON properties of the API response entity given expected JSON paths.
     *
     * @param <R> response entity type
     * @param actualApiResponse the actual API response
     * @param knimeHubJSONResponse the real hub response
     * @param expectedJsonPaths the expected JSON paths
     * @param mapper {@link ObjectMapper} used in the {@link ApiClient}
     * @param jsonConfig The JSON configuration needed to query the JSON objects
     * @throws HubFailureIOException if an I/O error occurred
     */
    public static <R> void assertJSONProperties(final ApiResponse<R> actualApiResponse,
        final JsonNode knimeHubJSONResponse, final List<String> expectedJsonPaths, final ObjectMapper mapper,
        final Configuration jsonConfig) throws HubFailureIOException {
        // Create the actual JSON node response object.
        var responseEntity = actualApiResponse.checkSuccessful();
        JsonNode actualJSONResponse = mapper.valueToTree(responseEntity);

        // Compare the JSON properties queried using the expected JSON paths.
        for (var jsonPath : expectedJsonPaths) {
            var actualJSONProperties = JsonPath.using(jsonConfig).parse(actualJSONResponse).read(jsonPath);
            var expectedJSONProperties = JsonPath.using(jsonConfig).parse(knimeHubJSONResponse).read(jsonPath);

            // For expected JSON attributes which are represented by ZonedDateTime, we have to first parse
            // the string into a ZonedDateTime before we can compare with the actual values.
            expectedJSONProperties = parseJSONPropertiesAsZonedDateTime(mapper,expectedJSONProperties, jsonPath);

            assertEquals(expectedJSONProperties, actualJSONProperties,
                "Unexpected properties for JSON path '%s'".formatted(jsonPath));
        }
    }

    private static Object parseJSONPropertiesAsZonedDateTime(final ObjectMapper mapper,
        final Object expectedJSONProperties, final String jsonPath) {
        if (!(expectedJSONProperties instanceof NullNode)
                && (jsonPath.contains("createdOn") || jsonPath.contains("lastUploadedOn"))) {
            if (expectedJSONProperties instanceof TextNode expectedTextNode) {
                final var textValue = expectedTextNode.asText().equals(null) ? null
                    : ZonedDateTime.parse(expectedTextNode.asText()).toString();
                return new TextNode(textValue);
            }
            if (expectedJSONProperties instanceof ArrayNode expectedArrayNode) {
                var parsedArrayNode = mapper.createArrayNode();
                for (Iterator<JsonNode> iterator = expectedArrayNode.iterator(); iterator.hasNext();) {
                    final var next = iterator.next();
                    if (next instanceof NullNode) {
                        parsedArrayNode.add(NullNode.getInstance());
                    } else {
                        parsedArrayNode.add(new TextNode(ZonedDateTime.parse(next.asText()).toString()));
                    }
                }
                return parsedArrayNode;
            }
        }
        return expectedJSONProperties;
    }

}
