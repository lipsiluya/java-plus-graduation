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
public class CategoryProxyController {
    private final EventExtraClient client;

    @GetMapping("/categories")
    public ResponseEntity<String> getCategories(@RequestParam(required = false) Integer from,
                                                @RequestParam(required = false) Integer size) {
        return forward(() -> client.getCategories(from, size));
    }

    @GetMapping("/categories/{catId}")
    public ResponseEntity<String> getCategoryById(@PathVariable Long catId) {
        return forward(() -> client.getCategoryById(catId));
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<String> createCategory(@RequestBody(required = false) String body) {
        return forward(() -> client.createCategory(body));
    }

    @PatchMapping("/admin/categories/{catId}")
    public ResponseEntity<String> updateCategory(@PathVariable Long catId,
                                                 @RequestBody(required = false) String body) {
        return forward(() -> client.updateCategory(catId, body));
    }

    @DeleteMapping("/admin/categories/{catId}")
    public ResponseEntity<String> deleteCategory(@PathVariable Long catId) {
        return forward(() -> client.deleteCategory(catId));
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
