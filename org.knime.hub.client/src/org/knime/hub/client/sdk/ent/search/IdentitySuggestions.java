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
 * Response payload returned by {@code GET /suggestions/identities}.
 *
 * @author Benjamin Moser, KNIME GmbH, Konstanz, Germany
 * @since 1.4
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IdentitySuggestions {

    private static final String JSON_PROPERTY_USERS = "users";
    private static final String JSON_PROPERTY_TEAMS = "teams";
    private static final String JSON_PROPERTY_EXTERNAL_GROUPS = "externalGroups";

    private final List<AccountSearchItem> m_users;
    private final List<AccountSearchItem> m_teams;
    private final List<AccountSearchItem> m_externalGroups;

    /**
     * Identity suggestions grouped by account category.
     *
     * @param users matching user accounts
     * @param teams matching teams
     * @param externalGroups matching external groups
     */
    @JsonCreator
    public IdentitySuggestions(
        @JsonProperty(value = JSON_PROPERTY_USERS, required = true) final List<AccountSearchItem> users,
        @JsonProperty(value = JSON_PROPERTY_TEAMS, required = true) final List<AccountSearchItem> teams,
        @JsonProperty(value = JSON_PROPERTY_EXTERNAL_GROUPS, required = true) final List<AccountSearchItem> externalGroups) {
        m_users = Objects.requireNonNull(users, JSON_PROPERTY_USERS + " must not be null");
        m_teams = Objects.requireNonNull(teams, JSON_PROPERTY_TEAMS + " must not be null");
        m_externalGroups = Objects.requireNonNull(externalGroups, JSON_PROPERTY_EXTERNAL_GROUPS + " must not be null");
    }

    /**
     * Returns the matching users.
     *
     * @return matching users
     */
    @JsonProperty(JSON_PROPERTY_USERS)
    public List<AccountSearchItem> getUsers() {
        return m_users;
    }

    /**
     * Returns the matching teams.
     *
     * @return matching teams
     */
    @JsonProperty(JSON_PROPERTY_TEAMS)
    public List<AccountSearchItem> getTeams() {
        return m_teams;
    }

    /**
     * Returns the matching external groups.
     *
     * @return matching external groups
     */
    @JsonProperty(JSON_PROPERTY_EXTERNAL_GROUPS)
    public List<AccountSearchItem> getExternalGroups() {
        return m_externalGroups;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (IdentitySuggestions)o;
        return Objects.equals(m_users, that.m_users)
            && Objects.equals(m_teams, that.m_teams)
            && Objects.equals(m_externalGroups, that.m_externalGroups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_users, m_teams, m_externalGroups);
    }

    @Override
    public String toString() {
        return EntityUtil.toString(this);
    }
}
