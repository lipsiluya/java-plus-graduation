package ru.practicum.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dao.EventRepository;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.exception.*;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.Request;
import ru.practicum.request.model.RequestStatus;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.statistic.AnalyzerClient;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final AnalyzerClient analyzerClient;

    @Override
    @Transactional
    public EventFullDto create(Long userId, NewEventDto newEventDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id = " + userId + " не найден"));

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Категория с id = " + newEventDto.getCategory() + " не найдена"));

        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата события должна быть не раньше чем через 2 часа от текущего момента");
        }

        Event event = EventMapper.mapToEvent(newEventDto, user, category);

        Event savedEvent = eventRepository.save(event);
        return EventMapper.mapToFullDto(savedEvent);
    }

    @Override
    @Transactional
    public EventFullDto updateByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя редактировать опубликованное событие");
        }

        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Дата события должна быть не раньше чем через 2 часа от текущего момента");
        }

        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new BadRequestException("Лимит участников не может быть отрицательным");
        }

        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id = " + updateRequest.getCategory() + " не найдена"));
            event.setCategory(category);
        }

        EventMapper.updateEventFromUserRequest(updateRequest, event);

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case SEND_TO_REVIEW:
                    event.setState(EventState.PENDING);
                    break;
                case CANCEL_REVIEW:
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        Event updatedEvent = eventRepository.save(event);

        return EventMapper.mapToFullDto(updatedEvent);
    }

    @Override
    @Transactional
    public EventFullDto updateByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        if (updateRequest.getEventDate() != null &&
                updateRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new BadRequestException("Дата начала события должна быть не ранее чем за час от даты публикации");
        }

        if (updateRequest.getParticipantLimit() != null && updateRequest.getParticipantLimit() < 0) {
            throw new ValidationException("Лимит участников не может быть отрицательным");
        }

        if (updateRequest.getCategory() != null) {
            Category category = categoryRepository.findById(updateRequest.getCategory())
                    .orElseThrow(() -> new NotFoundException("Категория с id = " + updateRequest.getCategory() + " не найдена"));
            event.setCategory(category);
        }

        EventMapper.updateEventFromAdminRequest(updateRequest, event);

        if (updateRequest.getStateAction() != null) {
            switch (updateRequest.getStateAction()) {
                case PUBLISH_EVENT:
                    if (event.getState() != EventState.PENDING) {
                        throw new ConflictException("Событие можно публиковать только если оно в состоянии ожидания публикации");
                    }
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case REJECT_EVENT:
                    if (event.getState() == EventState.PUBLISHED) {
                        throw new ConflictException("Событие можно отклонить только если оно еще не опубликовано");
                    }
                    event.setState(EventState.CANCELED);
                    break;
            }
        }

        Event updatedEvent = eventRepository.save(event);

        return EventMapper.mapToFullDto(updatedEvent);
    }

    @Override
    public EventFullDto getByUser(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        List<Event> events = Collections.singletonList(event);
        Double eventRating = getEventsRatings(events).getOrDefault(eventId, 0.0);
        EventFullDto eventFullDto = EventMapper.mapToFullDto(event);
        eventFullDto.setRating(eventRating);

        return eventFullDto;
    }

    @Override
    public EventFullDto getPublicEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Событие не опубликовано");
        }

        List<Event> events = Collections.singletonList(event);
        Double eventRating = getEventsRatings(events).getOrDefault(eventId, 0.0);
        EventFullDto eventFullDto = EventMapper.mapToFullDto(event);
        eventFullDto.setRating(eventRating);

        return eventFullDto;
    }

    @Override
    public List<EventShortDto> getAllByUser(Long userId, Integer from, Integer size) {
        Pageable pageable = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findByInitiatorId(userId, pageable).getContent();
        Map<Long, Double> ratingsMap = getEventsRatings(events);

        List<EventShortDto> eventShortDtos = events.stream()
                .map(EventMapper::mapToShortDto)
                .toList();

        eventShortDtos.forEach(
                eventShortDto -> eventShortDto.setRating(ratingsMap.getOrDefault(eventShortDto.getId(), 0.0))
        );

        return eventShortDtos;
    }

    
    @Override
    public List<EventFullDto> searchForAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd, Integer from, Integer size) {
        log.info("Поиск событий администратором: users={}, states={}, categories={}, rangeStart={}, rangeEnd={}, from={}, size={}",
                users, states, categories, rangeStart, rangeEnd, from, size);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<EventState> eventStates = null;

        if (states != null && !states.isEmpty()) {
            eventStates = new ArrayList<>(states);
        }

        List<Event> events = eventRepository.findEventsWithFilters(users, eventStates, categories, rangeStart, rangeEnd, pageable);
        List<EventFullDto> dtos = events.stream()
                .map(EventMapper::mapToFullDto)
                .collect(Collectors.toList());

        Map<Long, Double> ratingsMap = getEventsRatings(events);
        dtos.forEach(dto -> dto.setRating(ratingsMap.getOrDefault(dto.getId(), 0.0)));

        return dtos;
    }

    
    @Override
    public List<EventShortDto> searchForUser(String text, List<Long> categories, Boolean paid,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable,
                                             String sort, Integer from, Integer size) {
        log.info("Публичный поиск событий: text={}, categories={}, paid={}, rangeStart={}, rangeEnd={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);

        // Валидация дат
        if (rangeStart != null && rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new BadRequestException("End date must be after start date");
        }

        Pageable pageable;
        if ("EVENT_DATE".equalsIgnoreCase(sort)) {
            pageable = PageRequest.of(from / size, size, Sort.by("eventDate").descending());
        } else {
            pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());
        }

        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }

        String textForSearch = (text != null) ? "%" + text.toLowerCase() + "%" : null;

        List<Event> events = eventRepository.findPublishedEventsWithFilters(
                textForSearch, categories, paid, rangeStart, rangeEnd, onlyAvailable, pageable);

        Map<Long, Double> ratingsMap = getEventsRatings(events);
        List<EventShortDto> dtos = events.stream()
                .map(EventMapper::mapToShortDto)
                .toList();

        dtos.forEach(dto -> dto.setRating(ratingsMap.getOrDefault(dto.getId(), 0.0)));

        if ("VIEWS".equalsIgnoreCase(sort) || "RATING".equalsIgnoreCase(sort)) {
            dtos = dtos.stream()
                    .sorted(Comparator.comparing(EventShortDto::getRating).reversed())
                    .toList();
        }

        return dtos;
    }


    
        @Override
    public List<EventShortDto> getRecommendations(Long userId, Integer size) {
        int limit = size != null ? size : 10;
        List<ru.practicum.ewm.stats.proto.RecommendedEventProto> recommendations;
        try {
            recommendations = analyzerClient.getRecommendationsForUser(userId, limit)
                    .toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }

        if (recommendations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> recommendedIds = recommendations.stream()
                .map(rec -> rec.getEventId())
                .toList();

        Map<Long, Double> scores = recommendations.stream()
                .collect(Collectors.toMap(
                        rec -> rec.getEventId(),
                        rec -> rec.getScore(),
                        (existing, replacement) -> existing
                ));

        List<Event> events = eventRepository.findEventsByIdIn(recommendedIds);
        List<EventShortDto> dtos = events.stream()
                .map(EventMapper::mapToShortDto)
                .toList();

        dtos.forEach(dto -> dto.setRating(scores.getOrDefault(dto.getId(), 0.0)));
        dtos = dtos.stream()
                .sorted(Comparator.comparing(EventShortDto::getRating).reversed())
                .toList();

        return dtos;
    }

    @Override
    @Transactional
    public void likeEvent(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        boolean isInitiator = event.getInitiator().getId().equals(userId);
        boolean hasRequest = !requestRepository.findByRequesterIdAndEventId(userId, eventId).isEmpty();

        if (!isInitiator && !hasRequest) {
            throw new BadRequestException("Пользователь не посещал событие");
        }
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ForbiddenException(
                    "Пользователь с id = " + userId + " не является инициатором события с id = " + eventId
            );
        }

        return requestRepository.findByEventId(eventId).stream()
                .map(RequestMapper::requestToParticipationRequestDto)
                .toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequests(Long userId, Long eventId,
                                                         EventRequestStatusUpdateRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id = " + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new ForbiddenException(
                    "Пользователь с id = " + userId + " не является инициатором события с id = " + eventId
            );
        }

        List<Request> requests = requestRepository.findByIdIn(updateRequest.getRequestIds());
        if (requests.isEmpty()) {
            return new EventRequestStatusUpdateResult(); // или ValidationException
        }

        // Проверяем, что все заявки относятся к этому событию и в PENDING
        for (Request request : requests) {
            if (!request.getEvent().getId().equals(eventId)) {
                throw new ForbiddenException(
                        "Запрос с id = " + request.getId() + " не принадлежит событию с id = " + eventId
                );
            }
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Статус можно изменить только у заявок в состоянии ожидания");
            }
        }

        long confirmedCount = requestRepository.countRequestsByEventAndStatus(event, RequestStatus.CONFIRMED);
        long participantLimit = event.getParticipantLimit() != null ? event.getParticipantLimit() : 0L;

        RequestStatus newStatus = updateRequest.getStatus();

        if (newStatus == RequestStatus.CONFIRMED
                && participantLimit > 0
                && confirmedCount + requests.size() > participantLimit) {

            // это как раз тот случай из теста "лимит уже достигнут"
            throw new ConflictException("Достигнут лимит участников для события");
        }

        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        List<ParticipationRequestDto> confirmedDtos = new ArrayList<>();
        List<ParticipationRequestDto> rejectedDtos = new ArrayList<>();

        for (Request request : requests) {
            if (newStatus == RequestStatus.CONFIRMED) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmedCount++;
                confirmedDtos.add(RequestMapper.requestToParticipationRequestDto(request));
            } else if (newStatus == RequestStatus.REJECTED) {
                request.setStatus(RequestStatus.REJECTED);
                rejectedDtos.add(RequestMapper.requestToParticipationRequestDto(request));
            } else {
                throw new ValidationException("Неизвестный статус: " + newStatus);
            }
        }

        event.setConfirmedRequests(confirmedCount);
        eventRepository.save(event);
        requestRepository.saveAll(requests);

        // если после подтверждения лимит выбит — отклоняем все оставшиеся PENDING
        if (participantLimit > 0 && confirmedCount >= participantLimit) {
            List<Request> pendingRequests =
                    requestRepository.findByEventIdAndStatus(eventId, RequestStatus.PENDING);
            for (Request pending : pendingRequests) {
                pending.setStatus(RequestStatus.REJECTED);
                rejectedDtos.add(RequestMapper.requestToParticipationRequestDto(pending));
            }
            requestRepository.saveAll(pendingRequests);
        }

        result.setConfirmedRequests(confirmedDtos);
        result.setRejectedRequests(rejectedDtos);
        return result;
    }


        private Map<Long, Double> getEventsRatings(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> ids = events.stream()
                .map(Event::getId)
                .toList();

        try {
            return analyzerClient.getInteractionsCount(ids)
                    .collect(Collectors.toMap(
                            stats -> stats.getEventId(),
                            stats -> stats.getScore(),
                            (existing, replacement) -> existing
                    ));
        } catch (Exception e) {
            return events.stream()
                    .collect(Collectors.toMap(Event::getId, event -> 0.0));
        }
    }

}