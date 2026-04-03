package ru.practicum.ewm.stats.analyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_event_interactions")
public class UserEventInteraction {

    @EmbeddedId
    private UserEventId id;

    @Column(nullable = false)
    private Double weight;

    @Column(name = "last_action_time", nullable = false)
    private Instant lastActionTime;
}
