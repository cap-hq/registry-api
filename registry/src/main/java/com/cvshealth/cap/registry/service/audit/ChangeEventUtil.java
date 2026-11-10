// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.model.*;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.model.exception.ApiException;
import com.cvshealth.cap.registry.model.exception.BadRequestException;
import com.cvshealth.cap.registry.model.exception.ExceptionReason;
import com.cvshealth.cap.registry.persistent.TeamEntity;
import com.cvshealth.cap.registry.persistent.base.UpdatableEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

import static com.cvshealth.cap.registry.model.audit.ChangeEventType.BUSINESS_LINE;

@Slf4j
public class ChangeEventUtil {

    private static final Set<String> ENTITY_METADATA_KEYS = updatableEntityKeys();

    public static ChangeEventDetails toChangeEventDetails(Map<String, Object> before, Object after) {
        return toChangeEventDetails(before, after, null);
    }

    public static ChangeEventDetails toChangeEventDetails(Map<String, Object> before, Object after, Map<String, Object> context) {
        if (before == null) {
            before = Collections.emptyMap();
        }
        return ChangeEventDetails.builder().oldValues(before).newValues(snapshot(after)).context(context).build();
    }

    public static ChangeEventDetails toChangeEventDetailsFromMaps(Map<String, Object> before, Map<String, Object> after, Map<String, Object> context) {
        if (before == null) {
            before = Collections.emptyMap();
        }
        if (after == null) {
            after = Collections.emptyMap();
        }
        return ChangeEventDetails.builder()
                .oldValues(new HashMap<>(before))
                .newValues(new HashMap<>(after))
                .context(context)
                .build();
    }

    static void excludeMetadata(Map<String, Object> values) {
        ENTITY_METADATA_KEYS.forEach(values::remove);
    }

    public static ChangeEventDetails reduce(final ChangeEventDetails eventDetails, boolean deltasOnly, boolean excludeMetadata) {
        final Map<String, Object> oldValues = deltasOnly ? new HashMap<>() : eventDetails.getOldValues();
        final Map<String, Object> newValues = deltasOnly ? new HashMap<>() : eventDetails.getNewValues();
        if (excludeMetadata) {
            excludeMetadata(oldValues);
            excludeMetadata(newValues);
        }
        if (deltasOnly) {
            Map<String, ModelPair<Object, Object>> delta = delta(eventDetails.getOldValues(), eventDetails.getNewValues());
            delta.forEach((key, value) -> {
                oldValues.put(key, value.getFirst());
                newValues.put(key, value.getSecond());
            });
        }
        return ChangeEventDetails.builder().deltasOnly(deltasOnly).metadataExcluded(excludeMetadata)
                .oldValues(oldValues).newValues(newValues).context(eventDetails.getContext()).build();
    }

