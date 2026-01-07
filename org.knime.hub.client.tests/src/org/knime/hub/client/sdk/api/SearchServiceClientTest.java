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
 *
 * History
 *   Dec 10, 2025 (assistant): created
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
import org.knime.hub.client.sdk.ent.search.PrivateSearchMode;
import org.knime.hub.client.sdk.ent.search.SearchResults;
import org.knime.hub.client.sdk.ent.search.SearchResultsCountByCategory;
import org.knime.hub.client.sdk.testing.TestUtil;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Tests for {@link SearchServiceClient}.
 */
class SearchServiceClientTest extends AbstractTest {

    private static SearchServiceClient SEARCH_CLIENT;

    private static final List<String> COMPONENT_SEARCH_JSON_PATHS = List.of( //
        "$['countByCategory']['all']", //
        "$['countByCategory']['components']", //
        "$['results'][*]['itemType']", //
        "$['results'][*]['id']", //
        "$['results'][*]['title']", //
        "$['results'][*]['isVersioned']", //
        "$['results'][*]['version']", //
        "$['results'][*]['downloadCount']", //
        "$['results'][*]['icon']", //
        "$['relatedTags']" //
    );

    private static final List<String> INSTANT_SEARCH_JSON_PATHS = List.of( //
        "$['countByCategory']['all']", //
        "$['countByCategory']['components']", //
        "$['results'][*]['itemType']", //
        "$['results'][*]['id']", //
        "$['results'][*]['title']", //
        "$['results'][*]['isVersioned']", //
        "$['results'][*]['version']", //
        "$['results'][*]['downloadCount']", //
        "$['results'][*]['tags']", //
        "$['suggestedTags']", //
        "$['suggestedUsernames']", //
        "$['suggestedTeamnames']", //
        "$['suggestedExternalGroups'][*]['id']", //
        "$['relatedTags']", //
        "$['relatedPathTags']", //
        "$['took']", //
        "$['esQuery']", //
        "$['esResult']" //
    );

    private static final List<String> COUNT_JSON_PATHS =
        List.of("$['all']", "$['workflows']", "$['nodes']", "$['extensions']", "$['components']", "$['collections']");

    @BeforeAll
    static void setUp() throws Exception {
        initializeServerMockTests();
        SEARCH_CLIENT = new HubClientAPI(getApiClient()).search();
    }

    @AfterAll
    static void tearDown() {
        terminateServerMockTests();
    }

    @Test
    void testSearchDeserializesComponentHit() throws IOException {
        final String fixture = "search-components.json";
        final JsonNode expectedResponse = stubSearchResponse( //
            fixture, //
            "/search", //
            Map.of( //
                "query", "foo", //
                "type", "component", //
                "limit", "5", ///
                "offset", "0", //
                "sort", "best", //
                "privateSearchMode", "include" //
            ));

        final ApiResponse<SearchResults> response = SEARCH_CLIENT.search( //
            "foo", //
            SearchServiceClient.SearchType.COMPONENT, //
            5, //
            0, //
            SearchServiceClient.SearchSort.BEST, //
            PrivateSearchMode.INCLUDE, //
            List.of(), //
            null, //
            null, //
            null, //
            Map.of() //
        );

        assertEquals(200, response.statusCode());
        TestUtil.assertJSONProperties(response, expectedResponse, COMPONENT_SEARCH_JSON_PATHS, getMapper(),
            getJsonPathConfig());
    }

    @Test
    void testInstantSearchDeserializesComponentHit() throws IOException {
        final String fixture = "instant-search.json";
        final JsonNode expectedResponse = stubSearchResponse( //
            fixture, //
            "/instant-search", //
            Map.of( //
                "query", "instant", //
                "limit", "3", //
                "privateSearchMode", "auto", //
                "debug", "true" //
            ));

        final ApiResponse<SearchResults> response = SEARCH_CLIENT.instantSearch( //
            "instant", //
            3, //
            PrivateSearchMode.AUTO, //
            true, //
            Map.of() //
        );

        assertEquals(200, response.statusCode());
        TestUtil.assertJSONProperties(response, expectedResponse, INSTANT_SEARCH_JSON_PATHS, getMapper(),
            getJsonPathConfig());
    }

    @Test
    void testGetCountsDeserializesCounts() throws IOException {
        final String fixture = "search-counts.json";
        final JsonNode expectedResponse = stubSearchResponse( //
            fixture, //
            "/search-counts", //
            Map.of() //
        );

        final ApiResponse<SearchResultsCountByCategory> response = SEARCH_CLIENT.getCounts(Map.of());

        assertEquals(200, response.statusCode());
        TestUtil.assertJSONProperties(response, expectedResponse, COUNT_JSON_PATHS, getMapper(), getJsonPathConfig());
    }

    private static JsonNode stubSearchResponse(final String testFileName, final String urlPath,
        final Map<String, String> queryParams) throws IOException {
        final var filePath =
            IPath.forPosix(TestUtil.RESOURCE_FOLDER_NAME).append("searchEntities").append(testFileName);
        final var resourceUrl = TestUtil.resolveToURL(filePath);
        final var jsonBody = TestUtil.readResourceToString(resourceUrl);
        final var jsonNode = toExpectedJsonNode(urlPath, jsonBody);

        var mapping = get(urlPathEqualTo(urlPath));
        queryParams.forEach((k, v) -> mapping.withQueryParam(k, equalTo(v)));

        getServerMock().stubFor(mapping.willReturn(aResponse() //
            .withStatus(200) //
            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON) //
            .withBody(jsonBody)));

        return jsonNode;
    }

    private static JsonNode toExpectedJsonNode(final String urlPath, final String jsonBody) throws IOException {
        // Re-marshal through the domain model so numeric fields use their declared Java types (long vs int).
        if ("/search".equals(urlPath) || "/instant-search".equals(urlPath)) {
            return getMapper().valueToTree(getMapper().readValue(jsonBody, SearchResults.class));
        }
        if ("/search-counts".equals(urlPath)) {
            return getMapper().valueToTree(getMapper().readValue(jsonBody, SearchResultsCountByCategory.class));
        }
        return getMapper().readTree(jsonBody);
    }
}
