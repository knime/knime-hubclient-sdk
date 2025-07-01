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
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Jun 23, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO representing a named item version of a {@link RepositoryItem}.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NamedItemVersion {

    private static final String JSON_PROPERTY_VERSION = "version";
    private int m_version;

    private static final String JSON_PROPERTY_TITLE = "title";
    private String m_title;

    private static final String JSON_PROPERTY_DESCRIPTION = "description";
    private String m_description;

    private static final String JSON_PROPERTY_AUTHOR = "author";
    private String m_author;

    private static final String JSON_PROPERTY_AUTHOR_ACCOUNT_ID = "authorAccountId";
    private String m_authorAccountId;

    private static final String JSON_PROPERTY_CREATED_ON = "createdOn";
    private Instant m_createdOn;

    @JsonCreator
    private NamedItemVersion(@JsonProperty(value = JSON_PROPERTY_VERSION, required = true) final int version,
        @JsonProperty(value = JSON_PROPERTY_TITLE, required = true) final String title,
        @JsonProperty(value = JSON_PROPERTY_DESCRIPTION, required = false) final String description,
        @JsonProperty(value = JSON_PROPERTY_AUTHOR, required = true) final String author,
        @JsonProperty(value = JSON_PROPERTY_AUTHOR_ACCOUNT_ID, required = true) final String authorAccountId,
        @JsonProperty(value = JSON_PROPERTY_CREATED_ON, required = true) final Instant createdOn) {
        m_version = version;
        m_title = title;
        m_description = description;
        m_author = author;
        m_authorAccountId = authorAccountId;
        m_createdOn = createdOn;
    }

    /**
     * Retrieves the version number.
     *
     * @return version
     */
    @JsonProperty(JSON_PROPERTY_VERSION)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public int getVersion() {
        return m_version;
    }

    /**
     * Retrieves the version title.
     *
     * @return title
     */
    @JsonProperty(JSON_PROPERTY_TITLE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getTitle() {
        return m_title;
    }

    /**
     * Retrieves the version description.
     *
     * @return description
     */
    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT)
    public Optional<String> getDescription() {
        return Optional.ofNullable(m_description);
    }

    /**
     * Retrieves the version author.
     *
     * @return author
     */
    @JsonProperty(JSON_PROPERTY_AUTHOR)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getAuthor() {
        return m_author;
    }

    /**
     * Retrieves the account ID of the version author.
     *
     * @return authorAccountId
     */
    @JsonProperty(JSON_PROPERTY_AUTHOR_ACCOUNT_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getAuthorAccountId() {
        return m_authorAccountId;
    }

    /**
     * Retrieves the time stamp of the version creation.
     *
     * @return createdOn
     */
    @JsonProperty(JSON_PROPERTY_CREATED_ON)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public Instant getCreatedOn() {
        return m_createdOn;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var namedItemVersion = (NamedItemVersion) o;
        return Objects.equals(this.m_version, namedItemVersion.m_version)
                && Objects.equals(this.m_title, namedItemVersion.m_title)
                && Objects.equals(this.m_description, namedItemVersion.m_description)
                && Objects.equals(this.m_author, namedItemVersion.m_author)
                && Objects.equals(this.m_authorAccountId, namedItemVersion.m_authorAccountId)
                && Objects.equals(this.m_createdOn, namedItemVersion.m_createdOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_version, m_title, m_description, m_author, m_authorAccountId, m_createdOn);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }

}
