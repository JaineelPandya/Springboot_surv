package com.surv.platform.document;

import com.surv.platform.common.response.ApiResponse;
import com.surv.platform.document.dto.*;
import com.surv.platform.dto.DocumentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Tag(name = "Documents", description = "Document registration, classification, versioning and recipient management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Register and upload a new document (auto MIME detection + classification)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentUploadResult>> register(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") @Valid DocumentRegisterRequest request,
            Authentication auth) throws IOException {
        DocumentUploadResult result = documentService.registerDocument(
                request, file.getBytes(), file.getOriginalFilename(), auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Document registered successfully"));
    }

    @Operation(summary = "Get document by ID (owner, recipient, or elevated role)")
    @GetMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentDto>> get(
            @PathVariable UUID documentId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(documentService.getDocument(documentId, auth)));
    }

    @Operation(summary = "List documents — EMPLOYEE sees own only; elevated roles see all or filter by owner")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> list(
            @RequestParam(required = false) UUID ownerId,
            @ParameterObject Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(documentService.listDocuments(ownerId, pageable, auth)));
    }

    @Operation(summary = "Manually reclassify a document's sensitivity level (elevated roles only)")
    @PatchMapping("/{documentId}/classify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPLIANCE_OFFICER','SECURITY_ANALYST')")
    public ResponseEntity<ApiResponse<DocumentDto>> classify(
            @PathVariable UUID documentId,
            @Valid @RequestBody ClassifyRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.reclassify(documentId, request, auth)));
    }

    @Operation(summary = "Upload a new version of an existing document")
    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DocumentDto>> addVersion(
            @PathVariable UUID documentId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "New version") String changeSummary,
            Authentication auth) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                documentService.addVersion(documentId, file.getBytes(),
                        file.getOriginalFilename(), changeSummary, auth)));
    }

    @Operation(summary = "Add a recipient to a document")
    @PostMapping("/{documentId}/recipients")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> addRecipient(
            @PathVariable UUID documentId,
            @Valid @RequestBody AddRecipientRequest request,
            Authentication auth) {
        documentService.addRecipient(documentId, request, auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(null, "Recipient added"));
    }
}
