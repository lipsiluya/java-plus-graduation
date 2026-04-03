package ru.practicum.ewm.stats.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.analyzer.model.EventPairId;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.avro.AvroUtils;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SimilarityListener {

    private final EventSimilarityRepository similarityRepository;

    @KafkaListener(topics = "${app.kafka.topics.events-similarity}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handle(byte[] payload) {
        EventSimilarityAvro avro = AvroUtils.fromBytes(payload, EventSimilarityAvro.getClassSchema());
        Instant timestamp = Instant.ofEpochMilli(avro.getTimestamp());

        EventPairId id = new EventPairId(avro.getEventA(), avro.getEventB());
        EventSimilarity similarity = similarityRepository.findById(id)
                .orElseGet(() -> new EventSimilarity(id, avro.getScore(), timestamp));

        similarity.setScore(avro.getScore());
        similarity.setUpdatedAt(timestamp);
        similarityRepository.save(similarity);
    }
}
