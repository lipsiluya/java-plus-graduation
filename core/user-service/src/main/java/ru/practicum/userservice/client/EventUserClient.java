package ru.practicum.userservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "event-service")
public interface EventUserClient {

    @GetMapping("/admin/users")
    ResponseEntity<String> getUsers(@RequestParam(required = false) List<Long> ids,
                                    @RequestParam(required = false) Integer from,
                                    @RequestParam(required = false) Integer size);

    @PostMapping(value = "/admin/users", consumes = "application/json")
    ResponseEntity<String> createUser(@RequestBody String body);

    @DeleteMapping("/admin/users/{userId}")
    ResponseEntity<String> deleteUser(@PathVariable Long userId);
}
