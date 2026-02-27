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
 */
package org.knime.hub.client.sdk.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.ent.catalog.ItemUploadInstructions;
import org.knime.hub.client.sdk.ent.catalog.ItemUploadRequest;
import org.knime.hub.client.sdk.testing.TestUtil;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Tests for {@link CatalogServiceClient#initiateUploadById(String, ItemUploadRequest, Map)}.
 */
class CatalogServiceClientUploadByIdTest extends AbstractTest {

    private static CatalogServiceClient CATALOG_CLIENT;

    @BeforeAll
    static void setUp() throws Exception {
        initializeServerMockTests();
        CATALOG_CLIENT = new HubClientAPI(getApiClient()).catalog();
    }

    @AfterAll
    static void tearDown() {
        terminateServerMockTests();
    }

    @Test
    void testInitiateUploadById() throws IOException {
        final String itemId = "item-123";
        final String path = "/repository/%s/artifact".formatted(itemId);

        final var filePath = IPath.forPosix(TestUtil.RESOURCE_FOLDER_NAME)
            .append(TestUtil.EntityFolders.CATALOG_ENTITES.toString())
            .append("itemUploadInstructions.json");
        final var resourceUrl = TestUtil.resolveToURL(filePath);
        final var jsonBody = TestUtil.readResourceToString(resourceUrl);
        final JsonNode expectedResponse = getMapper().readTree(jsonBody);

        getServerMock().stubFor(put(urlEqualTo(path))
            .withRequestBody(matchingJsonPath("$.itemContentType", equalTo("application/vnd.knime.workflow+zip")))
            .withRequestBody(matchingJsonPath("$.initialPartCount", equalTo("1")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .withBody(jsonBody)));

        final ApiResponse<ItemUploadInstructions> response = CATALOG_CLIENT.initiateUploadById(
            itemId, new ItemUploadRequest("application/vnd.knime.workflow+zip", 1), Map.of());

        assertEquals(200, response.statusCode(), "Expected initiateUploadById to return HTTP 200");

        final var expectedJsonPaths = List.of(
            "$['uploadId']",
            "$['parts']['1']['method']",
            "$['parts']['1']['url']"
        );
        TestUtil.assertJSONProperties(response, expectedResponse, expectedJsonPaths, getMapper(), getJsonPathConfig());

        getServerMock().verify(putRequestedFor(urlEqualTo(path)));
    }
}
