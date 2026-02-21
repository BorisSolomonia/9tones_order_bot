package ge.orderapp.controller;

import ge.orderapp.dto.request.AddMyCustomerRequest;
import ge.orderapp.dto.request.CreateCustomerRequest;
import ge.orderapp.dto.request.UpdateCustomerRequest;
import ge.orderapp.dto.response.CustomerDto;
import ge.orderapp.dto.response.MyCustomerDto;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.ForbiddenException;
import ge.orderapp.security.SessionAuthFilter;
import ge.orderapp.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerDto>> search(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        return ResponseEntity.ok(customerService.search(search, user.userId(), tab, page, size));
    }

    @GetMapping("/frequent")
    public ResponseEntity<List<CustomerDto>> frequent(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(customerService.getFrequent(limit));
    }

    @PostMapping
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CreateCustomerRequest req,
                                               HttpServletRequest request) {
        requireRole(request, "ADMIN");
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        return ResponseEntity.ok(customerService.create(req, user.username()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerDto> update(@PathVariable String id,
                                               @Valid @RequestBody UpdateCustomerRequest req,
                                               HttpServletRequest request) {
        requireRole(request, "ADMIN");
        return ResponseEntity.ok(customerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        requireRole(request, "ADMIN");
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<MyCustomerDto>> myCustomers(HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        return ResponseEntity.ok(customerService.getMyCustomers(user.userId()));
    }

    @PostMapping("/my")
    public ResponseEntity<Void> addMyCustomer(@Valid @RequestBody AddMyCustomerRequest req,
                                                HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        customerService.addMyCustomer(user.userId(), req.customerName(), req.customerId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/my/{customerId}")
    public ResponseEntity<Void> removeMyCustomer(@PathVariable String customerId,
                                                   HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        customerService.removeMyCustomer(user.userId(), customerId);
        return ResponseEntity.noContent().build();
    }

    private void requireRole(HttpServletRequest request, String role) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        if (!role.equals(user.role())) {
            throw new ForbiddenException("Role " + role + " required");
        }
    }
}
