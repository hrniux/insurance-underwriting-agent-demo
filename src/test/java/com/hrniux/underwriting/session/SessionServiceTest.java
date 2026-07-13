package com.hrniux.underwriting.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hrniux.underwriting.shared.error.ResourceNotFoundException;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "SES-TEST-001");
    }

    @Test
    void createsAnEmptySessionWithAStableId() {
        UnderwritingSession created = service.createSession();

        assertThat(created.id()).isEqualTo("SES-TEST-001");
        assertThat(created.messages()).isEmpty();
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void appendsAnImmutableMessageSnapshot() {
        service.createSession();

        UnderwritingSession updated = service.appendMessage(
                "SES-TEST-001", SessionRole.USER, "请评估保单 P-1001");

        assertThat(updated.messages())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.role()).isEqualTo(SessionRole.USER);
                    assertThat(message.content()).isEqualTo("请评估保单 P-1001");
                    assertThat(message.createdAt()).isEqualTo(NOW);
                });
        assertThatThrownBy(() -> updated.messages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsBlankMessages() {
        service.createSession();

        assertThatThrownBy(() -> service.appendMessage("SES-TEST-001", SessionRole.USER, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void reportsAnUnknownSession() {
        assertThatThrownBy(() -> service.getSession("SES-MISSING"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(error -> assertThat(((ResourceNotFoundException) error).errorCode())
                        .isEqualTo("SESSION_NOT_FOUND"));
    }
}
