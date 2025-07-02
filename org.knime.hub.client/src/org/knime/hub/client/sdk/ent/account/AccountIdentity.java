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
 *   May 19, 2025 (magnus): created
 */
package org.knime.hub.client.sdk.ent.account;

import org.knime.hub.client.sdk.ent.util.ObjectMapperUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * POJO representing the account identity.
 *
 * @author Magnus Gohm, KNIME AG, Konstanz, Germany
 */
@JsonIgnoreProperties(value = AccountIdentity.JSON_PROPERTY_TYPE, allowSetters = true, ignoreUnknown = true)
@JsonTypeInfo(//
        use = JsonTypeInfo.Id.NAME, //
        include = JsonTypeInfo.As.EXISTING_PROPERTY, // Also serialized via #getType().
        property = AccountIdentity.JSON_PROPERTY_TYPE, //
        visible = true, //
        requireTypeIdForSubtypes = OptBoolean.TRUE
)
@JsonSubTypes({ //
        @JsonSubTypes.Type(value = UserAccount.class, name = UserAccount.TYPE) //
})
@JsonPropertyOrder({ AccountIdentity.JSON_PROPERTY_TYPE, AccountIdentity.JSON_PROPERTY_ID }) // Serialize first
public abstract sealed class AccountIdentity permits UserAccount {

    /**
     * Repository item type enum.
     */
    public enum AccountIdentityType {
        /** user type */
        USER(UserAccount.TYPE), //
        /** application type */
        APPLICATION("APPLICATION"),
        /** service type */
        SERVICE("SERVICE");

        private final String m_value;

        AccountIdentityType(final String value) {
            this.m_value = value;
        }

        @JsonValue
        private final String getValue() {
            return m_value;
        }

        @Override
        public String toString() {
            return String.valueOf(m_value);
        }

        @JsonCreator
        private static AccountIdentityType fromValue(final String value) {
            for (AccountIdentityType b : AccountIdentityType.values()) {
                if (b.m_value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }

    /**
     * JSON key name for the item type property
     */
    protected static final String JSON_PROPERTY_TYPE = "type";
    // no member for type, instead use overridden #getType()

    /**
     * JSON key name for the item ID property
     */
    protected static final String JSON_PROPERTY_ID = "id";
    private final String m_id;

    /**
     * An account identity on the KNIME Hub.
     *
     * @param id the ID of the account
     */
    @JsonCreator
    protected AccountIdentity(@JsonProperty(value = JSON_PROPERTY_ID, required = true) final String id) {
        m_id = id;
    }


    /**
     * Retrieves the account ID
     *
     * @return id
     */
    @JsonProperty(JSON_PROPERTY_ID)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public String getId() {
        return m_id;
    }

    /**
     * Retrieves the account identity type.
     *
     * @return type
     */
    @JsonProperty(JSON_PROPERTY_TYPE)
    @JsonInclude(value = JsonInclude.Include.ALWAYS)
    public abstract AccountIdentityType getType();

    @Override
    public String toString() {
        try {
            return ObjectMapperUtil.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON: ", e);
        }
    }

}
