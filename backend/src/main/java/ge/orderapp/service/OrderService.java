package ge.orderapp.service;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.request.CreateOrderRequest;
import ge.orderapp.dto.response.OrderDto;
import ge.orderapp.dto.response.OrderItemDto;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.NotFoundException;
import ge.orderapp.repository.SheetsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final InMemoryStore store;
    private final TelegramService telegramService;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public OrderService(InMemoryStore store, TelegramService telegramService) {
        this.store = store;
        this.telegramService = telegramService;
    }

    public OrderDto createOrder(CreateOrderRequest request, UserDto currentUser) {
        String now = Instant.now().toString();
        String orderId = UUID.randomUUID().toString();
        String date = LocalDate.now().toString();

        // Create order items
        List<OrderItemDto> items = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest itemReq : request.items()) {
            String itemId = UUID.randomUUID().toString();
            OrderItemDto item = new OrderItemDto(
                    itemId, orderId,
                    sanitize(itemReq.customerName()),
                    itemReq.customerId(),
                    sanitize(itemReq.comment()),
                    now);
            items.add(item);
            store.putOrderItem(item);

            if (sheetsClient != null) {
                sheetsClient.appendRow("Order_Items", List.of(
                        item.itemId(), item.orderId(), item.customerName(),
                        item.customerId() != null ? item.customerId() : "",
                        item.comment() != null ? item.comment() : "",
                        item.createdAt()));
            }
        }

        // Send to Telegram first if requested, so we know actual result
        boolean telegramSent = false;
        String telegramSentAt = "";
        // Build a temporary order DTO for the message formatter
        OrderDto tempOrder = new OrderDto(
                orderId, currentUser.userId(), currentUser.displayName(),
                date, "PENDING", false, "", items.size(), now, items);
        if (request.sendTelegram()) {
            telegramSent = telegramService.sendOrderMessage(tempOrder, items);
            if (telegramSent) {
                telegramSentAt = Instant.now().toString();
            }
        }

        // Determine final status based on actual Telegram result
        String status = telegramSent || !request.sendTelegram() ? "SENT" : "FAILED";

        // Create final order with correct status
        OrderDto order = new OrderDto(
                orderId, currentUser.userId(), currentUser.displayName(),
                date, status, telegramSent, telegramSentAt, items.size(), now, items);
        store.putOrder(order);

        if (sheetsClient != null) {
            sheetsClient.appendRow("Orders", List.of(
                    order.orderId(), order.managerId(), order.managerName(),
                    order.date(), order.status(),
                    telegramSent ? "TRUE" : "FALSE",
                    telegramSentAt,
                    order.itemCount(), order.createdAt()));
        }

        // Increment frequency scores
        for (OrderItemDto item : items) {
            if (item.customerId() != null && !item.customerId().isBlank()) {
                store.incrementFrequencyScore(item.customerId());
            }
        }

        log.info("Order created: {} with {} items, telegram: {}", orderId, items.size(), telegramSent);
        return order;
    }

    public List<OrderDto> getOrders(String date, String managerId, int page, int size) {
        return store.getOrders(date, managerId, page, size);
    }

    public OrderDto getOrderById(String id) {
        OrderDto order = store.getOrder(id);
        if (order == null) throw new NotFoundException("Order not found: " + id);
        List<OrderItemDto> items = store.getOrderItems(id);
        return new OrderDto(
                order.orderId(), order.managerId(), order.managerName(),
                order.date(), order.status(), order.telegramSent(),
                order.telegramSentAt(), order.itemCount(), order.createdAt(), items);
    }

    public String exportCsv(String dateFrom, String dateTo, String managerId) {
        List<OrderDto> allOrders = store.getOrders(null, managerId, 0, Integer.MAX_VALUE);
        StringBuilder csv = new StringBuilder();
        csv.append("Order ID,Manager,Date,Status,Customer,Comment\n");

        for (OrderDto order : allOrders) {
            if (dateFrom != null && !dateFrom.isBlank() && order.date().compareTo(dateFrom) < 0) continue;
            if (dateTo != null && !dateTo.isBlank() && order.date().compareTo(dateTo) > 0) continue;

            List<OrderItemDto> items = store.getOrderItems(order.orderId());
            for (OrderItemDto item : items) {
                csv.append(escapeCsv(order.orderId())).append(",");
                csv.append(escapeCsv(order.managerName())).append(",");
                csv.append(escapeCsv(order.date())).append(",");
                csv.append(escapeCsv(order.status())).append(",");
                csv.append(escapeCsv(item.customerName())).append(",");
                csv.append(escapeCsv(item.comment() != null ? item.comment() : "")).append("\n");
            }
        }
        return csv.toString();
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
