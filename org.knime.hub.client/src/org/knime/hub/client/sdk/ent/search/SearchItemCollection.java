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
 *  Nodes are deemed to be separate and independent programs and to not be
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

import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Collection search result.
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuppressWarnings({"java:S1176", "MissingJavadoc"})
public final class SearchItemCollection extends SearchItem {

    static final String TYPE = "Collection";

    private static final String JSON_PROPERTY_TAGS = "tags";
    private final List<String> m_tags;

    @JsonCreator
    private SearchItemCollection(@JsonProperty(SearchItem.JSON_PROPERTY_TITLE) final String title,
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
        @JsonProperty(value = JSON_PROPERTY_TAGS, required = true) final List<String> tags) {
        super(title, titleHighlighted, description, pathToResource, id, owner, ownerAccountId, explanation,
            matchedQueries, score, kudosCount, isPrivate);
        m_tags = tags;
    }

    @Override
    public SearchItemType getItemType() {
        return SearchItemType.COLLECTION;
    }

    @JsonProperty(JSON_PROPERTY_TAGS)
    public List<String> getTags() {
        return m_tags;
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
        var that = (SearchItemCollection)o;
        return Objects.equals(m_tags, that.m_tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_tags, super.hashCode());
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
