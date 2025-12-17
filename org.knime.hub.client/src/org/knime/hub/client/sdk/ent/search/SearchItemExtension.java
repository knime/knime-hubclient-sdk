package org.knime.hub.client.sdk.ent.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SearchItemExtension extends SearchItem {

    private static final String JSON_PROPERTY_VENDOR = "vendor";
    private static final String JSON_PROPERTY_TRUSTED = "trusted";

    private String m_vendor;
    private Boolean m_trusted;

    @JsonProperty(JSON_PROPERTY_VENDOR)
    public String getVendor() {
        return m_vendor;
    }

    public void setVendor(final String vendor) {
        m_vendor = vendor;
    }

    @JsonProperty(JSON_PROPERTY_TRUSTED)
    public Boolean isTrusted() {
        return m_trusted;
    }

    public void setTrusted(final Boolean trusted) {
        m_trusted = trusted;
    }
}
