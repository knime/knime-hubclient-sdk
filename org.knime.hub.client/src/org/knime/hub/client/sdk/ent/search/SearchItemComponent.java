/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.org; Email: contact@knime.org
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
 *  Nodes are deemed to be separate and independent programs and are not
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 */
package org.knime.hub.client.sdk.ent.search;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Component search result.
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemComponent extends SearchItem {

    static final String TYPE = "Component";

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

    /**
     * Search result item representing a component on the KNIME Hub.
     *
     * @param title the title of the component, may be {@code null}
     * @param titleHighlighted the title with HTML markup highlighting matched query terms, may be {@code null}
     * @param description the description of the component, may be {@code null}
     * @param pathToResource the path to the component resource, must not be {@code null}
     * @param id the unique identifier of the component, must not be {@code null}
     * @param owner the owner of the component, must not be {@code null}
     * @param ownerAccountId the account ID of the owner, may be {@code null}
     * @param explanation an optional explanation of why this item matched the search query, may be {@code null}
     * @param matchedQueries an optional array of matched query terms, may be {@code null}
     * @param score an optional relevance score for this search result, may be {@code null}
     * @param kudosCount an optional count of kudos received by this component, may be {@code null}
     * @param isPrivate whether this component is private
     * @param icon the icon metadata for this component, may be {@code null} if no icon information is available
     * @param tags the list of tags associated with this component, must not be {@code null} but may be empty
     * @param downloadCount an optional count of downloads for this component, may be {@code null}
     * @param isEncrypted whether this component's content is encrypted
     * @param isVersioned whether this component has a published version
     * @param version an optional version number for this component, if it has been versioned; may be {@code null}
     * @param versionCreatedOn an optional ISO-8601 timestamp at which this component was versioned; may be
     * {@code null}
     * @param lastEditedOn an optional ISO-8601 timestamp of the last edit of this component; may be {@code null}
     */
    @JsonCreator
    public SearchItemComponent(@JsonProperty(SearchItem.JSON_PROPERTY_TITLE) final String title,
        @JsonProperty(SearchItem.JSON_PROPERTY_TITLE_HIGHLIGHTED) final String titleHighlighted,
        @JsonProperty(SearchItem.JSON_PROPERTY_DESCRIPTION) final String description,
        @JsonProperty(value = SearchItem.JSON_PROPERTY_PATH, required = true) final String pathToResource,
        @JsonProperty(value = SearchItem.JSON_PROPERTY_ID, required = true) final String id,
        @JsonProperty(value = SearchItem.JSON_PROPERTY_OWNER, required = true) final String owner,
        @JsonProperty(SearchItem.JSON_PROPERTY_OWNER_ACCOUNT_ID) final String ownerAccountId,
        @JsonProperty(SearchItem.JSON_PROPERTY_EXPLANATION) final String explanation,
        @JsonProperty(SearchItem.JSON_PROPERTY_MATCHED_QUERIES) final String[] matchedQueries,
        @JsonProperty(SearchItem.JSON_PROPERTY_SCORE) final Float score,
        @JsonProperty(SearchItem.JSON_PROPERTY_KUDOS) final Integer kudosCount,
        @JsonProperty(value = SearchItem.JSON_PROPERTY_PRIVATE, required = true) final Boolean isPrivate,
        @JsonProperty(JSON_PROPERTY_ICON) final Icon icon,
        @JsonProperty(value = JSON_PROPERTY_TAGS, required = true) final List<String> tags,
        @JsonProperty(JSON_PROPERTY_DOWNLOAD_COUNT) final Integer downloadCount,
        @JsonProperty(value = JSON_PROPERTY_IS_ENCRYPTED, required = true) final boolean isEncrypted,
        @JsonProperty(value = JSON_PROPERTY_IS_VERSIONED, required = true) final boolean isVersioned,
        @JsonProperty(JSON_PROPERTY_VERSION) final Integer version,
        @JsonProperty(JSON_PROPERTY_VERSION_CREATED_ON) final String versionCreatedOn,
        @JsonProperty(JSON_PROPERTY_LAST_EDITED_ON) final String lastEditedOn) {
        super(title, titleHighlighted, description, pathToResource, id, owner, ownerAccountId, explanation,
            matchedQueries, score, kudosCount, isPrivate);
        m_icon = icon;
        m_tags = tags;
        m_downloadCount = downloadCount;
        m_isEncrypted = isEncrypted;
        m_isVersioned = isVersioned || version != null;
        m_version = version;
        m_versionCreatedOn = versionCreatedOn;
        m_lastEditedOn = lastEditedOn;
    }

    @Override
    public SearchItemType getItemType() {
        return SearchItemType.COMPONENT;
    }

    /**
     * Returns the icon metadata for this component, if available.
     *
     * @return the optional icon
     */
    @JsonProperty(JSON_PROPERTY_ICON)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Icon> getIcon() {
        return Optional.ofNullable(m_icon);
    }

    /**
     * Returns the tags associated with this component. The list may be empty but is never {@code null}.
     *
     * @return the list of tags
     */
    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    /**
     * Returns the number of times this component has been downloaded, if present.
     *
     * @return the optional download count
     */
    @JsonProperty(JSON_PROPERTY_DOWNLOAD_COUNT)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Integer> getDownloadCount() {
        return Optional.ofNullable(m_downloadCount);
    }

    /**
     * Returns whether the content of this component is encrypted.
     *
     * @return {@code true} if the component is encrypted
     */
    @JsonProperty(JSON_PROPERTY_IS_ENCRYPTED)
    public boolean isEncrypted() {
        return m_isEncrypted;
    }

    /**
     * Returns whether this component has a published version.
     *
     * @return {@code true} if the component is versioned
     */
    @JsonProperty(JSON_PROPERTY_IS_VERSIONED)
    public boolean isVersioned() {
        return m_isVersioned;
    }

    /**
     * Returns the version number of this component, if it has been versioned.
     *
     * @return the optional version number
     */
    @JsonProperty(JSON_PROPERTY_VERSION)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Integer> getVersion() {
        return Optional.ofNullable(m_version);
    }

    /**
     * Returns the ISO-8601 timestamp at which this component was versioned, if present.
     *
     * @return the optional version creation timestamp
     */
    @JsonProperty(JSON_PROPERTY_VERSION_CREATED_ON)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getVersionCreatedOn() {
        return Optional.ofNullable(m_versionCreatedOn);
    }

    /**
     * Returns the ISO-8601 timestamp of the last edit of this component, if present.
     *
     * @return the optional last-edited timestamp
     */
    @JsonProperty(JSON_PROPERTY_LAST_EDITED_ON)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getLastEditedOn() {
        return Optional.ofNullable(m_lastEditedOn);
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
