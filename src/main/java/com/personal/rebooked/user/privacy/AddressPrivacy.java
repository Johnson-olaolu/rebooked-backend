package com.personal.rebooked.user.privacy;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Controls whether the owner-only address fields (street, Google place data, coordinates) are serialised.
 * <p>
 * Default is the public view (country, state, city only). Endpoints that respond to the address owner
 * about themselves call {@link #showOwnerFields()}. The flag lives in the current request, so anything
 * serialised outside an HTTP request (WebSocket messages, background jobs) always gets the public view.
 */
public final class AddressPrivacy {

    private static final String OWNER_VIEW_ATTRIBUTE = AddressPrivacy.class.getName() + ".ownerView";

    private AddressPrivacy() {}

    public static void showOwnerFields() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(OWNER_VIEW_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public static boolean isOwnerView() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes != null
                && Boolean.TRUE.equals(attributes.getAttribute(OWNER_VIEW_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }
}
