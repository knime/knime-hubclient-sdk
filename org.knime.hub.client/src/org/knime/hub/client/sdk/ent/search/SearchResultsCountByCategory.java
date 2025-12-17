package org.knime.hub.client.sdk.ent.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Counts of search results per category.
 */
public final class SearchResultsCountByCategory {

    private static final String JSON_PROPERTY_ALL = "all";
    private static final String JSON_PROPERTY_WORKFLOWS = "workflows";
    private static final String JSON_PROPERTY_NODES = "nodes";
    private static final String JSON_PROPERTY_EXTENSIONS = "extensions";
    private static final String JSON_PROPERTY_COMPONENTS = "components";
    private static final String JSON_PROPERTY_COLLECTIONS = "collections";

    private long m_all;
    private long m_workflows;
    private long m_nodes;
    private long m_extensions;
    private long m_components;
    private long m_collections;

    @JsonProperty(JSON_PROPERTY_ALL)
    public long getAll() {
        return m_all;
    }

    public void setAll(final long all) {
        m_all = all;
    }

    @JsonProperty(JSON_PROPERTY_WORKFLOWS)
    public long getWorkflows() {
        return m_workflows;
    }

    public void setWorkflows(final long workflows) {
        m_workflows = workflows;
    }

    @JsonProperty(JSON_PROPERTY_NODES)
    public long getNodes() {
        return m_nodes;
    }

    public void setNodes(final long nodes) {
        m_nodes = nodes;
    }

    @JsonProperty(JSON_PROPERTY_EXTENSIONS)
    public long getExtensions() {
        return m_extensions;
    }

    public void setExtensions(final long extensions) {
        m_extensions = extensions;
    }

    @JsonProperty(JSON_PROPERTY_COMPONENTS)
    public long getComponents() {
        return m_components;
    }

    public void setComponents(final long components) {
        m_components = components;
    }

    @JsonProperty(JSON_PROPERTY_COLLECTIONS)
    public long getCollections() {
        return m_collections;
    }

    public void setCollections(final long collections) {
        m_collections = collections;
    }
}
