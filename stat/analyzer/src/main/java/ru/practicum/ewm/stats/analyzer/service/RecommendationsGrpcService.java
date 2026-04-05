package ru.practicum.ewm.stats.analyzer.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.UserEventInteraction;
import ru.practicum.ewm.stats.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.analyzer.repository.UserEventInteractionRepository;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendationsControllerGrpc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class RecommendationsGrpcService extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final UserEventInteractionRepository interactionRepository;
    private final EventSimilarityRepository similarityRepository;

    @Value("${recommendations.neighbors:10}")
    private int neighborsCount;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        long userId = request.getUserId();
        int maxResults = Math.max(request.getMaxResults(), 0);

        if (maxResults <= 0) {
            responseObserver.onCompleted();
            return;
        }

        List<UserEventInteraction> recent = interactionRepository
                .findByIdUserIdOrderByLastActionTimeDesc(userId, PageRequest.of(0, maxResults));
        if (recent.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        List<UserEventInteraction> allInteractions = interactionRepository.findByIdUserId(userId);
        Map<Long, Double> userWeights = allInteractions.stream()
                .collect(Collectors.toMap(i -> i.getId().getEventId(), UserEventInteraction::getWeight, (a, b) -> a));
        Set<Long> interactedEvents = new HashSet<>(userWeights.keySet());
        Set<Long> seedEvents = recent.stream()
                .map(i -> i.getId().getEventId())
                .collect(Collectors.toSet());

        Map<Long, Double> candidates = collectCandidates(seedEvents, interactedEvents);
        if (candidates.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        List<Long> candidateIds = candidates.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .toList();

        List<RecommendedEventProto> results = new ArrayList<>();
        for (Long candidateId : candidateIds) {
            double predicted = predictScore(candidateId, userWeights);
            results.add(RecommendedEventProto.newBuilder()
                    .setEventId(candidateId)
                    .setScore(predicted)
                    .build());
        }

        results.sort(Comparator.comparingDouble(RecommendedEventProto::getScore).reversed());
        results.forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        int maxResults = Math.max(request.getMaxResults(), 0);

        Set<Long> interacted = interactionRepository.findByIdUserId(userId).stream()
                .map(i -> i.getId().getEventId())
                .collect(Collectors.toSet());

        List<RecommendedEventProto> results = similarityRepository.findAllByEvent(eventId).stream()
                .map(sim -> {
                    long other = sim.getId().getEventA().equals(eventId)
                            ? sim.getId().getEventB()
                            : sim.getId().getEventA();
                    return Map.entry(other, sim.getScore());
                })
                .filter(entry -> entry.getKey() != eventId)
                .filter(entry -> !interacted.contains(entry.getKey()))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> RecommendedEventProto.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build())
                .toList();

        results.forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        List<Long> eventIds = request.getEventIdList();
        if (eventIds == null || eventIds.isEmpty()) {
            responseObserver.onCompleted();
            return;
        }

        Map<Long, Double> sums = interactionRepository.sumWeightsByEventIds(eventIds).stream()
                .collect(Collectors.toMap(UserEventInteractionRepository.EventWeightSumProjection::getEventId,
                        UserEventInteractionRepository.EventWeightSumProjection::getWeightSum));

        for (Long eventId : eventIds) {
            double score = sums.getOrDefault(eventId, 0.0);
            responseObserver.onNext(RecommendedEventProto.newBuilder()
                    .setEventId(eventId)
                    .setScore(score)
                    .build());
        }

        responseObserver.onCompleted();
    }

    private Map<Long, Double> collectCandidates(Set<Long> seedEvents, Set<Long> interactedEvents) {
        List<EventSimilarity> similarities = similarityRepository.findAllByEvents(new ArrayList<>(seedEvents));
        Map<Long, Double> candidates = new HashMap<>();

        for (EventSimilarity similarity : similarities) {
            long eventA = similarity.getId().getEventA();
            long eventB = similarity.getId().getEventB();

            if (seedEvents.contains(eventA) && !interactedEvents.contains(eventB)) {
                candidates.merge(eventB, similarity.getScore(), Math::max);
            }
            if (seedEvents.contains(eventB) && !interactedEvents.contains(eventA)) {
                candidates.merge(eventA, similarity.getScore(), Math::max);
            }
        }
        return candidates;
    }

    private double predictScore(Long candidateId, Map<Long, Double> userWeights) {
        if (userWeights.isEmpty()) {
            return 0.0;
        }

        List<Long> interactedIds = new ArrayList<>(userWeights.keySet());
        List<EventSimilarity> sims = similarityRepository.findAllForEventAndOthers(candidateId, interactedIds);

        List<Map.Entry<Long, Double>> neighbors = sims.stream()
                .map(sim -> {
                    long other = sim.getId().getEventA().equals(candidateId)
                            ? sim.getId().getEventB()
                            : sim.getId().getEventA();
                    return Map.entry(other, sim.getScore());
                })
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(neighborsCount)
                .toList();

        double sumWeighted = 0.0;
        double sumScores = 0.0;

        for (Map.Entry<Long, Double> neighbor : neighbors) {
            Double weight = userWeights.get(neighbor.getKey());
            if (weight == null) {
                continue;
            }
            sumWeighted += neighbor.getValue() * weight;
            sumScores += neighbor.getValue();
        }

        return sumScores == 0.0 ? 0.0 : sumWeighted / sumScores;
    }
}
