package ru.practicum.requestservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "event-service")
public interface EventRequestClient {

    @GetMapping("/users/{userId}/requests")
    ResponseEntity<String> getUserRequests(@PathVariable Long userId);

    @PostMapping("/users/{userId}/requests")
    ResponseEntity<String> addParticipationRequest(@PathVariable Long userId,
                                                   @RequestParam Long eventId);

    @PatchMapping("/users/{userId}/requests/{requestId}/cancel")
    ResponseEntity<String> cancelRequest(@PathVariable Long userId,
                                         @PathVariable Long requestId);
}
