package ru.practicum.requestservice.controller;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.requestservice.client.EventRequestClient;
import ru.practicum.statistic.CollectorClient;

import java.util.function.Supplier;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class RequestProxyController {
    private final EventRequestClient client;
    private final CollectorClient collectorClient;

    @GetMapping("/{userId}/requests")
    public ResponseEntity<String> getUserRequests(@PathVariable Long userId) {
        return forward(() -> client.getUserRequests(userId));
    }

    @PostMapping("/{userId}/requests")
    public ResponseEntity<String> addParticipationRequest(@PathVariable Long userId,
                                                          @RequestParam Long eventId) {
        return forward(() -> client.addParticipationRequest(userId, eventId),
                () -> collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_REGISTER));
    }

    @PatchMapping("/{userId}/requests/{requestId}/cancel")
    public ResponseEntity<String> cancelRequest(@PathVariable Long userId,
                                                @PathVariable Long requestId) {
        return forward(() -> client.cancelRequest(userId, requestId));
    }

    private ResponseEntity<String> forward(Supplier<ResponseEntity<String>> call) {
        return forward(call, () -> {
        });
    }

    private ResponseEntity<String> forward(Supplier<ResponseEntity<String>> call, Runnable onSuccess) {
        try {
            ResponseEntity<String> response = call.get();
            if (response.getStatusCode().is2xxSuccessful()) {
                onSuccess.run();
            }
            return response;
        } catch (FeignException e) {
            int status = e.status() >= 0 ? e.status() : HttpStatus.SERVICE_UNAVAILABLE.value();
            return ResponseEntity.status(status).body(e.contentUTF8());
        }
    }
}
