package ge.orderapp.controller;

import ge.orderapp.dto.request.CreateOrderRequest;
import ge.orderapp.dto.response.OrderDto;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.ForbiddenException;
import ge.orderapp.security.SessionAuthFilter;
import ge.orderapp.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Set<String> EXPORT_ROLES = Set.of("ACCOUNTANT", "ADMIN");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest req,
                                            HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        if (!"MANAGER".equals(user.role())) {
            throw new ForbiddenException("Only managers can create orders");
        }
        return ResponseEntity.ok(orderService.createOrder(req, user));
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> list(
            @RequestParam(required = false) String date,
            @RequestParam(required = false, name = "manager_id") String managerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        // Managers can only see their own orders
        if ("MANAGER".equals(user.role())) {
            managerId = user.userId();
        }
        return ResponseEntity.ok(orderService.getOrders(date, managerId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getById(@PathVariable String id, HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        OrderDto order = orderService.getOrderById(id);
        if ("MANAGER".equals(user.role()) && !user.userId().equals(order.managerId())) {
            throw new ForbiddenException("Managers can only view their own orders");
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false, name = "date_from") String dateFrom,
            @RequestParam(required = false, name = "date_to") String dateTo,
            @RequestParam(required = false, name = "manager_id") String managerId,
            HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        if (!EXPORT_ROLES.contains(user.role())) {
            throw new ForbiddenException("Export not allowed for role: " + user.role());
        }

        String csv = orderService.exportCsv(dateFrom, dateTo, managerId);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
