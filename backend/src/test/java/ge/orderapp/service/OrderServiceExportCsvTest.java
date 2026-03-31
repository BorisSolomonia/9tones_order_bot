package ge.orderapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.response.OrderDto;
import ge.orderapp.dto.response.OrderItemDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderServiceExportCsvTest {

    @Test
    void exportIncludesLocalizedOrderDateAndTimeColumns() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        store.putOrder(new OrderDto(
                "o1", "m1", "Manager",
                "2026-03-31", "SENT", true, "2026-03-31T06:15:10Z",
                1, "2026-03-31T06:15:10Z", null));
        store.putOrderItem(new OrderItemDto("i1", "o1", "Customer", "c1", "Comment", "2026-03-31T06:15:10Z", null));

        OrderService service = new OrderService(store, null);
        ReflectionTestUtils.setField(service, "appTimeZone", "Asia/Tbilisi");

        String csv = service.exportCsv("2026-03-31", "2026-03-31", "m1");

        assertTrue(csv.startsWith("Order ID,Manager,Order Date,Order Time,Status,Customer,Comment,Board\n"));
        assertTrue(csv.contains("o1,Manager,31.03.2026,10:15:10,SENT,Customer,Comment,"));
    }
}
