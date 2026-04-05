package ru.practicum.compilations.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NewCompilationDto {
    private Set<Long> events = new HashSet<>();

    @NotNull
    private Boolean pinned = false;

    @NotBlank
    @Size(max = 50)
    private String title;
}
