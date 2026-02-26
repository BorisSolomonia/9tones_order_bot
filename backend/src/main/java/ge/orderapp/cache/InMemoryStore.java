package ge.orderapp.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ge.orderapp.dto.response.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class InMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStore.class);
    private final ObjectMapper objectMapper;

    // Primary stores keyed by entity ID
    private final ConcurrentHashMap<String, CustomerDto> customers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UserDto> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userPasswordHashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OrderDto> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OrderItemDto> orderItems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DraftDto> drafts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MyCustomerDto> myCustomers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SyncStateDto> syncStates = new CopyOnWriteArrayList<>();

    // Board assignments: customerId -> list of boards
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> customerBoards = new ConcurrentHashMap<>();

    // Secondary indexes
    private final ConcurrentHashMap<String, String> usersByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CustomerDto> customersByTin = new ConcurrentHashMap<>();

    private volatile boolean ready = false;

    public InMemoryStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // --- Load from raw Sheets data ---

    public void loadCustomers(List<List<Object>> rows) {
        customers.clear();
        customersByTin.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            CustomerDto c = new CustomerDto(
                    str(row, 0), str(row, 1), str(row, 2),
                    intVal(row, 3), str(row, 4),
                    "TRUE".equalsIgnoreCase(str(row, 5)),
                    str(row, 6), str(row, 7), null);
            customers.put(c.customerId(), c);
            if (c.tin() != null && !c.tin().isBlank()) {
                customersByTin.put(c.tin(), c);
            }
        }
        log.info("Loaded {} customers into memory", customers.size());
    }

    public void loadUsers(List<List<Object>> rows) {
        users.clear();
        usersByUsername.clear();
        userPasswordHashes.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            UserDto u = new UserDto(
                    str(row, 0), str(row, 1), str(row, 3),
                    str(row, 4), "TRUE".equalsIgnoreCase(str(row, 5)),
                    str(row, 6));
            users.put(u.userId(), u);
            usersByUsername.put(u.username().toLowerCase(), u.userId());
            userPasswordHashes.put(u.userId(), str(row, 2));
        }
        log.info("Loaded {} users into memory", users.size());
    }

    public void loadOrders(List<List<Object>> rows) {
        orders.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            OrderDto o = new OrderDto(
                    str(row, 0), str(row, 1), str(row, 2), str(row, 3),
                    str(row, 4), "TRUE".equalsIgnoreCase(str(row, 5)),
                    str(row, 6), intVal(row, 7), str(row, 8), null);
            orders.put(o.orderId(), o);
        }
        log.info("Loaded {} orders into memory", orders.size());
    }

    public void loadOrderItems(List<List<Object>> rows) {
        orderItems.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            String boardVal = str(row, 6);
            OrderItemDto item = new OrderItemDto(
                    str(row, 0), str(row, 1), str(row, 2),
                    str(row, 3), str(row, 4), str(row, 5),
                    boardVal.isBlank() ? null : boardVal);
            orderItems.put(item.itemId(), item);
        }
        log.info("Loaded {} order items into memory", orderItems.size());
    }

    public void loadDrafts(List<List<Object>> rows) {
        drafts.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            String draftId = str(row, 0);
            String managerId = str(row, 1);
            String name = str(row, 2);
            String itemsJson = str(row, 3);
            String createdAt = str(row, 4);
            String updatedAt = str(row, 5);

            List<DraftItemDto> items = parseDraftItems(itemsJson);
            DraftDto d = new DraftDto(draftId, managerId, name, items, createdAt, updatedAt);
            drafts.put(draftId, d);
        }
        log.info("Loaded {} drafts into memory", drafts.size());
    }

    public void loadMyCustomers(List<List<Object>> rows) {
        myCustomers.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            MyCustomerDto mc = new MyCustomerDto(str(row, 0), str(row, 1), str(row, 2), str(row, 3));
            myCustomers.add(mc);
        }
        log.info("Loaded {} my_customers entries into memory", myCustomers.size());
    }

    public void loadSyncStates(List<List<Object>> rows) {
        syncStates.clear();
        int skippedInvalid = 0;
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            SyncStateDto s = new SyncStateDto(
                    str(row, 0), str(row, 1), str(row, 2), str(row, 3),
                    str(row, 4), intVal(row, 5), intVal(row, 6),
                    str(row, 7), str(row, 8), str(row, 9));
            if (!isValidSyncState(s)) {
                skippedInvalid++;
                continue;
            }
            syncStates.add(s);
        }
        log.info("Loaded {} sync states into memory (skippedInvalid={})", syncStates.size(), skippedInvalid);
    }

    public void loadCustomerBoards(List<List<Object>> rows) {
        customerBoards.clear();
        for (List<Object> row : rows) {
            if (row.isEmpty()) continue;
            String customerId = str(row, 0);
            String board = str(row, 1);
            if (customerId.isBlank() || board.isBlank()) continue;
            customerBoards.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(board);
        }
        log.info("Loaded customer boards for {} customers", customerBoards.size());
    }

    // --- Customer operations ---

    public List<CustomerDto> searchCustomers(String query, String managerId, String tab, int page, int size) {
        Set<String> myCustomerIds = getMyCustomerIds(managerId);

        List<CustomerDto> filtered = customers.values().stream()
                .filter(CustomerDto::active)
                .filter(c -> {
                    if ("my".equals(tab)) return myCustomerIds.contains(c.customerId());
                    return true;
                })
                .filter(c -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.toLowerCase();
                    return (c.name() != null && c.name().toLowerCase().contains(q)) ||
                           (c.tin() != null && c.tin().contains(q));
                })
                .sorted((a, b) -> {
                    boolean aIsMy = myCustomerIds.contains(a.customerId());
                    boolean bIsMy = myCustomerIds.contains(b.customerId());
                    if (aIsMy != bIsMy) return aIsMy ? -1 : 1;
                    if (a.frequencyScore() != b.frequencyScore())
                        return Integer.compare(b.frequencyScore(), a.frequencyScore());
                    return compareNullSafe(a.name(), b.name());
                })
                .collect(Collectors.toList());

        // Expand each customer by their boards (one row per board; one row with null board if no boards)
        List<CustomerDto> expanded = new ArrayList<>();
        for (CustomerDto c : filtered) {
            CopyOnWriteArrayList<String> boards = customerBoards.get(c.customerId());
            if (boards == null || boards.isEmpty()) {
                expanded.add(new CustomerDto(c.customerId(), c.name(), c.tin(),
                        c.frequencyScore(), c.addedBy(), c.active(), c.createdAt(), c.updatedAt(), null));
            } else {
                for (String b : boards) {
                    expanded.add(new CustomerDto(c.customerId(), c.name(), c.tin(),
                            c.frequencyScore(), c.addedBy(), c.active(), c.createdAt(), c.updatedAt(), b));
                }
            }
        }

        int start = page * size;
        if (start >= expanded.size()) return List.of();
        int end = Math.min(start + size, expanded.size());
        return expanded.subList(start, end);
    }

    public List<CustomerDto> getFrequentCustomers(int limit) {
        return customers.values().stream()
                .filter(CustomerDto::active)
                .sorted((a, b) -> Integer.compare(b.frequencyScore(), a.frequencyScore()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public CustomerDto getCustomer(String id) {
        return customers.get(id);
    }

    public CustomerDto getCustomerByTin(String tin) {
        return customersByTin.get(tin);
    }

    public void putCustomer(CustomerDto customer) {
        customers.put(customer.customerId(), customer);
        if (customer.tin() != null && !customer.tin().isBlank()) {
            customersByTin.put(customer.tin(), customer);
        }
    }

    // --- Board operations ---

    public List<String> getBoards(String customerId) {
        CopyOnWriteArrayList<String> boards = customerBoards.get(customerId);
        return boards != null ? List.copyOf(boards) : List.of();
    }

    public void addBoard(String customerId, String board) {
        customerBoards.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(board);
    }

    public boolean removeBoard(String customerId, String board) {
        CopyOnWriteArrayList<String> boards = customerBoards.get(customerId);
        if (boards == null) return false;
        return boards.remove(board);
    }

    // --- User operations ---

    public UserDto getUserByUsername(String username) {
        if (username == null) return null;
        String userId = usersByUsername.get(username.toLowerCase());
        if (userId == null) return null;
        return users.get(userId);
    }

    public String getUserPasswordHash(String userId) {
        return userPasswordHashes.get(userId);
    }

    public UserDto getUser(String userId) {
        return users.get(userId);
    }

    public List<UserDto> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public void putUser(UserDto user, String passwordHash) {
        users.put(user.userId(), user);
        usersByUsername.put(user.username().toLowerCase(), user.userId());
        if (passwordHash != null) {
            userPasswordHashes.put(user.userId(), passwordHash);
        }
    }

    // --- Order operations ---

    public void putOrder(OrderDto order) {
        orders.put(order.orderId(), order);
    }

    public OrderDto getOrder(String orderId) {
        return orders.get(orderId);
    }

    public List<OrderDto> getOrders(String date, String managerId, int page, int size) {
        List<OrderDto> filtered = orders.values().stream()
                .filter(o -> date == null || date.isBlank() || date.equals(o.date()))
                .filter(o -> managerId == null || managerId.isBlank() || managerId.equals(o.managerId()))
                .sorted((a, b) -> compareNullSafe(b.createdAt(), a.createdAt()))
                .collect(Collectors.toList());

        int start = page * size;
        if (start >= filtered.size()) return List.of();
        int end = Math.min(start + size, filtered.size());
        return filtered.subList(start, end);
    }

    // --- Order Items ---

    public void putOrderItem(OrderItemDto item) {
        orderItems.put(item.itemId(), item);
    }

    public OrderItemDto getOrderItem(String itemId) {
        return orderItems.get(itemId);
    }

    public List<OrderItemDto> getOrderItems(String orderId) {
        return orderItems.values().stream()
                .filter(i -> orderId.equals(i.orderId()))
                .collect(Collectors.toList());
    }

    public void updateOrderItemBoard(String itemId, String board) {
        OrderItemDto existing = orderItems.get(itemId);
        if (existing == null) return;
        orderItems.put(itemId, new OrderItemDto(
                existing.itemId(), existing.orderId(), existing.customerName(),
                existing.customerId(), existing.comment(), existing.createdAt(), board));
    }

    // --- Drafts ---

    public void putDraft(DraftDto draft) {
        drafts.put(draft.draftId(), draft);
    }

    public DraftDto getDraft(String draftId) {
        return drafts.get(draftId);
    }

    public void removeDraft(String draftId) {
        drafts.remove(draftId);
    }

    public List<DraftDto> getDrafts(String managerId) {
        return drafts.values().stream()
                .filter(d -> managerId.equals(d.managerId()))
                .sorted((a, b) -> compareNullSafe(b.updatedAt(), a.updatedAt()))
                .collect(Collectors.toList());
    }

    public DraftDto getDraftByName(String managerId, String name) {
        return drafts.values().stream()
                .filter(d -> managerId.equals(d.managerId()) && name.equalsIgnoreCase(d.name()))
                .findFirst()
                .orElse(null);
    }

    // --- My Customers ---

    public Set<String> getMyCustomerIds(String managerId) {
        if (managerId == null) return Set.of();
        return myCustomers.stream()
                .filter(mc -> managerId.equals(mc.managerId()))
                .map(MyCustomerDto::customerId)
                .collect(Collectors.toSet());
    }

    public List<MyCustomerDto> getMyCustomers(String managerId) {
        return myCustomers.stream()
                .filter(mc -> managerId.equals(mc.managerId()))
                .collect(Collectors.toList());
    }

    public void addMyCustomer(MyCustomerDto mc) {
        myCustomers.add(mc);
    }

    public void removeMyCustomer(String managerId, String customerId) {
        myCustomers.removeIf(mc -> managerId.equals(mc.managerId()) && customerId.equals(mc.customerId()));
    }

    // --- Sync State ---

    public List<SyncStateDto> getSyncStates(int limit) {
        List<SyncStateDto> sorted = syncStates.stream()
                .filter(this::isValidSyncState)
                .sorted((a, b) -> compareNullSafe(b.startedAt(), a.startedAt()))
                .collect(Collectors.toList());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public SyncStateDto getLatestSyncState() {
        return syncStates.stream()
                .filter(this::isValidSyncState)
                .max(Comparator.comparing(SyncStateDto::startedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    public void addSyncState(SyncStateDto state) {
        syncStates.add(state);
        log.info("Sync state added: syncId={}, status={}, found={}, added={}, error={}",
                state.syncId(), state.status(), state.customersFound(), state.customersAdded(), truncate(state.errorMessage()));
    }

    public void updateSyncState(SyncStateDto updated) {
        syncStates.removeIf(s -> s.syncId().equals(updated.syncId()));
        syncStates.add(updated);
        log.info("Sync state updated: syncId={}, status={}, found={}, added={}, error={}",
                updated.syncId(), updated.status(), updated.customersFound(), updated.customersAdded(), truncate(updated.errorMessage()));
    }

    public boolean hasSyncHistory() {
        return syncStates.stream().anyMatch(this::isValidSyncState);
    }

    public boolean hasSuccessfulSyncHistory() {
        return syncStates.stream().anyMatch(s -> isValidSyncState(s) && "SUCCESS".equalsIgnoreCase(s.status()));
    }

    // --- Frequency score ---

    public void incrementFrequencyScore(String customerId) {
        CustomerDto existing = customers.get(customerId);
        if (existing != null) {
            CustomerDto updated = new CustomerDto(
                    existing.customerId(), existing.name(), existing.tin(),
                    existing.frequencyScore() + 1, existing.addedBy(),
                    existing.active(), existing.createdAt(), existing.updatedAt(), existing.board());
            putCustomer(updated);
        }
    }

    // --- Readiness ---

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    // --- Helpers ---

    private String str(List<Object> row, int index) {
        if (index >= row.size()) return "";
        Object val = row.get(index);
        return val == null ? "" : val.toString().trim();
    }

    private int intVal(List<Object> row, int index) {
        String s = str(row, index);
        if (s.isEmpty()) return 0;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int compareNullSafe(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    private List<DraftItemDto> parseDraftItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<DraftItemDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse draft items JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String truncate(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() <= 240) return trimmed;
        return trimmed.substring(0, 240) + "...";
    }

    private boolean isValidSyncState(SyncStateDto state) {
        if (state == null) return false;
        return state.syncId() != null && !state.syncId().isBlank()
                && state.status() != null && !state.status().isBlank();
    }
}
