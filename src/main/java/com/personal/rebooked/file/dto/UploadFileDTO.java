package com.personal.rebooked.file.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

public record UploadFileDTO(
        @NotBlank MultipartFile file
) {
}
