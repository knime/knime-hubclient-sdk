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

import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the create named item version request body.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CreateNamedItemVersionRequestBody {

    private static final String JSON_PROPERTY_TITLE = "title";
    private final String m_title;

    private static final String JSON_PROPERTY_DESCRIPTION = "description";
    private final String m_description;

    @JsonCreator
    private CreateNamedItemVersionRequestBody(
        @JsonProperty(value = JSON_PROPERTY_TITLE, required = true) final String title,
        @JsonProperty(value = JSON_PROPERTY_DESCRIPTION) final String description) {
        m_title = title;
        m_description = description;
    }

    /**
     * Retrieves the title of the version.
     *
     * @return title
     */
    @JsonProperty(JSON_PROPERTY_TITLE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getTitle() {
        return m_title;
    }

    /**
     * Retrieves the optional description of this version.
     *
     * @return description
     */
    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Optional<String> getDescription() {
        return Optional.ofNullable(m_description);
    }

    /**
     * Creates a new {@link CreateNamedItemVersionRequestBuilder}.
     *
     * @return builder
     */
    public static CreateNamedItemVersionRequestBuilder builder() {
        return new CreateNamedItemVersionRequestBuilder();
    }

    /**
     * Builder for {@link CreateNamedItemVersionRequestBody}.
     */
    public static final class CreateNamedItemVersionRequestBuilder {
        private String m_title;
        private String m_description;

        private CreateNamedItemVersionRequestBuilder() {
        }

        /**
         * Sets the version title.
         *
         * @param title The version title
         * @return this
         */
        public CreateNamedItemVersionRequestBuilder withTitle(final String title) {
            m_title = title;
            return this;
        }

        /**
         * Sets the version description.
         *
         * @param description The version description
         * @return this
         */
        public CreateNamedItemVersionRequestBuilder withDescription(final String description) {
            m_description = description;
            return this;
        }

        /**
         * Builds a new {@link CreateNamedItemVersionRequestBody}.
         *
         * @return createNamedItemVersionRequestBody
         */
        public CreateNamedItemVersionRequestBody build() {
            return new CreateNamedItemVersionRequestBody(m_title, m_description);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var createNamedItemVersionRequestBody = (CreateNamedItemVersionRequestBody) o;
        return Objects.equals(this.m_title, createNamedItemVersionRequestBody.m_title)
                && Objects.equals(this.m_description, createNamedItemVersionRequestBody.m_description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_title, m_description);
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }

}

