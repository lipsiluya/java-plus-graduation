package ru.practicum.statistic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Deprecated
@Component
@RequiredArgsConstructor
public class StatClient {

    private final CollectorClient collectorClient;
    private final AnalyzerClient analyzerClient;

    public void sendUserAction(long userId, long eventId, ActionTypeProto actionType, Instant timestamp) {
        collectorClient.sendUserAction(userId, eventId, actionType, timestamp);
    }

    public void sendUserAction(long userId, long eventId, ActionTypeProto actionType) {
        collectorClient.sendUserAction(userId, eventId, actionType);
    }

    public Stream<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        return analyzerClient.getRecommendationsForUser(userId, maxResults);
    }

    public Stream<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        return analyzerClient.getSimilarEvents(eventId, userId, maxResults);
    }

    public Stream<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        return analyzerClient.getInteractionsCount(eventIds);
    }
}
