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
 * ---------------------------------------------------------------------
 */
package org.knime.hub.client.sdk.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
import org.knime.hub.client.sdk.ent.search.ComponentSuggestions;
import org.knime.hub.client.sdk.ent.search.IdentitySuggestions;
import org.knime.hub.client.sdk.testing.TestUtil;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Tests for {@link SuggestionsServiceClient}.
 */
class SuggestionsServiceClientTest extends AbstractTest {

    private static SuggestionsServiceClient SUGGESTIONS_CLIENT;

    private static final List<String> COMPONENT_SUGGESTIONS_JSON_PATHS = List.of( //
        "$['components'][*]['itemType']", //
        "$['components'][*]['id']", //
        "$['components'][*]['title']", //
        "$['components'][*]['isVersioned']", //
        "$['components'][*]['version']", //
        "$['components'][*]['downloadCount']", //
        "$['components'][*]['icon']" //
    );

    private static final List<String> IDENTITY_SUGGESTIONS_JSON_PATHS = List.of( //
        "$['users'][*]['id']", //
        "$['users'][*]['name']", //
        "$['teams'][*]['id']", //
        "$['teams'][*]['name']", //
        "$['externalGroups'][*]['id']", //
        "$['externalGroups'][*]['name']" //
    );

    @BeforeAll
    static void setUp() throws Exception {
        initializeServerMockTests();
        SUGGESTIONS_CLIENT = new HubClientAPI(getApiClient()).suggestions();
    }

    @AfterAll
    static void tearDown() {
        terminateServerMockTests();
    }

    @Test
    void testSuggestComponentsDeserializesComponentHits() throws IOException {
        final JsonNode expectedResponse = stubSuggestionsResponse( //
            "suggestions-components.json", //
            "/suggestions/components", //
            Map.of( //
                "query", "foo", //
                "limit", "5", //
                "inPort", "org.knime.TablePortObject" //
            ),
            ComponentSuggestions.class);

        final ApiResponse<ComponentSuggestions> response = SUGGESTIONS_CLIENT.suggestComponents( //
            "foo", //
            5, //
            "org.knime.TablePortObject", //
            null, //
            Map.of() //
        );

        assertEquals(200, response.statusCode());
        TestUtil.assertJSONProperties(response, expectedResponse, COMPONENT_SUGGESTIONS_JSON_PATHS, getMapper(),
            getJsonPathConfig());
    }

    @Test
    void testSuggestIdentitiesDeserializesIdentityHits() throws IOException {
        final JsonNode expectedResponse = stubSuggestionsResponse( //
            "suggestions-identities.json", //
            "/suggestions/identities", //
            Map.of( //
                "query", "te", //
                "limit", "2" //
            ),
            IdentitySuggestions.class);

        final ApiResponse<IdentitySuggestions> response = SUGGESTIONS_CLIENT.suggestIdentities( //
            "te", //
            2, //
            Map.of() //
        );

        assertEquals(200, response.statusCode());
        TestUtil.assertJSONProperties(response, expectedResponse, IDENTITY_SUGGESTIONS_JSON_PATHS, getMapper(),
            getJsonPathConfig());
    }

    private static <T> JsonNode stubSuggestionsResponse(final String testFileName, final String urlPath,
        final Map<String, String> queryParams, final Class<T> entityType) throws IOException {
        final var filePath =
            IPath.forPosix(TestUtil.RESOURCE_FOLDER_NAME).append("searchEntities").append(testFileName);
        final var resourceUrl = TestUtil.resolveToURL(filePath);
        final var jsonBody = TestUtil.readResourceToString(resourceUrl);
        final var jsonNode = getMapper().valueToTree(getMapper().readValue(jsonBody, entityType));

        var mapping = get(urlPathEqualTo(urlPath));
        queryParams.forEach((k, v) -> mapping.withQueryParam(k, equalTo(v)));

        getServerMock().stubFor(mapping.willReturn(aResponse() //
            .withStatus(200) //
            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON) //
            .withBody(jsonBody)));

        return jsonNode;
    }
}
