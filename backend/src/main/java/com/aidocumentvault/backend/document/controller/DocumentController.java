package com.aidocumentvault.backend.document.controller;

import com.aidocumentvault.backend.document.dto.DocumentContentResponse;
import com.aidocumentvault.backend.document.dto.DocumentResponse;
import com.aidocumentvault.backend.document.dto.UploadDocumentRequest;
import com.aidocumentvault.backend.document.entity.DocumentStatus;
import com.aidocumentvault.backend.document.service.DocumentService;
import com.aidocumentvault.backend.storage.dto.StoredFileDownload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/{id}")
    public DocumentResponse findById(@PathVariable UUID id) {
        return documentService.getMyResponseByIdOrThrow(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadDocumentFile(@PathVariable UUID id) {
        StoredFileDownload download = documentService.downloadMyDocumentFile(id);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.mimeType()))
            .contentLength(download.sizeInBytes())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(download.originalFileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(download.resource());
    }

    @GetMapping
    public List<DocumentResponse> findAll(
        @RequestParam(required = false) DocumentStatus status
    ) {
        return status == null
            ? documentService.findAllMyResponses()
            : documentService.findAllMyResponsesByStatus(status);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(
        @RequestPart("file") MultipartFile file,
        @Valid @RequestPart("metadata") UploadDocumentRequest metadata
    ) {
        return documentService.uploadDocument(file, metadata);
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<DocumentContentResponse> getDocumentContent(
        @PathVariable UUID documentId
    ) {
        return ResponseEntity.ok(documentService.getDocumentContent(documentId));
    }

}
