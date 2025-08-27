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
 *   May 9, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.catalog;

import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.catalog.SpaceRequestBody.SpaceRequestBodyBuilder;
import org.knime.hub.client.sdk.ent.util.EntityUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for space rename request.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SpaceRenameRequestBody {

    private static final String JSON_PROPERTY_NAME = "name";
    private final String m_name;

    private static final String JSON_PROPERTY_FORCE = "force";
    private final boolean m_force;

    private static final String JSON_PROPERTY_IF_TARGET_MATCH = "If-Target-Match";
    private final String m_ifTargetMatch;

    @JsonCreator
    private SpaceRenameRequestBody(
            @JsonProperty(value = JSON_PROPERTY_NAME, required = true) final String canonicalPath,
            @JsonProperty(value = JSON_PROPERTY_FORCE) final boolean force,
            @JsonProperty(value = JSON_PROPERTY_IF_TARGET_MATCH) final String ifTargetMatch) {
        this.m_name = canonicalPath;
        this.m_force = force;
        this.m_ifTargetMatch = ifTargetMatch;
    }

    /**
     * Retrieves the new name of the space.
     *
     * @return name
     */
    @JsonProperty(JSON_PROPERTY_NAME)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getName() {
        return m_name;
    }

    /**
     * Whether to force the rename operation.
     *
     * @return tags
     */
    @JsonProperty(JSON_PROPERTY_FORCE)
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public boolean isForce() {
        return m_force;
    }

    /**
     * Returns the If-Target-Match header.
     *
     * @return If-Target-Match header
     */
    @JsonProperty(JSON_PROPERTY_IF_TARGET_MATCH)
    @JsonInclude(value = JsonInclude.Include.NON_ABSENT)
    public Optional<String> getIfTargetMatch() {
        return Optional.ofNullable(m_ifTargetMatch);
    }

    /**
     * Creates a new {@link SpaceRequestBodyBuilder}.
     *
     * @return builder
     */
    public static CopyOrMoveRequestBodyBuilder builder() {
        return new CopyOrMoveRequestBodyBuilder();
    }

    /**
     * Builder for {@link SpaceRenameRequestBody}.
     */
    public static final class CopyOrMoveRequestBodyBuilder {
        private String m_name;
        private boolean m_force;
        private String m_ifTargetMatch;

        private CopyOrMoveRequestBodyBuilder() {
        }

        /**
         * Sets the space name.
         *
         * @param name the name
         * @return this
         */
        public CopyOrMoveRequestBodyBuilder withName(final String name) {
            m_name = name;
            return this;
        }

        /**
         * Sets the force parameter.
         *
         * @param force the force parameter
         * @return this
         */
        public CopyOrMoveRequestBodyBuilder withForce(final boolean force) {
            m_force = force;
            return this;
        }

        /**
         * Sets a value for the IF-Target-Match header.
         *
         * @param ifTargetMatch the if target match header value
         * @return this
         */
        public CopyOrMoveRequestBodyBuilder withIfTargetMatch(final String ifTargetMatch) {
            m_ifTargetMatch = ifTargetMatch;
            return this;
        }

        /**
         * Builds a new {@link SpaceRenameRequestBody}.
         *
         * @return spaceRenameRequestBody
         */
        public SpaceRenameRequestBody build() {
            return new SpaceRenameRequestBody(m_name, m_force, m_ifTargetMatch);
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
        var spaceRenameBody = (SpaceRenameRequestBody) o;
        return Objects.equals(this.m_name, spaceRenameBody.m_name)
                && Objects.equals(this.m_force, spaceRenameBody.m_force)
                && Objects.equals(this.m_ifTargetMatch, spaceRenameBody.m_ifTargetMatch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_name, m_force, m_ifTargetMatch);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
