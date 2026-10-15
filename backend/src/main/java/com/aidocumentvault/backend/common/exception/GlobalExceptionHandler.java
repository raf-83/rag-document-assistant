package com.aidocumentvault.backend.common.exception;

import com.aidocumentvault.backend.ai.exception.DocumentChatNotAvailableException;
import com.aidocumentvault.backend.common.api.ApiErrorResponse;
import com.aidocumentvault.backend.document.exception.InvalidDocumentSearchQueryException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
        ResourceNotFoundException exception,
        HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setError(HttpStatus.NOT_FOUND.getReasonPhrase());
        response.setMessage(exception.getMessage());
        response.setPath(request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DocumentChatNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentChatNotAvailable(
        DocumentChatNotAvailableException exception,
        HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setError(HttpStatus.CONFLICT.getReasonPhrase());
        response.setMessage(exception.getMessage());
        response.setPath(request.getRequestURI());

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(response);
    }

    @ExceptionHandler(InvalidDocumentSearchQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidDocumentSearchQuery(
        InvalidDocumentSearchQueryException exception,
        HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setMessage(exception.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
        response.setPath(request.getRequestURI());

        return ResponseEntity
            .badRequest()
            .body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMediaTypeNotSupported(
        HttpMediaTypeNotSupportedException exception,
        HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        response.setError(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase());
        response.setMessage("Unsupported media type");
        response.setPath(request.getRequestURI());

        return ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error(
            "Unhandled exception during {} {}",
            request.getMethod(),
            request.getRequestURI(),
            exception
        );

        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setError(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        response.setMessage("Unexpected server error");
        response.setPath(request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .findFirst()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .orElse("Validation failed");

        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setError(HttpStatus.BAD_REQUEST.getReasonPhrase());
        response.setMessage(message);
        response.setPath(request.getRequestURI());

        return ResponseEntity
            .badRequest()
            .body(response);
    }
}
