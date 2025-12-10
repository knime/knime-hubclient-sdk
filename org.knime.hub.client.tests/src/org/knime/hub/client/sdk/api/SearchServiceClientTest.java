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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.knime.hub.client.sdk.AbstractTest;
import org.knime.hub.client.sdk.ApiResponse;
import org.knime.hub.client.sdk.ent.search.PrivateSearchMode;
import org.knime.hub.client.sdk.ent.search.SearchItem;
import org.knime.hub.client.sdk.ent.search.SearchItemComponent;
import org.knime.hub.client.sdk.ent.search.SearchResults;
import org.knime.hub.client.sdk.ent.search.SearchResultsCountByCategory;
import org.knime.hub.client.sdk.testing.TestUtil;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * Tests for {@link SearchServiceClient}.
 */
class SearchServiceClientTest extends AbstractTest {

    private static SearchServiceClient SEARCH_CLIENT;

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
        stubSearchResponse(fixture, "/search", Map.of("query", "foo", "type", "component", "limit", "5", "offset", "0",
            "sort", "best", "privateSearchMode", "include"));

        final ApiResponse<SearchResults> response =
            SEARCH_CLIENT.search("foo", SearchServiceClient.SearchType.COMPONENT, 5, 0,
                SearchServiceClient.SearchSort.BEST, PrivateSearchMode.INCLUDE, List.of(), null, null, null, Map.of());

        assertEquals(200, response.statusCode());
        final var body = response.result();
        var countByCategory = body.toOptional().orElseThrow().getCountByCategory();
        assertNotNull(countByCategory);
        assertEquals(761, countByCategory.getAll());
        assertEquals(31, countByCategory.getComponents());

        final List<SearchItem> hits = body.toOptional().orElseThrow().getResults();
        assertEquals(1, hits.size());
        final SearchItemComponent hit = (SearchItemComponent)hits.get(0);
        assertEquals("Component", hit.getItemType().getValue());
        assertEquals("*4H0xvD1ha6NmoZYY", hit.getId());
        assertEquals("Header for Data Apps", hit.getTitle());
        assertTrue(hit.isVersioned());
        assertEquals(1, hit.getVersion());
        assertEquals(821, hit.getDownloadCount());
        assertNotNull(hit.getIcon());
    }

    @Test
    void testInstantSearchDeserializesComponentHit() throws IOException {
        final String fixture = "instant-search.json";
        stubSearchResponse(fixture, "/instant-search",
            Map.of("query", "instant", "limit", "3", "privateSearchMode", "auto", "debug", "true"));

        final ApiResponse<SearchResults> response =
            SEARCH_CLIENT.instantSearch("instant", 3, PrivateSearchMode.AUTO, true, Map.of());

        assertEquals(200, response.statusCode());
        final var body = response.result().toOptional().orElseThrow();

        final var counts = body.getCountByCategory();
        assertNotNull(counts);
        assertEquals(11, counts.getAll());
        assertEquals(4, counts.getComponents());

        final List<SearchItem> hits = body.getResults();
        assertEquals(1, hits.size());
        final SearchItemComponent hit = (SearchItemComponent)hits.get(0);
        assertEquals("Component", hit.getItemType().getValue());
        assertEquals("*instant-id*", hit.getId());
        assertEquals("Instant Component", hit.getTitle());
        assertTrue(hit.isVersioned());
        assertEquals(2, hit.getVersion());
        assertEquals(42, hit.getDownloadCount());
        assertNotNull(hit.getIcon());

        assertEquals(List.of("instant"), body.getSuggestedTags());
        assertEquals(List.of("insta-user"), body.getSuggestedUsernames());
        assertEquals(List.of("instant-team"), body.getSuggestedTeamnames());
        assertEquals(1, body.getSuggestedExternalGroups().size());
        assertEquals("group:ext:1", body.getSuggestedExternalGroups().get(0).getId());
        assertEquals(List.of("related"), body.getRelatedTags());
        assertEquals(List.of("path-tag"), body.getRelatedPathTags());
        assertEquals(12L, body.getTook());
        assertTrue(body.getEsQuery().isPresent());
        assertTrue(body.getEsResult().isPresent());
    }

    @Test
    void testGetCountsDeserializesCounts() throws IOException {
        final String fixture = "search-counts.json";
        stubSearchResponse(fixture, "/search-counts", Map.of());

        final ApiResponse<SearchResultsCountByCategory> response = SEARCH_CLIENT.getCounts(Map.of());

        assertEquals(200, response.statusCode());
        final var counts = response.result().toOptional().orElseThrow();
        assertEquals(123, counts.getAll());
        assertEquals(45, counts.getWorkflows());
        assertEquals(12, counts.getNodes());
        assertEquals(7, counts.getExtensions());
        assertEquals(34, counts.getComponents());
        assertEquals(25, counts.getCollections());
    }

    private static void stubSearchResponse(final String testFileName, final String urlPath,
        final Map<String, String> queryParams) throws IOException {
        final var filePath =
            IPath.forPosix(TestUtil.RESOURCE_FOLDER_NAME).append("searchEntities").append(testFileName);
        final URL resourceUrl = TestUtil.resolveToURL(filePath);
        final var jsonBody = TestUtil.readResourceToString(resourceUrl);

        var mapping = get(urlPathEqualTo(urlPath));
        queryParams.forEach((k, v) -> mapping.withQueryParam(k, equalTo(v)));

        getServerMock().stubFor(mapping.willReturn(aResponse() //
            .withStatus(200) //
            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON) //
            .withBody(jsonBody)));
    }
}
