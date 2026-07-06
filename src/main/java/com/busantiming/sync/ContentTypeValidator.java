package com.busantiming.sync;

import java.util.Set;

public class ContentTypeValidator {

    private final Set<Integer> validIds;

    public ContentTypeValidator(Set<Integer> validIds) {
        this.validIds = validIds;
    }

    public Integer parseAndValidate(String contentTypeId) {
        if (contentTypeId == null || contentTypeId.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(contentTypeId.trim());
            return validIds.contains(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isValid(String contentTypeId) {
        return parseAndValidate(contentTypeId) != null;
    }
}
