package ru.practicum.compilations.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Set;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCompilationDto {
    private Set<Long> events;

    private Boolean pinned;

    @Size(max = 50)
    private String title;
}
