package com.personal.rebooked.user.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.personal.rebooked.user.privacy.OwnerOnlyFilter;
import lombok.Data;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;

/**
 * Structured Nigerian address. country, state and city are public.
 * The other fields are only serialised for the owner (see {@link com.personal.rebooked.user.privacy.AddressPrivacy}).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Address {
    /** ISO code. Only "NG" for now. */
    private String country;

    /** Canonical name from Constants.NIGERIAN_STATES. */
    private String state;

    private String city;

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = OwnerOnlyFilter.class)
    private String addressLine;

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = OwnerOnlyFilter.class)
    private String formattedAddress;

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = OwnerOnlyFilter.class)
    private String placeId;

    /** GeoJSON point: x = longitude, y = latitude. Never serialised; clients use latitude/longitude. */
    @JsonIgnore
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    @JsonProperty("latitude")
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = OwnerOnlyFilter.class)
    public Double getLatitude() {
        return location == null ? null : location.getY();
    }

    @JsonProperty("longitude")
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = OwnerOnlyFilter.class)
    public Double getLongitude() {
        return location == null ? null : location.getX();
    }
}
