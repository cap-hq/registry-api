// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.service.audit;

import com.cvshealth.cap.registry.mapper.ChangeEventMapper;
import com.cvshealth.cap.registry.model.PaginationInput;
import com.cvshealth.cap.registry.model.audit.*;
import com.cvshealth.cap.registry.persistent.ChangeEventEntity;
import com.cvshealth.cap.registry.repository.ChangeEventRepository;
import com.cvshealth.cap.registry.repository.specification.CustomPostgresSQLDialect;
import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeEventSchemaCompatibilityTest {
    private static final String CLIENT_ID = "schema-compatibility-client";
    private final ChangeEventMapper mapper = Mappers.getMapper(ChangeEventMapper.class);

    @Test
    void auditWritesReadsAndSyncWorkAgainstMigratedSchema() {
        // Never alter the shared/reusable integration-test database.
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            try (SessionFactory sessionFactory = new Configuration()
                    .addAnnotatedClass(ChangeEventEntity.class)
                    .setProperty("hibernate.connection.url", postgres.getJdbcUrl())
                    .setProperty("hibernate.connection.username", postgres.getUsername())
                    .setProperty("hibernate.connection.password", postgres.getPassword())
                    .setProperty("hibernate.dialect", CustomPostgresSQLDialect.class.getName())
                    .setProperty("hibernate.hbm2ddl.auto", "validate")
                    .buildSessionFactory()) {
                ChangeEventDetails details = ChangeEventDetails.builder()
                        .oldValues(Map.of())
                        .newValues(Map.of("name", "schema-compatible-api"))
                        .context(Map.of("source", "compatibility-test"))
                        .build();
                ChangeEventView input = ChangeEventView.builder()
                        .entityId(123L)
                        .eventType(ChangeEventType.API)
                        .eventAction(ChangeEventAction.CREATE)
                        .changes(details)
                        .build();

                ChangeEventView saved = sessionFactory.fromTransaction(session -> service(session).saveChangeEvent(input));
                assertThat(saved.getEventId()).isNotNull();
                assertThat(saved.getCreateTimestamp()).isNotNull();
                assertThat(saved.getChanges()).isEqualTo(details);

                jdbc.update("UPDATE audit_log SET client_sync_status = hstore('other-client', 'ERROR') WHERE id = ?",
                        saved.getEventId());

                ChangeEventGetRequest request = ChangeEventGetRequest.builder()
                        .clientId(CLIENT_ID)
                        .eventTypes(List.of(ChangeEventType.API))
                        .syncStatus(ChangeEventStatus.UNPROCESSED)
                        .entityId(123)
                        .build();
                var response = sessionFactory.fromTransaction(session ->
                        service(session).getChangeEvents(new PaginationInput(1, 10), request));
                assertThat(response.getChangeEvents()).singleElement().satisfies(event -> {
                    assertThat(event.getEventId()).isEqualTo(saved.getEventId());
                    assertThat(event.getEntityId()).isEqualTo(123L);
                    assertThat(event.getEventType()).isEqualTo(ChangeEventType.API);
                    assertThat(event.getEventAction()).isEqualTo(ChangeEventAction.CREATE);
                    assertThat(event.getChanges()).isEqualTo(details);
                    assertThat(event.getSyncStatus()).isEqualTo(ChangeEventStatus.UNPROCESSED);
                });

                ChangeEventView synced = sessionFactory.fromTransaction(session ->
                        service(session).syncChangeEvent(ChangeEventUpdateRequest.builder()
                                .eventId(saved.getEventId())
                                .clientId(CLIENT_ID)
                                .syncStatus(ChangeEventStatus.PROCESSED)
                                .build()));
                assertThat(synced.getSyncStatus()).isEqualTo(ChangeEventStatus.PROCESSED);
                assertThat(synced.getVersion()).isEqualTo(saved.getVersion() + 1);

                ChangeEventEntity persisted = sessionFactory.fromTransaction(session ->
                        session.find(ChangeEventEntity.class, saved.getEventId()));
                assertThat(persisted.getClientSyncStatus())
                        .containsExactlyInAnyOrderEntriesOf(Map.of(CLIENT_ID, "PROCESSED", "other-client", "ERROR"));
                assertThat(mapper.mapObjectToChangeEventDetails(persisted.getChanges())).isEqualTo(details);
                assertThat(persisted.getCreateTimestamp()).isEqualTo(response.getChangeEvents().getFirst().getCreateTimestamp());
                assertThat(persisted.getUpdateTimestamp()).isNotNull();
            }
        }
    }

    private ChangeEventService service(Session session) {
        ChangeEventRepository repository = new JpaRepositoryFactory(session).getRepository(ChangeEventRepository.class);
        return new ChangeEventService(repository, mapper);
    }
}
