package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.UserEventId;
import ru.practicum.ewm.stats.analyzer.model.UserEventInteraction;

import java.util.List;

public interface UserEventInteractionRepository extends JpaRepository<UserEventInteraction, UserEventId> {

    List<UserEventInteraction> findByIdUserIdOrderByLastActionTimeDesc(Long userId, Pageable pageable);

    List<UserEventInteraction> findByIdUserId(Long userId);

    boolean existsByIdUserIdAndIdEventId(Long userId, Long eventId);

    @Query("""
            select u.id.eventId as eventId, sum(u.weight) as weightSum
            from UserEventInteraction u
            where u.id.eventId in :eventIds
            group by u.id.eventId
            """)
    List<EventWeightSumProjection> sumWeightsByEventIds(@Param("eventIds") List<Long> eventIds);

    interface EventWeightSumProjection {
        Long getEventId();
        Double getWeightSum();
    }
}
