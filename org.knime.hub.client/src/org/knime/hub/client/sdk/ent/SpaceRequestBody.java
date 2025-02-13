/*
 * ------------------------------------------------------------------------
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
 * -------------------------------------------------------------------
 *
 * History
 *   Nov 6, 2024 (magnus): created
 */

package org.knime.hub.client.sdk.ent;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the space request body.
 * 
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SpaceRequestBody {
    
    private static final String JSON_PROPERTY_DESCRIPTION = "description";
    private final String m_description;

    private static final String JSON_PROPERTY_TAGS = "tags";
    private final List<String> m_tags;

    private static final String JSON_PROPERTY_PRIVATE = "private";
    private final Boolean m_private;

    @JsonCreator
    private SpaceRequestBody(
            @JsonProperty(value = JSON_PROPERTY_DESCRIPTION) String description,
            @JsonProperty(value = JSON_PROPERTY_TAGS) List<String> tags,
            @JsonProperty(value = JSON_PROPERTY_PRIVATE) Boolean isPrivate) {
        this.m_description = description;
        this.m_tags = tags;
        this.m_private = isPrivate;
    }

    /**
     * Retrieves the optional description for this item.
     * 
     * @return description
     */
    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Optional<String> getDescription() {
        return Optional.ofNullable(m_description);
    }

    /**
     * Retrieves an array of tags for the space.
     * 
     * @return tags
     */
    @JsonProperty(JSON_PROPERTY_TAGS)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public List<String> getTags() {
        return Optional.ofNullable(m_tags).orElseGet(Collections::emptyList);
    }

    /**
     * Return {@code true} if the space is private otherwise {@code false}.
     * 
     * @return _private
     */
    @JsonProperty(JSON_PROPERTY_PRIVATE)
    @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
    public Optional<Boolean> getPrivate() {
        return Optional.ofNullable(m_private);
    }

    /**
     * Creates a new {@link SpaceRequestBodyBuilder}.
     * 
     * @return builder
     */
    public static SpaceRequestBodyBuilder builder() {
        return new SpaceRequestBodyBuilder();
    }

    /**
     * Builder for {@link SpaceRequestBody}.
     */
    public static final class SpaceRequestBodyBuilder {
        private String m_description;
        private List<String> m_tags;
        private Boolean m_private;

        private SpaceRequestBodyBuilder() {
        }

        /**
         * Sets a description for the space.
         * 
         * @param description
         * @return this
         */
        public SpaceRequestBodyBuilder withDescription(String description) {
            m_description = description;
            return this;
        }

        /**
         * Sets tags for the space.
         * 
         * @param tags
         * @return this
         */
        public SpaceRequestBodyBuilder withTags(List<String> tags) {
            m_tags = tags;
            return this;
        }

        /**
         * Sets a value for whether the space shall be private.
         * 
         * @param isPrivate
         * @return this
         */
        public SpaceRequestBodyBuilder setPrivate(Boolean isPrivate) {
            m_private = isPrivate;
            return this;
        }

        /**
         * Builds a new {@link SpaceRequestBody}.
         * 
         * @return spaceRequestBody
         */
        public SpaceRequestBody build() {
            return new SpaceRequestBody(m_description, m_tags, m_private);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var spaceRequestBody = (SpaceRequestBody) o;
        return Objects.equals(this.m_description, spaceRequestBody.m_description)
                && Objects.equals(this.m_tags, spaceRequestBody.m_tags)
                && Objects.equals(this.m_private, spaceRequestBody.m_private);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_description, m_tags, m_private);
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getInstance().getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }
}
