package ru.practicum.extraservice.controller;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.practicum.extraservice.client.EventExtraClient;

import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
public class CompilationProxyController {
    private final EventExtraClient client;

    @GetMapping("/compilations")
    public ResponseEntity<String> getCompilations(@RequestParam(required = false) Boolean pinned,
                                                  @RequestParam(required = false) Integer from,
                                                  @RequestParam(required = false) Integer size) {
        return forward(() -> client.getCompilations(pinned, from, size));
    }

    @GetMapping("/compilations/{compId}")
    public ResponseEntity<String> getCompilationById(@PathVariable Long compId) {
        return forward(() -> client.getCompilationById(compId));
    }

    @PostMapping("/admin/compilations")
    public ResponseEntity<String> createCompilation(@RequestBody(required = false) String body) {
        return forward(() -> client.createCompilation(body));
    }

    @PatchMapping("/admin/compilations/{compId}")
    public ResponseEntity<String> updateCompilation(@PathVariable Long compId,
                                                    @RequestBody(required = false) String body) {
        return forward(() -> client.updateCompilation(compId, body));
    }

    @DeleteMapping("/admin/compilations/{compId}")
    public ResponseEntity<String> deleteCompilation(@PathVariable Long compId) {
        return forward(() -> client.deleteCompilation(compId));
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
