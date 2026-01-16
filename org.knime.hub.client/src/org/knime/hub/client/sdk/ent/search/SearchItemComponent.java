package org.knime.hub.client.sdk.ent.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Component search result.
 *
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemComponent extends SearchItem {

    private static final String JSON_PROPERTY_ICON = "icon";
    private final Icon m_icon;

    private static final String JSON_PROPERTY_TAGS = "tags";
    private final List<String> m_tags;

    private static final String JSON_PROPERTY_DOWNLOAD_COUNT = "downloadCount";
    private final Integer m_downloadCount;

    private static final String JSON_PROPERTY_IS_ENCRYPTED = "isEncrypted";
    private final boolean m_isEncrypted;

    private static final String JSON_PROPERTY_IS_VERSIONED = "isVersioned";
    private final boolean m_isVersioned;

    private static final String JSON_PROPERTY_VERSION = "version";
    private final Integer m_version;

    private static final String JSON_PROPERTY_VERSION_CREATED_ON = "versionCreatedOn";
    private final String m_versionCreatedOn;

    private static final String JSON_PROPERTY_LAST_EDITED_ON = "lastEditedOn";
    private final String m_lastEditedOn;

    @JsonCreator
    private SearchItemComponent(@JsonProperty(SearchItem.JSON_PROPERTY_TITLE) final String title,
        @JsonProperty(SearchItem.JSON_PROPERTY_TITLE_HIGHLIGHTED) final String titleHighlighted,
        @JsonProperty(SearchItem.JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(SearchItem.JSON_PROPERTY_ITEM_TYPE) final SearchItemType itemType,
        @JsonProperty(SearchItem.JSON_PROPERTY_PATH) final String pathToResource,
        @JsonProperty(SearchItem.JSON_PROPERTY_ID) final String id,
        @JsonProperty(SearchItem.JSON_PROPERTY_OWNER) final String owner,
        @JsonProperty(SearchItem.JSON_PROPERTY_OWNER_ACCOUNT_ID) final String ownerAccountId,
        @JsonProperty(SearchItem.JSON_PROPERTY_EXPLANATION) final String explanation,
        @JsonProperty(SearchItem.JSON_PROPERTY_MATCHED_QUERIES) final String[] matchedQueries,
        @JsonProperty(SearchItem.JSON_PROPERTY_SCORE) final Float score,
        @JsonProperty(SearchItem.JSON_PROPERTY_KUDOS) final Integer kudosCount,
        @JsonProperty(SearchItem.JSON_PROPERTY_PRIVATE) final Boolean isPrivate,
        @JsonProperty(JSON_PROPERTY_ICON) final Icon icon,
        @JsonProperty(JSON_PROPERTY_TAGS) final List<String> tags,
        @JsonProperty(JSON_PROPERTY_DOWNLOAD_COUNT) final Integer downloadCount,
        @JsonProperty(JSON_PROPERTY_IS_ENCRYPTED) final boolean isEncrypted,
        @JsonProperty(JSON_PROPERTY_IS_VERSIONED) final boolean isVersioned,
        @JsonProperty(JSON_PROPERTY_VERSION) final Integer version,
        @JsonProperty(JSON_PROPERTY_VERSION_CREATED_ON) final String versionCreatedOn,
        @JsonProperty(JSON_PROPERTY_LAST_EDITED_ON) final String lastEditedOn) {
        super(title, titleHighlighted, description, itemType, pathToResource, id, owner, ownerAccountId, explanation,
            matchedQueries, score, kudosCount, isPrivate);
        m_icon = icon;
        m_tags = tags == null ? new ArrayList<>() : tags;
        m_downloadCount = downloadCount;
        m_isEncrypted = isEncrypted;
        m_isVersioned = isVersioned || version != null;
        m_version = version;
        m_versionCreatedOn = versionCreatedOn;
        m_lastEditedOn = lastEditedOn;
    }

    @JsonProperty(JSON_PROPERTY_ICON)
    public Icon getIcon() {
        return m_icon;
    }

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    @JsonProperty(JSON_PROPERTY_DOWNLOAD_COUNT)
    public Integer getDownloadCount() {
        return m_downloadCount;
    }

    @JsonProperty(JSON_PROPERTY_IS_ENCRYPTED)
    public boolean isEncrypted() {
        return m_isEncrypted;
    }

    @JsonProperty(JSON_PROPERTY_IS_VERSIONED)
    public boolean isVersioned() {
        return m_isVersioned;
    }

    @JsonProperty(JSON_PROPERTY_VERSION)
    public Integer getVersion() {
        return m_version;
    }

    @JsonProperty(JSON_PROPERTY_VERSION_CREATED_ON)
    public String getVersionCreatedOn() {
        return m_versionCreatedOn;
    }

    @JsonProperty(JSON_PROPERTY_LAST_EDITED_ON)
    public String getLastEditedOn() {
        return m_lastEditedOn;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        var that = (SearchItemComponent)o;
        return m_isEncrypted == that.m_isEncrypted
            && m_isVersioned == that.m_isVersioned
            && Objects.equals(m_icon, that.m_icon)
            && Objects.equals(m_tags, that.m_tags)
            && Objects.equals(m_downloadCount, that.m_downloadCount)
            && Objects.equals(m_version, that.m_version)
            && Objects.equals(m_versionCreatedOn, that.m_versionCreatedOn)
            && Objects.equals(m_lastEditedOn, that.m_lastEditedOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_icon, m_tags, m_downloadCount, m_isEncrypted, m_isVersioned, m_version,
            m_versionCreatedOn, m_lastEditedOn, super.hashCode());
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
