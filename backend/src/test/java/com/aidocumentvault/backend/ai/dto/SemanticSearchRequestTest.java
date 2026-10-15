package com.aidocumentvault.backend.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SemanticSearchRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptValidSemanticSearchRequest() {
        // given
        UUID documentTypeId = UUID.randomUUID();
        UUID scopedDocumentId = UUID.randomUUID();
        OffsetDateTime createdFrom = OffsetDateTime.parse("2026-07-01T10:00:00Z");
        OffsetDateTime createdTo = OffsetDateTime.parse("2026-07-06T10:00:00Z");

        SemanticSearchRequest request = new SemanticSearchRequest(
            "ile wynosi zasilek dla bezrobotnych?",
            5,
            documentTypeId,
            createdFrom,
            createdTo,
            Set.of(scopedDocumentId)
        );

        // when
        Set<ConstraintViolation<SemanticSearchRequest>> violations =
            validator.validate(request);

        // then
        assertThat(violations).isEmpty();
        assertThat(request.getQuery()).isEqualTo("ile wynosi zasilek dla bezrobotnych?");
        assertThat(request.getLimit()).isEqualTo(5);
        assertThat(request.getDocumentTypeId()).isEqualTo(documentTypeId);
        assertThat(request.getCreatedFrom()).isEqualTo(createdFrom);
        assertThat(request.getCreatedTo()).isEqualTo(createdTo);
        assertThat(request.getScopeDocumentIds()).containsExactly(scopedDocumentId);
    }

    @Test
    void shouldNormalizeQueryAndApplyEffectiveDefaults() {
        // given
        SemanticSearchRequest request = new SemanticSearchRequest();
        request.setQuery("  ile wynosi zasilek dla bezrobotnych?  ");

        // when
        String normalizedQuery = request.normalizedQuery();
        int effectiveLimit = request.effectiveLimit();
        Set<UUID> effectiveScopeDocumentIds = request.effectiveScopeDocumentIds();

        // then
        assertThat(normalizedQuery).isEqualTo("ile wynosi zasilek dla bezrobotnych?");
        assertThat(effectiveLimit).isEqualTo(SemanticSearchRequest.DEFAULT_LIMIT);
        assertThat(effectiveScopeDocumentIds).isEmpty();
    }

    @Test
    void shouldAcceptNullOptionalFilters() {
        // given
        SemanticSearchRequest request = new SemanticSearchRequest(
            "ile wynosi zasilek dla bezrobotnych?",
            null,
            null,
            null,
            null,
            null
        );

        // when
        Set<ConstraintViolation<SemanticSearchRequest>> violations =
            validator.validate(request);

        // then
        assertThat(violations).isEmpty();
        assertThat(request.effectiveLimit()).isEqualTo(SemanticSearchRequest.DEFAULT_LIMIT);
        assertThat(request.effectiveScopeDocumentIds()).isEmpty();
    }

    @Test
    void shouldRejectBlankQuery() {
        // given
        SemanticSearchRequest request = validRequest();
        request.setQuery(" ");

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("Search query must not be blank");
    }

    @Test
    void shouldRejectTooShortQuery() {
        // given
        SemanticSearchRequest request = validRequest();
        request.setQuery("a");

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("Search query must be between 2 and 1000 characters");
    }

    @Test
    void shouldRejectLimitBelowMinimum() {
        // given
        SemanticSearchRequest request = validRequest();
        request.setLimit(0);

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("Limit must be at least 1");
    }

    @Test
    void shouldRejectLimitAboveMaximum() {
        // given
        SemanticSearchRequest request = validRequest();
        request.setLimit(SemanticSearchRequest.MAX_LIMIT + 1);

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("Limit must not exceed 50");
    }

    @Test
    void shouldRejectCreatedFromAfterCreatedTo() {
        // given
        SemanticSearchRequest request = validRequest();
        request.setCreatedFrom(OffsetDateTime.parse("2026-07-06T10:00:00Z"));
        request.setCreatedTo(OffsetDateTime.parse("2026-07-01T10:00:00Z"));

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("createdFrom must be before or equal to createdTo");
    }

    @Test
    void shouldRejectTooManyScopeDocumentIds() {
        // given
        SemanticSearchRequest request = validRequest();
        request.setScopeDocumentIds(scopeDocumentIds(SemanticSearchRequest.MAX_SCOPE_DOCUMENTS + 1));

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("Scope document ids cannot contain more than 100 items");
    }

    @Test
    void shouldRejectNullScopeDocumentId() {
        // given
        Set<UUID> scopeDocumentIds = new LinkedHashSet<>();
        scopeDocumentIds.add(UUID.randomUUID());
        scopeDocumentIds.add(null);

        SemanticSearchRequest request = validRequest();
        request.setScopeDocumentIds(scopeDocumentIds);

        // when
        Set<String> messages = violationMessagesFor(request);

        // then
        assertThat(messages).contains("Scope document id must not be null");
    }

    private static SemanticSearchRequest validRequest() {
        return new SemanticSearchRequest(
            "ile wynosi zasilek dla bezrobotnych?",
            5,
            UUID.randomUUID(),
            OffsetDateTime.parse("2026-07-01T10:00:00Z"),
            OffsetDateTime.parse("2026-07-06T10:00:00Z"),
            Set.of(UUID.randomUUID())
        );
    }

    private static Set<String> violationMessagesFor(SemanticSearchRequest request) {
        return validator.validate(request)
            .stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());
    }

    private static Set<UUID> scopeDocumentIds(int count) {
        Set<UUID> documentIds = new LinkedHashSet<>();

        for (int index = 0; index < count; index++) {
            documentIds.add(UUID.randomUUID());
        }

        return documentIds;
    }
}
