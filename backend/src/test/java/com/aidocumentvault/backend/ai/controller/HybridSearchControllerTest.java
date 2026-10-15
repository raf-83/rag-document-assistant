package com.aidocumentvault.backend.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidocumentvault.backend.ai.retrieval.model.HybridMatchType;
import com.aidocumentvault.backend.ai.dto.HybridSearchChunkResponse;
import com.aidocumentvault.backend.ai.dto.HybridSearchRequest;
import com.aidocumentvault.backend.ai.dto.HybridSearchResponse;
import com.aidocumentvault.backend.ai.dto.HybridSearchResultResponse;
import com.aidocumentvault.backend.ai.service.HybridSearchService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class HybridSearchControllerTest {

    @Mock
    private HybridSearchService hybridSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
            .standaloneSetup(new HybridSearchController(hybridSearchService))
            .setValidator(validator)
            .build();
    }

    @Test
    void shouldReturnHybridSearchResponse() throws Exception {
        // given
        UUID documentId =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID documentTypeId =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID chunkId =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(hybridSearchService.search(any(HybridSearchRequest.class)))
            .thenReturn(
                new HybridSearchResponse(
                    5,
                    1,
                    List.of(
                        new HybridSearchResultResponse(
                            documentId,
                            "Decyzja",
                            documentTypeId,
                            "Dokument urzędowy",
                            0.91,
                            0.88,
                            1,
                            List.of(
                                new HybridSearchChunkResponse(
                                    chunkId,
                                    0,
                                    0.88,
                                    0.77,
                                    0.66,
                                    HybridMatchType.HYBRID,
                                    "Podgląd tekstu"
                                )
                            ),
                            null
                        )
                    )
                )
            );

        // when / then
        mockMvc.perform(post("/api/search/hybrid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "ile wynosi zasilek dla bezrobotnych?",
                      "limit": 5,
                      "documentTypeId": null,
                      "createdFrom": null,
                      "createdTo": null,
                      "scopeDocumentIds": []
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.resultCount").value(1))
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results[0].documentId")
                .value(documentId.toString()))
            .andExpect(jsonPath("$.results[0].documentTitle")
                .value("Decyzja"))
            .andExpect(jsonPath("$.results[0].documentTypeId")
                .value(documentTypeId.toString()))
            .andExpect(jsonPath("$.results[0].documentTypeName")
                .value("Dokument urzędowy"))
            .andExpect(jsonPath("$.results[0].score").value(0.91))
            .andExpect(jsonPath("$.results[0].bestChunkScore")
                .value(0.88))
            .andExpect(jsonPath("$.results[0].matchedChunkCount")
                .value(1))
            .andExpect(jsonPath("$.results[0].chunks[0].chunkId")
                .value(chunkId.toString()))
            .andExpect(jsonPath("$.results[0].chunks[0].matchType")
                .value("HYBRID"));

        ArgumentCaptor<HybridSearchRequest> requestCaptor =
            ArgumentCaptor.forClass(HybridSearchRequest.class);

        verify(hybridSearchService).search(requestCaptor.capture());

        HybridSearchRequest request = requestCaptor.getValue();
        assertThat(request.getQuery()).isEqualTo("ile wynosi zasilek dla bezrobotnych?");
        assertThat(request.getLimit()).isEqualTo(5);
        assertThat(request.getDocumentTypeId()).isNull();
        assertThat(request.getCreatedFrom()).isNull();
        assertThat(request.getCreatedTo()).isNull();
        assertThat(request.getScopeDocumentIds()).isEmpty();
    }

    @Test
    void shouldRejectInvalidRequestBeforeCallingService() throws Exception {
        // when / then
        mockMvc.perform(post("/api/search/hybrid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": " ",
                      "limit": 5,
                      "scopeDocumentIds": []
                    }
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(hybridSearchService);
    }
}
