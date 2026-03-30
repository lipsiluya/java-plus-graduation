package ru.practicum.requestservice.controller;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.requestservice.client.EventRequestClient;

import java.util.function.Supplier;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class RequestProxyController {
    private final EventRequestClient client;

    @GetMapping("/{userId}/requests")
    public ResponseEntity<String> getUserRequests(@PathVariable Long userId) {
        return forward(() -> client.getUserRequests(userId));
    }

    @PostMapping("/{userId}/requests")
    public ResponseEntity<String> addParticipationRequest(@PathVariable Long userId,
                                                          @RequestParam Long eventId) {
        return forward(() -> client.addParticipationRequest(userId, eventId));
    }

    @PatchMapping("/{userId}/requests/{requestId}/cancel")
    public ResponseEntity<String> cancelRequest(@PathVariable Long userId,
                                                @PathVariable Long requestId) {
        return forward(() -> client.cancelRequest(userId, requestId));
    }

    private ResponseEntity<String> forward(Supplier<ResponseEntity<String>> call) {
        try {
            return call.get();
        } catch (FeignException e) {
            int status = e.status() >= 0 ? e.status() : HttpStatus.SERVICE_UNAVAILABLE.value();
            return ResponseEntity.status(status).body(e.contentUTF8());
        }
    }
}
