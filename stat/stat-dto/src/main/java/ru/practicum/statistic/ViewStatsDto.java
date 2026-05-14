package ru.practicum.statistic;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ViewStatsDto {
    private String app;
    private String uri;
    private Long hits;
}
