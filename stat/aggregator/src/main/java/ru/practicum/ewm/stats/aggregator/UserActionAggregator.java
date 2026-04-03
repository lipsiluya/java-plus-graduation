package ru.practicum.ewm.stats.aggregator;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.AvroUtils;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserActionAggregator {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    @Value("${app.kafka.topics.events-similarity}")
    private String similarityTopic;

    private final Map<Long, Map<Long, Double>> userWeights = new ConcurrentHashMap<>();
    private final Map<Long, Double> eventWeightsSum = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Double>> minWeightsSums = new ConcurrentHashMap<>();

    @KafkaListener(topics = "${app.kafka.topics.user-actions}", groupId = "${spring.kafka.consumer.group-id}")
    public void handle(byte[] payload) {
        UserActionAvro action = AvroUtils.fromBytes(payload, UserActionAvro.getClassSchema());

        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = weight(action.getActionType());

        Map<Long, Double> userEvents = userWeights.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
        double oldWeight = userEvents.getOrDefault(eventId, 0.0);

        if (newWeight <= oldWeight) {
            return;
        }

        userEvents.put(eventId, newWeight);
        eventWeightsSum.merge(eventId, newWeight - oldWeight, Double::sum);

        for (Map.Entry<Long, Double> entry : userEvents.entrySet()) {
            long otherEvent = entry.getKey();
            if (otherEvent == eventId) {
                continue;
            }

            double otherWeight = entry.getValue();
            double oldMin = Math.min(oldWeight, otherWeight);
            double newMin = Math.min(newWeight, otherWeight);

            if (newMin == oldMin) {
                continue;
            }

            double updatedMinSum = updateMinSum(eventId, otherEvent, newMin - oldMin);
            double sumA = eventWeightsSum.getOrDefault(eventId, 0.0);
            double sumB = eventWeightsSum.getOrDefault(otherEvent, 0.0);

            if (sumA <= 0.0 || sumB <= 0.0) {
                continue;
            }

            double score = updatedMinSum / (sumA * sumB);
            long first = Math.min(eventId, otherEvent);
            long second = Math.max(eventId, otherEvent);

            EventSimilarityAvro similarity = EventSimilarityAvro.newBuilder()
                    .setEventA(first)
                    .setEventB(second)
                    .setScore(score)
                    .setTimestamp(action.getTimestamp())
                    .build();

            kafkaTemplate.send(similarityTopic, first + ":" + second, AvroUtils.toBytes(similarity));
        }
    }

    private double updateMinSum(long eventA, long eventB, double delta) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);

        Map<Long, Double> inner = minWeightsSums.computeIfAbsent(first, id -> new ConcurrentHashMap<>());
        double updated = inner.getOrDefault(second, 0.0) + delta;
        inner.put(second, updated);
        return updated;
    }

    private double weight(ActionTypeAvro type) {
        if (type == null) {
            return 0.4;
        }
        return switch (type) {
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
            case VIEW -> 0.4;
        };
    }
}
