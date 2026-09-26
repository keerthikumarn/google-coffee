package com.googlecoffee.web;

import com.googlecoffee.ai.BaristaService;
import com.googlecoffee.live.LiveOrderHub;
import com.googlecoffee.live.OrderView;
import com.googlecoffee.model.Feedback;
import com.googlecoffee.model.MenuItem;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.model.Session;
import com.googlecoffee.service.EtaCalculator;
import com.googlecoffee.service.FeedbackService;
import com.googlecoffee.service.MenuService;
import com.googlecoffee.service.OrderService;
import com.googlecoffee.service.SessionService;
import com.googlecoffee.service.SettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CustomerController {

    public record CreateSessionRequest(
            @NotBlank @Size(max = 40) String name,
            @NotBlank @Size(max = 10) String table,
            @Size(max = 6) List<String> preferences) {}

    public record PreferencesRequest(@Size(max = 6) List<String> preferences) {}

    public record ChatRequest(
            @NotBlank String sessionId,
            @NotBlank @Size(max = 500) String message,
            @Size(max = 20) List<BaristaService.ChatTurn> history) {}

    public record LineDto(@NotBlank String itemId, @Min(1) @Max(10) int qty, @Size(max = 80) String note) {}

    public record EstimateRequest(@NotEmpty @Size(max = 15) List<@Valid LineDto> items) {}

    public record PlaceOrderRequest(@NotBlank String sessionId, @NotEmpty @Size(max = 15) List<@Valid LineDto> items) {}

    public record FeedbackRequest(
            @NotBlank String sessionId,
            String orderId,
            @Min(1) @Max(5) int rating,
            @Size(max = 300) String comment) {}

    public record CafeStatus(int activeBaristas, int ordersInQueue, int waitMinutesForCoffee, String busyLevel) {}

    private final MenuService menu;
    private final SessionService sessions;
    private final OrderService orders;
    private final FeedbackService feedback;
    private final BaristaService barista;
    private final LiveOrderHub hub;
    private final SettingsService settings;

    public CustomerController(MenuService menu, SessionService sessions, OrderService orders,
                              FeedbackService feedback, BaristaService barista,
                              LiveOrderHub hub, SettingsService settings) {
        this.menu = menu;
        this.sessions = sessions;
        this.orders = orders;
        this.feedback = feedback;
        this.barista = barista;
        this.hub = hub;
        this.settings = settings;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/menu")
    public List<MenuItem> menu() {
        return menu.all();
    }

    @GetMapping("/cafe/status")
    public CafeStatus status() {
        List<Order> active = hub.activeOrders();
        int inQueue = (int) active.stream()
                .filter(o -> o.status() == OrderStatus.PLACED || o.status() == OrderStatus.PREPARING).count();
        int wait = EtaCalculator.estimateNew(active, settings.activeBaristas(), 4);
        String level = wait <= 5 ? "Quiet" : wait <= 12 ? "Steady" : "Busy";
        return new CafeStatus(settings.activeBaristas(), inQueue, wait, level);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public Session createSession(@Valid @RequestBody CreateSessionRequest req) {
        return sessions.create(req.name(), req.table(), req.preferences());
    }

    @GetMapping("/sessions/{id}")
    public Session session(@PathVariable String id) {
        return sessions.get(id);
    }

    @PutMapping("/sessions/{id}/preferences")
    public Session updatePreferences(@PathVariable String id, @Valid @RequestBody PreferencesRequest req) {
        return sessions.updatePreferences(id, req.preferences());
    }

    @PostMapping("/chat")
    public BaristaService.ChatReply chat(@Valid @RequestBody ChatRequest req) {
        Session s = sessions.get(req.sessionId());
        return barista.chat(s, req.history(), req.message());
    }

    @GetMapping("/recommendations")
    public BaristaService.Picks recommendations(@RequestParam String sessionId) {
        Session s = sessions.get(sessionId);
        return barista.recommendations(s, orders.historyFor(s.id()));
    }

    @PostMapping("/orders/estimate")
    public Map<String, Integer> estimate(@Valid @RequestBody EstimateRequest req) {
        return Map.of("minutes", orders.estimateMinutes(toLines(req.items())));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView place(@Valid @RequestBody PlaceOrderRequest req) {
        return orders.place(req.sessionId(), toLines(req.items()));
    }

    @GetMapping("/orders/{id}")
    public OrderView order(@PathVariable String id, @RequestParam String sessionId) {
        return hub.viewOf(orders.getForSession(id, sessionId));
    }

    @GetMapping(value = "/orders/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id, @RequestParam String sessionId) {
        return hub.subscribeCustomer(orders.getForSession(id, sessionId));
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Feedback feedback(@Valid @RequestBody FeedbackRequest req) {
        return feedback.submit(req.sessionId(), req.orderId(), req.rating(), req.comment());
    }

    private static List<OrderService.LineRequest> toLines(List<LineDto> items) {
        return items.stream().map(i -> new OrderService.LineRequest(i.itemId(), i.qty(), i.note())).toList();
    }
}
