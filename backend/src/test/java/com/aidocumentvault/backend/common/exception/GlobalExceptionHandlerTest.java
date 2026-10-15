package com.aidocumentvault.backend.common.exception;

import com.aidocumentvault.backend.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn415WhenMediaTypeIsNotSupported() {
        // given
        HttpMediaTypeNotSupportedException exception =
            mock(HttpMediaTypeNotSupportedException.class);

        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI())
            .thenReturn("/api/documents");

        // when
        ResponseEntity<ApiErrorResponse> response =
            handler.handleHttpMediaTypeNotSupported(exception, request);

        // then
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());

        ApiErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(415, body.getStatus());
        assertEquals("Unsupported Media Type", body.getError());
        assertEquals("Unsupported media type", body.getMessage());
        assertEquals("/api/documents", body.getPath());
        assertNotNull(body.getTimestamp());
    }



    @Test
    void shouldReturn500ForUnexpectedException() {
        // given
        Exception exception = new RuntimeException("boom");

        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRequestURI())
            .thenReturn("/api/documents");

        // when
        ResponseEntity<ApiErrorResponse> response =
            handler.handleGenericException(exception, request);

        // then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ApiErrorResponse body = response.getBody();

        assertNotNull(body);
        assertEquals(500, body.getStatus());
        assertEquals("Internal Server Error", body.getError());
        assertEquals("Unexpected server error", body.getMessage());
        assertEquals("/api/documents", body.getPath());
        assertNotNull(body.getTimestamp());
    }
}
