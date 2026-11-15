package com.personal.rebooked.user.privacy;

/**
 * Jackson value filter for {@code @JsonInclude(value = CUSTOM, valueFilter = OwnerOnlyFilter.class)}.
 * Jackson calls {@code equals(value)}: returning true leaves the property out of the JSON.
 * Null values are always left out, so the decision Jackson caches for nulls never depends on the request.
 */
public class OwnerOnlyFilter {

    @Override
    public boolean equals(Object value) {
        return value == null || !AddressPrivacy.isOwnerView();
    }

    @Override
    public int hashCode() {
        return OwnerOnlyFilter.class.hashCode();
    }
}
