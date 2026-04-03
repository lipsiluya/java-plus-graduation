package ru.practicum.ewm.stats.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.analyzer.model.UserEventId;
import ru.practicum.ewm.stats.analyzer.model.UserEventInteraction;
import ru.practicum.ewm.stats.analyzer.repository.UserEventInteractionRepository;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.AvroUtils;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserActionListener {

    private final UserEventInteractionRepository interactionRepository;

    @KafkaListener(topics = "${app.kafka.topics.user-actions}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handle(byte[] payload) {
        UserActionAvro action = AvroUtils.fromBytes(payload, UserActionAvro.getClassSchema());

        double weight = weight(action.getActionType());
        Instant timestamp = action.getTimestamp() != null ? action.getTimestamp() : Instant.now();

        UserEventId id = new UserEventId(action.getUserId(), action.getEventId());
        UserEventInteraction interaction = interactionRepository.findById(id)
                .orElseGet(() -> new UserEventInteraction(id, weight, timestamp));

        if (interaction.getWeight() == null || weight > interaction.getWeight()) {
            interaction.setWeight(weight);
        }
        interaction.setLastActionTime(timestamp);
        interactionRepository.save(interaction);
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
