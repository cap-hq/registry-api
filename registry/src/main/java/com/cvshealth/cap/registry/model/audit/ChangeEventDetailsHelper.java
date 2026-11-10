// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.model.audit;

import java.util.Map;
import java.util.Optional;

import static java.util.Optional.empty;

public class ChangeEventDetailsHelper {

    public static <T> Optional<T> getFromContext(ChangeEventDetails changeEventDetails, String outerKey, String innerKey, Class<T> type) {
        if (changeEventDetails == null) return empty();
        return getOptional(changeEventDetails.getContext(), outerKey, innerKey, type);
    }

    public static <T> Optional<T> getOptional(Map<String, ?> map, String outerKey, String innerKey, Class<T> type) {
        Map<?, ?> nested = getOptional(map, outerKey, Map.class).orElse(null);
        return getOptional(nested, innerKey, type);
    }

    public static <T> Optional<T> getOptional(Map<?, ?> map, String key, Class<T> type) {
        if (map == null) return empty();
        if (!map.containsKey(key)) return empty();
        Object value = map.get(key);
        if (value == null) return empty();
        if (!type.isInstance(value)) return empty();
        return Optional.of(type.cast(value));
    }

}
