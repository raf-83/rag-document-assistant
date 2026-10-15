package com.aidocumentvault.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChatRequest {

    @NotBlank(message = "Question must not be blank")
    @Size(min = 3, max = 1000, message = "Question must be between 3 and 1000 characters")
    private String question;
}
