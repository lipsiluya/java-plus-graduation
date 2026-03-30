package ru.practicum.extraservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "event-service")
public interface EventExtraClient {

    @GetMapping("/categories")
    ResponseEntity<String> getCategories(@RequestParam(required = false) Integer from,
                                         @RequestParam(required = false) Integer size);

    @GetMapping("/categories/{catId}")
    ResponseEntity<String> getCategoryById(@PathVariable Long catId);

    @PostMapping(value = "/admin/categories", consumes = "application/json")
    ResponseEntity<String> createCategory(@RequestBody String body);

    @PatchMapping(value = "/admin/categories/{catId}", consumes = "application/json")
    ResponseEntity<String> updateCategory(@PathVariable Long catId,
                                          @RequestBody String body);

    @DeleteMapping("/admin/categories/{catId}")
    ResponseEntity<String> deleteCategory(@PathVariable Long catId);

    @GetMapping("/compilations")
    ResponseEntity<String> getCompilations(@RequestParam(required = false) Boolean pinned,
                                           @RequestParam(required = false) Integer from,
                                           @RequestParam(required = false) Integer size);

    @GetMapping("/compilations/{compId}")
    ResponseEntity<String> getCompilationById(@PathVariable Long compId);

    @PostMapping(value = "/admin/compilations", consumes = "application/json")
    ResponseEntity<String> createCompilation(@RequestBody String body);

    @PatchMapping(value = "/admin/compilations/{compId}", consumes = "application/json")
    ResponseEntity<String> updateCompilation(@PathVariable Long compId,
                                             @RequestBody String body);

    @DeleteMapping("/admin/compilations/{compId}")
    ResponseEntity<String> deleteCompilation(@PathVariable Long compId);
}
