package com.livemart.product.controller;

import com.livemart.product.service.S3ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Tag(name = "Image API", description = "상품 이미지 업로드 API (AWS S3)")
@RestController
@RequestMapping("/api/products/{productId}/images")
@RequiredArgsConstructor
public class ImageUploadController {

    private final S3ImageService s3ImageService;

    // 허용된 이미지 포맷의 매직바이트 시그니처
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
        "image/jpeg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF},
        "image/png",  new byte[]{(byte)0x89, 0x50, 0x4E, 0x47},
        "image/gif",  new byte[]{0x47, 0x49, 0x46},
        "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}
    );

    @Operation(summary = "상품 이미지 업로드", description = "AWS S3에 상품 이미지를 업로드합니다")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<Map<String, String>> uploadImage(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미지 파일만 업로드 가능합니다"));
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일 크기는 10MB 이하여야 합니다"));
        }

        // 매직바이트 검증 — Content-Type 스푸핑 방어
        if (!validateMagicBytes(file)) {
            return ResponseEntity.badRequest().body(Map.of("error", "유효하지 않은 이미지 파일입니다 (매직바이트 불일치)"));
        }

        String imageUrl = s3ImageService.uploadImage(file, productId);
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @Operation(summary = "상품 이미지 삭제")
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long productId,
            @RequestParam String imageUrl) {
        s3ImageService.deleteImage(imageUrl);
        return ResponseEntity.ok().build();
    }

    /**
     * 매직바이트 검증 — 파일 헤더의 실제 시그니처를 확인하여 Content-Type 위조 방어
     */
    private boolean validateMagicBytes(MultipartFile file) throws IOException {
        byte[] header = new byte[4];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read < 3) return false;
        }
        return MAGIC_BYTES.values().stream().anyMatch(magic -> {
            for (int i = 0; i < magic.length; i++) {
                if (header[i] != magic[i]) return false;
            }
            return true;
        });
    }
}
