package za.co.titandynamix.sasbo_online_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DeletionCodeCreateDto(
        @NotBlank(message = "Deletion code name must not be blank.")
        @Size(max = 100, message = "Deletion code name must not exceed 100 characters.")
        String name,

        @Positive(message = "Parent ID must be a positive number if provided.")
        Long parentId
) {}

