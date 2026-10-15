package com.aidocumentvault.backend.ai.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchRequest {

    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;
    public static final int MAX_SCOPE_DOCUMENTS = 100;

    @NotBlank(message = "Search query must not be blank")
    @Size(min = 2, max = 1000, message = "Search query must be between 2 and 1000 characters")
    private String query;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = MAX_LIMIT, message = "Limit must not exceed 50")
    private Integer limit;

    private UUID documentTypeId;

    private OffsetDateTime createdFrom;

    private OffsetDateTime createdTo;

    @Size(max = MAX_SCOPE_DOCUMENTS, message = "Scope document ids cannot contain more than 100 items")
    private Set<@NotNull(message = "Scope document id must not be null") UUID> scopeDocumentIds;

    public String normalizedQuery() {
        return query == null ? null : query.trim();
    }

    public int effectiveLimit() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    public Set<UUID> effectiveScopeDocumentIds() {
        return scopeDocumentIds == null ? Set.of() : Set.copyOf(scopeDocumentIds);
    }

    @AssertTrue(message = "createdFrom must be before or equal to createdTo")
    public boolean isCreatedDateRangeValid() {
        if (createdFrom == null || createdTo == null) {
            return true;
        }

        return !createdFrom.isAfter(createdTo);
    }
}
