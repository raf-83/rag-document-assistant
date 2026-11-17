package com.aidocumentvault.backend.processing.message;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
@ContextConfiguration(classes = DocumentProcessingRequestedTest.TestConfiguration.class)
class DocumentProcessingRequestedTest {

    private static final String VALID_JSON = """
        {
          "eventType": "document.processing.requested",
          "schemaVersion": 1,
          "jobId": "10000000-0000-0000-0000-000000000001",
          "documentId": "20000000-0000-0000-0000-000000000002",
          "correlationId": "30000000-0000-0000-0000-000000000003"
        }
        """;

    private final JsonMapper mapper;

    DocumentProcessingRequestedTest(@Autowired JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Test
    void writesAndReadsTheVersionOneContract() throws Exception {
        var message = DocumentProcessingRequested.create(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            UUID.fromString("30000000-0000-0000-0000-000000000003")
        );

        var json = mapper.writeValueAsString(message);

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree(VALID_JSON));
        assertThat(mapper.readValue(VALID_JSON, DocumentProcessingRequested.class))
            .isEqualTo(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "eventType", "schemaVersion", "jobId", "documentId", "correlationId"
    })
    void rejectsMissingRequiredField(String field) throws Exception {
        var payload = (ObjectNode) mapper.readTree(VALID_JSON);
        payload.remove(field);

        assertThatThrownBy(() -> mapper.treeToValue(payload, DocumentProcessingRequested.class))
            .isInstanceOf(DatabindException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "eventType | \"document.deleted\"",
        "schemaVersion | 2",
        "jobId | null"
    }, delimiter = '|')
    void rejectsInvalidFieldValue(String field, String jsonValue) throws Exception {
        var payload = (ObjectNode) mapper.readTree(VALID_JSON);
        payload.set(field, mapper.readTree(jsonValue));

        assertThatThrownBy(() -> mapper.treeToValue(payload, DocumentProcessingRequested.class))
            .isInstanceOf(DatabindException.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
    }
}
