package ge.orderapp.service;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.request.AddCustomerLocationRequest;
import ge.orderapp.dto.request.CreateCustomerRequest;
import ge.orderapp.dto.request.UpdateCustomerRequest;
import ge.orderapp.dto.response.CustomerDto;
import ge.orderapp.dto.response.CustomerLocationDto;
import ge.orderapp.dto.response.MyCustomerDto;
import ge.orderapp.exception.BadRequestException;
import ge.orderapp.exception.NotFoundException;
import ge.orderapp.repository.SheetsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final InMemoryStore store;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public CustomerService(InMemoryStore store) {
        this.store = store;
    }

    public List<CustomerDto> search(String query, String managerId, String tab, int page, int size) {
        return store.searchCustomers(query, managerId, tab, page, size);
    }

    public List<CustomerDto> getFrequent(int limit) {
        return store.getFrequentCustomers(limit);
    }

    public CustomerDto getById(String id) {
        CustomerDto customer = store.getCustomer(id);
        if (customer == null) throw new NotFoundException("Customer not found: " + id);
        return customer;
    }

    public CustomerDto create(CreateCustomerRequest request, String addedBy) {
        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();

        String normalizedTin = normalizeTin(request.tin());

        if (!normalizedTin.isEmpty()) {
            CustomerDto existing = store.getCustomerByTin(normalizedTin);
            if (existing != null) {
                throw new BadRequestException("Customer with TIN " + normalizedTin + " already exists");
            }
        }

        CustomerDto customer = new CustomerDto(
                id, sanitize(request.name()), normalizedTin.isEmpty() ? null : normalizedTin,
                0, addedBy, true, now, now, null, null);

        store.putCustomer(customer);

        if (sheetsClient != null) {
            sheetsClient.appendRow("Customers", List.of(
                    customer.customerId(), customer.name(), customer.tin(),
                    customer.frequencyScore(), customer.addedBy(),
                    "TRUE", customer.createdAt(), customer.updatedAt()));
        }

        log.info("Customer created: {} ({})", customer.name(), customer.customerId());
        return customer;
    }

    public CustomerDto update(String id, UpdateCustomerRequest request) {
        CustomerDto existing = store.getCustomer(id);
        if (existing == null) throw new NotFoundException("Customer not found: " + id);

        String now = Instant.now().toString();
        CustomerDto updated = new CustomerDto(
                existing.customerId(),
                request.name() != null ? sanitize(request.name()) : existing.name(),
                existing.tin(),
                existing.frequencyScore(),
                existing.addedBy(),
                request.active() != null ? request.active() : existing.active(),
                existing.createdAt(),
                now,
                existing.board(),
                existing.address());

        store.putCustomer(updated);

        if (sheetsClient != null) {
            int rowIndex = sheetsClient.findRowIndex("Customers", id);
            if (rowIndex > 0) {
                sheetsClient.updateRow("Customers", rowIndex, List.of(
                        updated.customerId(), updated.name(), updated.tin(),
                        updated.frequencyScore(), updated.addedBy(),
                        updated.active() ? "TRUE" : "FALSE",
                        updated.createdAt(), updated.updatedAt()));
            }
        }

        return updated;
    }

    public void delete(String id) {
        update(id, new UpdateCustomerRequest(null, false));
    }

    // --- Board management ---

    public List<String> getBoards(String customerId) {
        getById(customerId);
        return store.getBoards(customerId);
    }

    public List<CustomerLocationDto> getLocations(String customerId) {
        getById(customerId);
        return store.getLocations(customerId);
    }

    public void addBoard(String customerId, String board, String addedBy) {
        String sanitized = sanitizeBoard(board);
        if (sanitized == null || sanitized.isBlank()) {
            throw new BadRequestException("Board name cannot be blank");
        }
        CustomerDto customer = store.getCustomer(customerId);
        if (customer == null || !customer.active()) {
            throw new NotFoundException("Customer not found: " + customerId);
        }
        if (store.getBoards(customerId).contains(sanitized)) return;

        store.addBoard(customerId, sanitized);

        if (sheetsClient != null) {
            String now = Instant.now().toString();
            sheetsClient.appendRow("Customer_Boards", List.of(customerId, sanitized, "", now, addedBy));
        }

        log.info("Board added: {} for customer {}", sanitized, customerId);
    }

    public void addLocation(String customerId, AddCustomerLocationRequest request, String addedBy, String role) {
        String board = sanitizeBoard(request.board());
        String address = sanitizeLocationValue(request.address());
        if (board == null || board.isBlank()) {
            throw new BadRequestException("Board name cannot be blank");
        }
        CustomerDto customer = store.getCustomer(customerId);
        if (customer == null || !customer.active()) {
            throw new NotFoundException("Customer not found: " + customerId);
        }

        boolean boardExists = store.getBoards(customerId).contains(board);
        if ("MANAGER".equals(role)) {
            if (!boardExists) {
                throw new BadRequestException("Managers can add addresses only under existing boards");
            }
            if (address == null || address.isBlank()) {
                throw new BadRequestException("Address cannot be blank");
            }
        }

        if (address == null && boardExists) return;
        if (store.getLocations(customerId).stream()
                .anyMatch(location -> board.equals(location.board()) && Objects.equals(address, location.address()))) {
            return;
        }

        store.addLocation(customerId, board, address);

        if (sheetsClient != null) {
            String now = Instant.now().toString();
            sheetsClient.appendRow("Customer_Boards", List.of(customerId, board, address != null ? address : "", now, addedBy));
        }

        log.info("Customer location added: board={}, address={} for customer {}", board, address, customerId);
    }

    public void removeBoard(String customerId, String board) {
        String sanitized = sanitize(board);
        int removedFromSheets = 0;
        if (sheetsClient != null) {
            removedFromSheets = sheetsClient.removeCustomerBoardRows(customerId, sanitized);
        }

        int removedFromMemory = store.removeBoard(customerId, sanitized);
        if (removedFromSheets == 0 && removedFromMemory == 0) {
            throw new NotFoundException("Board not found: " + sanitized);
        }
        log.info("Board removed: {} for customer {} (memoryRemoved={}, sheetsRemoved={})",
                sanitized, customerId, removedFromMemory, removedFromSheets);
    }

    public void removeLocation(String customerId, String board, String address, String role) {
        String sanitizedBoard = sanitizeBoard(board);
        String sanitizedAddress = sanitizeLocationValue(address);
        if ("MANAGER".equals(role) && (sanitizedAddress == null || sanitizedAddress.isBlank())) {
            throw new BadRequestException("Managers can remove address rows only");
        }

        int removedFromSheets = 0;
        if (sheetsClient != null) {
            removedFromSheets = sheetsClient.removeCustomerLocationRows(customerId, sanitizedBoard, sanitizedAddress);
        }

        int removedFromMemory = store.removeLocation(customerId, sanitizedBoard, sanitizedAddress);
        if (removedFromSheets == 0 && removedFromMemory == 0) {
            throw new NotFoundException("Location not found");
        }
        log.info("Customer location removed: board={}, address={} for customer {} (memoryRemoved={}, sheetsRemoved={})",
                sanitizedBoard, sanitizedAddress, customerId, removedFromMemory, removedFromSheets);
    }

    // --- My Customers ---

    public List<MyCustomerDto> getMyCustomers(String managerId) {
        return store.getMyCustomers(managerId);
    }

    public void addMyCustomer(String managerId, String customerName, String customerId) {
        String normalizedCustomerId = sanitize(customerId);
        CustomerDto customer = store.getCustomer(normalizedCustomerId);
        if (customer == null || !customer.active()) {
            throw new NotFoundException("Customer not found: " + normalizedCustomerId);
        }
        if (store.getMyCustomerIds(managerId).contains(normalizedCustomerId)) {
            return;
        }

        String now = Instant.now().toString();
        String normalizedCustomerName = sanitize(customerName);
        String finalCustomerName = (normalizedCustomerName == null || normalizedCustomerName.isBlank())
                ? customer.name()
                : normalizedCustomerName;
        MyCustomerDto mc = new MyCustomerDto(managerId, finalCustomerName, normalizedCustomerId, now);
        store.addMyCustomer(mc);

        if (sheetsClient != null) {
            sheetsClient.appendRow("My_Customers", List.of(
                    mc.managerId(), mc.customerName(), mc.customerId(), mc.addedAt()));
        }

        log.info("My customer added: {} for manager {}", customerName, managerId);
    }

    public void removeMyCustomer(String managerId, String customerId) {
        store.removeMyCustomer(managerId, customerId);
        log.info("My customer removed: {} for manager {}", customerId, managerId);
        // Note: Sheets row removal would require delete API; for simplicity we reload on next refresh
    }

    private String sanitize(String input) {
        if (input == null) return null;
        // Strip control characters
        return input.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
    }

    private String sanitizeBoard(String input) {
        String sanitized = sanitize(input);
        if (sanitized == null || sanitized.isBlank()) return sanitized;
        if (sanitized.startsWith("#")) {
            throw new BadRequestException("Invalid board value");
        }
        return sanitized;
    }

    private String sanitizeLocationValue(String input) {
        String sanitized = sanitize(input);
        if (sanitized == null || sanitized.isBlank()) return null;
        if (sanitized.startsWith("#")) {
            throw new BadRequestException("Invalid address value");
        }
        return sanitized;
    }

    private String normalizeTin(String tin) {
        if (tin == null || tin.isBlank()) return "";
        return tin.trim().replaceAll("[\\s\\-._]+", "");
    }
}
