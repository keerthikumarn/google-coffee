package com.googlecoffee.web;

import com.googlecoffee.ai.BaristaService;
import com.googlecoffee.live.LiveOrderHub;
import com.googlecoffee.live.OrderView;
import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.service.FeedbackService;
import com.googlecoffee.service.MenuService;
import com.googlecoffee.service.OrderService;
import com.googlecoffee.service.SessionService;
import com.googlecoffee.service.SettingsService;
import com.googlecoffee.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level behaviour of the guest API: routing, parameters and error mapping. */
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean MenuService menu;
    @MockitoBean SessionService sessions;
    @MockitoBean OrderService orders;
    @MockitoBean FeedbackService feedback;
    @MockitoBean BaristaService barista;
    @MockitoBean LiveOrderHub hub;
    @MockitoBean SettingsService settings;

    @Test
    void cancelReturnsTheCancelledOrder() throws Exception {
        Order cancelled = Fixtures.order("o1", "s1", OrderStatus.CANCELLED, 3, 1);
        when(orders.cancelForSession("o1", "s1")).thenReturn(cancelled);
        when(hub.viewOf(cancelled)).thenReturn(new OrderView(cancelled, 0, 0));

        mvc.perform(post("/api/orders/o1/cancel").param("sessionId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value("o1"))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
    }

    @Test
    void cancelWithoutASessionIsABadRequest() throws Exception {
        mvc.perform(post("/api/orders/o1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void cancelOfSomeoneElsesOrderIsNotFound() throws Exception {
        when(orders.cancelForSession(any(), any())).thenThrow(ApiException.notFound("Order"));

        mvc.perform(post("/api/orders/o1/cancel").param("sessionId", "intruder"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void cancelOfAnOrderThatAlreadyStartedIsAConflictWithAnExplanation() throws Exception {
        when(orders.cancelForSession(any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "This order is already preparing"));

        mvc.perform(post("/api/orders/o1/cancel").param("sessionId", "s1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This order is already preparing"));
    }
}
