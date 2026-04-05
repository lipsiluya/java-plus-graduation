package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.EventPairId;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;

import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, EventPairId> {

    @Query("""
            select e from EventSimilarity e
            where e.id.eventA = :eventId or e.id.eventB = :eventId
            """)
    List<EventSimilarity> findAllByEvent(@Param("eventId") Long eventId);

    @Query("""
            select e from EventSimilarity e
            where e.id.eventA in :eventIds or e.id.eventB in :eventIds
            """)
    List<EventSimilarity> findAllByEvents(@Param("eventIds") List<Long> eventIds);

    @Query("""
            select e from EventSimilarity e
            where (e.id.eventA = :eventId and e.id.eventB in :otherIds)
               or (e.id.eventB = :eventId and e.id.eventA in :otherIds)
            """)
    List<EventSimilarity> findAllForEventAndOthers(@Param("eventId") Long eventId,
                                                   @Param("otherIds") List<Long> otherIds);
}
