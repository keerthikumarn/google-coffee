package com.googlecoffee.web;

import com.googlecoffee.ai.PulseService;
import com.googlecoffee.live.LiveOrderHub;
import com.googlecoffee.live.StaffBoard;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.security.StaffAuth;
import com.googlecoffee.service.OrderService;
import com.googlecoffee.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    public record LoginRequest(@NotBlank String pin) {}

    public record StatusRequest(@NotNull OrderStatus status) {}

    public record SettingsRequest(@Min(1) @Max(8) int activeBaristas) {}

    private final StaffAuth auth;
    private final LiveOrderHub hub;
    private final OrderService orders;
    private final SettingsService settings;
    private final PulseService pulse;

    public StaffController(StaffAuth auth, LiveOrderHub hub, OrderService orders,
                           SettingsService settings, PulseService pulse) {
        this.auth = auth;
        this.hub = hub;
        this.orders = orders;
        this.settings = settings;
        this.pulse = pulse;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String client = clientId(http);
        long now = System.currentTimeMillis();
        if (auth.isLockedOut(client, now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again in 5 minutes.");
        }
        String token = auth.login(client, req.pin(), now);
        if (token == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "That PIN didn't match");
        return Map.of("token", token);
    }

    /** EventSource can't send headers, so the stream takes the token as a query parameter. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String token) {
        requireStaff(token);
        return hub.subscribeStaff();
    }

    /** Polling fallback for networks that buffer SSE (e.g. Cloudflare quick tunnels). */
    @GetMapping("/board")
    public StaffBoard board(@RequestHeader(value = "X-Staff-Token", required = false) String token) {
        requireStaff(token);
        return hub.board();
    }

    @PatchMapping("/orders/{id}")
    public Order updateStatus(@RequestHeader(value = "X-Staff-Token", required = false) String token,
                              @PathVariable String id, @Valid @RequestBody StatusRequest req) {
        requireStaff(token);
        return orders.updateStatus(id, req.status());
    }

    @PutMapping("/settings")
    public Map<String, Integer> updateSettings(@RequestHeader(value = "X-Staff-Token", required = false) String token,
                                               @Valid @RequestBody SettingsRequest req) {
        requireStaff(token);
        return Map.of("activeBaristas", settings.setActiveBaristas(req.activeBaristas()));
    }

    @GetMapping("/pulse")
    public PulseService.Pulse pulse(@RequestHeader(value = "X-Staff-Token", required = false) String token,
                                    @RequestParam(defaultValue = "false") boolean refresh) {
        requireStaff(token);
        return pulse.current(refresh);
    }

    private void requireStaff(String token) {
        if (!auth.verify(token, System.currentTimeMillis())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Staff sign-in required");
        }
    }

    private static String clientId(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}