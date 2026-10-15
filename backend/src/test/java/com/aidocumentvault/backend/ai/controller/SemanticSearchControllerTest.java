package com.aidocumentvault.backend.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidocumentvault.backend.ai.dto.SemanticSearchRequest;
import com.aidocumentvault.backend.ai.dto.SemanticSearchResponse;
import com.aidocumentvault.backend.ai.service.SemanticSearchService;
import java.util.List;
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
class SemanticSearchControllerTest {

    @Mock
    private SemanticSearchService semanticSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
            .standaloneSetup(new SemanticSearchController(semanticSearchService))
            .setValidator(validator)
            .build();
    }

    @Test
    void shouldReturnSemanticSearchResponse() throws Exception {
        // given
        when(semanticSearchService.search(any(SemanticSearchRequest.class)))
            .thenReturn(new SemanticSearchResponse(5, 0, List.of()));

        // when / then
        mockMvc.perform(post("/api/search/semantic")
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
            .andExpect(jsonPath("$.resultCount").value(0))
            .andExpect(jsonPath("$.results").isArray());

        ArgumentCaptor<SemanticSearchRequest> requestCaptor =
            ArgumentCaptor.forClass(SemanticSearchRequest.class);

        verify(semanticSearchService).search(requestCaptor.capture());

        SemanticSearchRequest request = requestCaptor.getValue();
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
        mockMvc.perform(post("/api/search/semantic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": " ",
                      "limit": 5,
                      "scopeDocumentIds": []
                    }
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(semanticSearchService);
    }
}
