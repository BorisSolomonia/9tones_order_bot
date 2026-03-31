package ge.orderapp.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.orderapp.dto.response.OrderDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryStoreOrderFilterTest {

    @Test
    void rangeFilterIsInclusiveAndOverridesSingleDate() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        store.putOrder(order("o1", "m1", "2026-03-01", "2026-03-01T09:00:00Z"));
        store.putOrder(order("o2", "m1", "2026-03-02", "2026-03-02T09:00:00Z"));
        store.putOrder(order("o3", "m1", "2026-03-03", "2026-03-03T09:00:00Z"));
        store.putOrder(order("o4", "m2", "2026-03-02", "2026-03-02T10:00:00Z"));

        List<OrderDto> orders = store.getOrders("2026-03-01", "2026-03-02", "2026-03-03", "m1", 0, 20);

        assertEquals(List.of("o3", "o2"), orders.stream().map(OrderDto::orderId).toList());
    }

    @Test
    void exactDateStillWorksWhenRangeIsAbsent() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        store.putOrder(order("o1", "m1", "2026-03-01", "2026-03-01T09:00:00Z"));
        store.putOrder(order("o2", "m1", "2026-03-02", "2026-03-02T09:00:00Z"));

        List<OrderDto> orders = store.getOrders("2026-03-01", null, null, "m1", 0, 20);

        assertEquals(List.of("o1"), orders.stream().map(OrderDto::orderId).toList());
    }

    @Test
    void upperBoundOnlyIncludesEarlierDates() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        store.putOrder(order("o1", "m1", "2026-03-01", "2026-03-01T09:00:00Z"));
        store.putOrder(order("o2", "m1", "2026-03-02", "2026-03-02T09:00:00Z"));
        store.putOrder(order("o3", "m1", "2026-03-03", "2026-03-03T09:00:00Z"));

        List<OrderDto> orders = store.getOrders(null, null, "2026-03-02", "m1", 0, 20);

        assertEquals(List.of("o2", "o1"), orders.stream().map(OrderDto::orderId).toList());
    }

    private OrderDto order(String orderId, String managerId, String date, String createdAt) {
        return new OrderDto(orderId, managerId, "Manager", date, "SENT", true, createdAt, 1, createdAt, null);
    }
}