    public static Map<String, Object> snapshot(Object entity) {
        if (entity == null) {
            return Collections.emptyMap();
        }
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(entity.getClass(), Object.class);
            Map<String, Object> values = new HashMap<>();
            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                Method readMethod = pd.getReadMethod();
                if (readMethod != null) {
                    Object value = readMethod.invoke(entity);
                    if (value != null) {
                        if (isScalar(value)) {
                            values.put(pd.getName(), value);
                        } else if ("getId".equals(readMethod.getName())) {  // flatten EmbeddedId to include
                            values.putAll(snapshot(readMethod.invoke(entity)));
                        }
                    }
                }
            }
            return values;
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            throw new BadRequestException(ExceptionReason.UNEXPECTED_ERROR);
        }
    }

    // ignore collections
    static boolean isScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Number
                || value instanceof Instant
                || value.getClass().isEnum();
    }

    // compute delta
    static Map<String, ModelPair<Object, Object>> delta(Map<String, Object> oldValues, Map<String, Object> newValues) {
        Map<String, ModelPair<Object, Object>> delta = new HashMap<>();
        Set<String> keys = new LinkedHashSet<>(oldValues.keySet());
        keys.addAll(newValues.keySet());
        for (String key : keys) {
            Object oldValue = oldValues.get(key);
            Object newValue = newValues.get(key);
            if (!Objects.deepEquals(oldValue, newValue)) {
                delta.put(key, ModelPair.builder().first(oldValue).second(newValue).build());
            }
        }
        return  delta;
    }

    // Return the property map from the entity for only a given interface
    public static <I> Map<String, Object> isnapshot(I entity, Class<I> clazz) {
        if (entity == null) {
            return Collections.emptyMap();
        }
        try {
            if (!clazz.isInterface()) {
                log.error("invalid mapping type");
                throw error("unable to process request");
            }
            BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
            Map<String, Object> values = new HashMap<>();
            for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                Method readMethod = pd.getReadMethod();
                if (readMethod != null) {
                    Object value = readMethod.invoke(entity);
                    if (value == null || isScalar(value)) {
                        values.put(pd.getName(), value);
                    }
                }
            }
            return values;
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            log.error("unable to map entity for audit logging", e);
            throw error("unable to process request");
        }
    }

    static ApiException error(String message) {
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "request-audit",
                ExceptionReason.UNEXPECTED_ERROR, message);
    }

    public static Map<String, Object> identifiableSnapshot(IdentifiableEntity entity) {
        return isnapshot(entity, IdentifiableEntity.class);
    }

    public static Map<String, Object> identifiableSnapshot(IdentifiableUser entity) {
        return isnapshot(entity, IdentifiableUser.class);
    }

    static ChangeEventType toChangeEventType(AuditEntry auditEntry) {
        if (auditEntry == null) {
            return null;
        }
        AuditEntity subject = auditEntry.getSubjectEntity();
        if (subject != null) {
            return switch (subject.getEntityType()) {
                case API -> ChangeEventType.API;
                case API_CONSUMER ->  ChangeEventType.API_CONSUMER;
                case TEAM -> ChangeEventType.TEAM;
                case APPLICATION -> ChangeEventType.APPLICATION;
                case MEMBER -> {
                    if (auditEntry.getTargetEntity() == null) {
                        yield ChangeEventType.MEMBER;
                    } else if (AuditEntityType.TEAM == auditEntry.getTargetEntity().getEntityType()) {
                        yield ChangeEventType.TEAM_MEMBER;
                    } else {
                        yield null;
                    }
                }
                case BUSINESS_LINE -> ChangeEventType.BUSINESS_LINE;
                case AUTH_CLIENT -> ChangeEventType.AUTH_CLIENT;
                default -> null;
            };
        }
        return null;
    }

    static ChangeEventAction toChangeEventAction(AuditEntry auditEntry) {
        if (auditEntry == null) {
            return null;
        }
        return switch (auditEntry.getActionType()) {
            case CREATE, CREATE_TRANSFER -> ChangeEventAction.CREATE;
            case UPDATE, UPDATE_ON, UPDATE_TRANSFER, RESET -> ChangeEventAction.UPDATE;
            case DELETE -> ChangeEventAction.DELETE;
            case ADD_TO -> ChangeEventAction.ADD;
            case REMOVE_FROM -> ChangeEventAction.REMOVE;
        };
    }

    static Long toChangeEntityId(AuditEntry auditEntry) {
        if (auditEntry == null) {
            return null;
        }
        AuditEntity subject = auditEntry.getSubjectEntity();
        if (subject != null) {
            return switch (subject.getEntityType()) {
                case TEAM, APPLICATION, AUTH_CLIENT, BUSINESS_LINE, API -> (Long) subject.getId();
                case API_CONSUMER -> {
                    // Use the API ID (targetEntity) as entityId, consistent with API lifecycle events
                    if (auditEntry.getTargetEntity() != null && AuditEntityType.API == auditEntry.getTargetEntity().getEntityType()) {
                        yield (Long) auditEntry.getTargetEntity().getId();
                    }
                    yield (Long) subject.getId();
                }
                case MEMBER -> {
                    if (auditEntry.getTargetEntity() == null) {
                        yield (Long) subject.getId();   // MEMBER
                    } else if (AuditEntityType.TEAM == auditEntry.getTargetEntity().getEntityType()) {
                        yield (Long) auditEntry.getTargetEntity().getId();  // TEAM_MEMBER
                    } else {
                        yield null;
                    }
                }
            };
        }
        return null;
    }

    static Set<String> updatableEntityKeys() {
        Set<String> keys = new LinkedHashSet<>();
        try {
            BeanInfo info = Introspector.getBeanInfo(UpdatableEntity.class);
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                Method writeMethod = pd.getWriteMethod();
                if (writeMethod != null && writeMethod.getDeclaringClass().isAssignableFrom(UpdatableEntity.class)) {
                    keys.add(pd.getName());
                }
            }
        } catch (IntrospectionException e) {
            log.error("Unavailable to determine the entity metadata fields for audit", e);
        }
        return keys;
    }

    public static Map<String, Object> teamContext(IdentifiableEntity teamEntity) {
        Map<String, Object> teamContext = identifiableSnapshot(teamEntity);
        if (teamEntity instanceof Team team) {
            teamContext.put("type", team.getType());
            teamContext.put("externalName", team.getExternalName());
        } else if (teamEntity instanceof TeamView teamView) {
            teamContext.put("type", teamView.getType());
            teamContext.put("externalName", teamView.getExternalName());
        } else if (teamEntity instanceof TeamEntity entity) {
            teamContext.put("type", entity.getType());
            teamContext.put("externalName", entity.getExternalName());
        } else if (teamEntity instanceof MemberViewTeam entity) {
            teamContext.put("type", entity.getType());
            teamContext.put("externalName", entity.getExternalName());
        }
        return teamContext;
    }
}