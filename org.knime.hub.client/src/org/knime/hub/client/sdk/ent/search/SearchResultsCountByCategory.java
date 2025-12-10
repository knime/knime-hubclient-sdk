package org.knime.hub.client.sdk.ent.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Counts of search results per category.
 *
 * @since 1.1
 */
public final class SearchResultsCountByCategory {

    private static final String JSON_PROPERTY_ALL = "all";
    private final long m_all;

    private static final String JSON_PROPERTY_WORKFLOWS = "workflows";
    private final long m_workflows;

    private static final String JSON_PROPERTY_NODES = "nodes";
    private final long m_nodes;

    private static final String JSON_PROPERTY_EXTENSIONS = "extensions";
    private final long m_extensions;

    private static final String JSON_PROPERTY_COMPONENTS = "components";
    private final long m_components;

    private static final String JSON_PROPERTY_COLLECTIONS = "collections";
    private final long m_collections;

    @JsonCreator
    private SearchResultsCountByCategory(@JsonProperty(JSON_PROPERTY_ALL) final long all,
        @JsonProperty(JSON_PROPERTY_WORKFLOWS) final long workflows,
        @JsonProperty(JSON_PROPERTY_NODES) final long nodes,
        @JsonProperty(JSON_PROPERTY_EXTENSIONS) final long extensions,
        @JsonProperty(JSON_PROPERTY_COMPONENTS) final long components,
        @JsonProperty(JSON_PROPERTY_COLLECTIONS) final long collections) {
        m_all = all;
        m_workflows = workflows;
        m_nodes = nodes;
        m_extensions = extensions;
        m_components = components;
        m_collections = collections;
    }

    @JsonProperty(JSON_PROPERTY_ALL)
    public long getAll() {
        return m_all;
    }

    @JsonProperty(JSON_PROPERTY_WORKFLOWS)
    public long getWorkflows() {
        return m_workflows;
    }

    @JsonProperty(JSON_PROPERTY_NODES)
    public long getNodes() {
        return m_nodes;
    }

    @JsonProperty(JSON_PROPERTY_EXTENSIONS)
    public long getExtensions() {
        return m_extensions;
    }

    @JsonProperty(JSON_PROPERTY_COMPONENTS)
    public long getComponents() {
        return m_components;
    }

    @JsonProperty(JSON_PROPERTY_COLLECTIONS)
    public long getCollections() {
        return m_collections;
    }
}
