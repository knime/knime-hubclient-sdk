package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemWorkflow extends SearchItem {

    private static final String JSON_PROPERTY_TAGS = "tags";
    private static final String JSON_PROPERTY_DOWNLOAD_COUNT = "downloadCount";
    private static final String JSON_PROPERTY_IS_VERSIONED = "isVersioned";
    private static final String JSON_PROPERTY_VERSION = "version";
    private static final String JSON_PROPERTY_VERSION_CREATED_ON = "versionCreatedOn";
    private static final String JSON_PROPERTY_LAST_EDITED_ON = "lastEditedOn";

    private List<String> m_tags = new ArrayList<>();
    private Integer m_downloadCount;
    private boolean m_isVersioned;
    private Integer m_version;
    private String m_versionCreatedOn;
    private String m_lastEditedOn;

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    public void setTags(final List<String> tags) {
        m_tags = tags;
    }

    @JsonProperty(JSON_PROPERTY_DOWNLOAD_COUNT)
    public Integer getDownloadCount() {
        return m_downloadCount;
    }

    public void setDownloadCount(final Integer downloadCount) {
        m_downloadCount = downloadCount;
    }

    @JsonProperty(JSON_PROPERTY_IS_VERSIONED)
    public boolean isVersioned() {
        return m_isVersioned;
    }

    public void setVersioned(final boolean isVersioned) {
        m_isVersioned = isVersioned;
    }

    @JsonProperty(JSON_PROPERTY_VERSION)
    public Integer getVersion() {
        return m_version;
    }

    public void setVersion(final Integer version) {
        m_version = version;
        if (version != null) {
            m_isVersioned = true;
        }
    }

    @JsonProperty(JSON_PROPERTY_VERSION_CREATED_ON)
    public String getVersionCreatedOn() {
        return m_versionCreatedOn;
    }

    public void setVersionCreatedOn(final String versionCreatedOn) {
        m_versionCreatedOn = versionCreatedOn;
    }

    @JsonProperty(JSON_PROPERTY_LAST_EDITED_ON)
    public String getLastEditedOn() {
        return m_lastEditedOn;
    }

    public void setLastEditedOn(final String lastEditedOn) {
        m_lastEditedOn = lastEditedOn;
    }
}
