// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.mapper;

import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface ChangeEventMapper {

    ObjectMapper OBJECT_MAPPER = MapperUtil.getObjectMapper();

    @Mapping(source = "eventAction", target = "auditType")
    @Mapping(source = "eventId", target = "id")
    @Mapping(target = "clientSyncStatus", ignore = true)
    ChangeEventEntity toEntity(ChangeEventView view);

    @Mapping(source = "auditType", target = "eventAction")
    @Mapping(source = "id", target = "eventId")
    @Mapping(source = "clientSyncStatus", target = "syncStatus")
    ChangeEventView toView(ChangeEventEntity entity, @Context String clientId);

    /**
     * Convert Map<String, String> clientSyncStatus to single ChangeEventStatus
     * Returns the first status found, or UNPROCESSED if empty
     */
    default ChangeEventStatus mapClientSyncStatusToSyncStatus(Map<String, String> clientSyncStatus, @Context String clientId) {
        if (CollectionUtils.isEmpty(clientSyncStatus) || !clientSyncStatus.containsKey(clientId)) {
            return ChangeEventStatus.getDefault();
        }
        try {
            return ChangeEventStatus.valueOf(clientSyncStatus.get(clientId));
        } catch (IllegalArgumentException e) {
            return ChangeEventStatus.getDefault();
        }
    }

    // Timestamp conversions
    default LocalDateTime map(Instant value) {
        return value != null ? LocalDateTime.ofInstant(value, ZoneId.systemDefault()) : null;
    }

    default Instant map(LocalDateTime value) {
        return value != null ? value.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    // Type conversions Long <-> Integer for eventId
    default Integer mapLongToInteger(Long value) {
        return value != null ? value.intValue() : null;
    }

    default Long mapIntegerToLong(Integer value) {
        return value != null ? value.longValue() : null;
    }

    // Enum to String conversions
    default String map(ChangeEventType value) {
        return value != null ? value.name() : null;
    }

    default ChangeEventType mapStringToEventType(String value) {
        return value != null ? ChangeEventType.valueOf(value) : null;
    }

    default String map(ChangeEventAction value) {
        return value != null ? value.name() : null;
    }

    default ChangeEventAction mapStringToEventAction(String value) {
        return value != null ? ChangeEventAction.valueOf(value) : null;
    }

    // JSON conversion for changes field
    default Object map(ChangeEventDetails value) {
        try {
            return value != null ? OBJECT_MAPPER.writeValueAsString(value) : null;
        } catch (Exception e) {
            throw new RuntimeException("Error converting ChangeEventDetails to JSON", e);
        }
    }

    default ChangeEventDetails mapObjectToChangeEventDetails(Object value) {
        try {
            if (value == null) return null;
            if (value instanceof String stringValue) {
                return OBJECT_MAPPER.readValue(stringValue, ChangeEventDetails.class);
            } else {
                String json = OBJECT_MAPPER.writeValueAsString(value);
                return OBJECT_MAPPER.readValue(json, ChangeEventDetails.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error converting Object to ChangeEventDetails", e);
        }
    }
}
