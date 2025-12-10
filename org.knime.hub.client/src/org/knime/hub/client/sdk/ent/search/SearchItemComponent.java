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
@SuppressWarnings({"java:S1176", "MissingJavadoc"})
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

    @JsonProperty(JSON_PROPERTY_ICON)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Icon> getIcon() {
        return Optional.ofNullable(m_icon);
    }

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
    }

    @JsonProperty(JSON_PROPERTY_DOWNLOAD_COUNT)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Integer> getDownloadCount() {
        return Optional.ofNullable(m_downloadCount);
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
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Integer> getVersion() {
        return Optional.ofNullable(m_version);
    }

    @JsonProperty(JSON_PROPERTY_VERSION_CREATED_ON)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> getVersionCreatedOn() {
        return Optional.ofNullable(m_versionCreatedOn);
    }

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
