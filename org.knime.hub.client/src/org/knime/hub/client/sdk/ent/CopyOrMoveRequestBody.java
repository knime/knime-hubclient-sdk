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
package org.knime.hub.client.sdk.ent;

import java.util.Objects;
import java.util.Optional;

import org.knime.hub.client.sdk.ent.SpaceRequestBody.SpaceRequestBodyBuilder;
import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Request body for copy or move request.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CopyOrMoveRequestBody {

    private static final String JSON_PROPERTY_CANONICAL_PATH = "canonicalPath";
    private final String m_canonicalPath;

    private static final String JSON_PROPERTY_FORCE = "force";
    private final boolean m_force;

    private static final String JSON_PROPERTY_IF_TARGET_MATCH = "If-Target-Match";
    private final String m_ifTargetMatch;

    @JsonCreator
    private CopyOrMoveRequestBody(
            @JsonProperty(value = JSON_PROPERTY_CANONICAL_PATH) final String canonicalPath,
            @JsonProperty(value = JSON_PROPERTY_FORCE) final boolean force,
            @JsonProperty(value = JSON_PROPERTY_IF_TARGET_MATCH) final String ifTargetMatch) {
        this.m_canonicalPath = canonicalPath;
        this.m_force = force;
        this.m_ifTargetMatch = ifTargetMatch;
    }

    /**
     * Retrieves the new canonical path of the repository item.
     *
     * @return canoncialPath
     */
    @JsonProperty(JSON_PROPERTY_CANONICAL_PATH)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getCanonicalPath() {
        return m_canonicalPath;
    }

    /**
     * Whether to force the copy or move operation, i.e. overwrite existing items.
     *
     * @return tags
     */
    @JsonProperty(JSON_PROPERTY_FORCE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
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
     * Builder for {@link CopyOrMoveRequestBody}.
     */
    public static final class CopyOrMoveRequestBodyBuilder {
        private String m_canonicalPath;
        private boolean m_force;
        private String m_ifTargetMatch;

        private CopyOrMoveRequestBodyBuilder() {
        }

        /**
         * Sets the canonical path.
         *
         * @param canoncialPath the canonical path
         * @return this
         */
        public CopyOrMoveRequestBodyBuilder withCanoncialPath(final String canoncialPath) {
            m_canonicalPath = canoncialPath;
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
         * Builds a new {@link CopyOrMoveRequestBody}.
         *
         * @return copyOrMoveRequestBody
         */
        public CopyOrMoveRequestBody build() {
            return new CopyOrMoveRequestBody(m_canonicalPath, m_force, m_ifTargetMatch);
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
        var copyOrMoveBody = (CopyOrMoveRequestBody) o;
        return Objects.equals(this.m_canonicalPath, copyOrMoveBody.m_canonicalPath)
                && Objects.equals(this.m_force, copyOrMoveBody.m_force)
                && Objects.equals(this.m_ifTargetMatch, copyOrMoveBody.m_ifTargetMatch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_canonicalPath, m_force, m_ifTargetMatch);
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
