package ge.orderapp.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.orderapp.dto.response.CustomerDto;
import ge.orderapp.dto.response.OrderItemDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InMemoryStoreBoardSanitizationTest {

    @Test
    void formulaErrorBoardsAreIgnoredForCustomerListsAndBoardQueries() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        store.putCustomer(new CustomerDto("c1", "ფუად ხატტაბ", "123", 0, "admin", true, "now", "now", null, null));
        store.loadCustomerBoards(List.of(
                List.of("c1", "#N/A (Did not find value 'ფუად ხატტაბ' in VLOOKUP evaluation.)"),
                List.of("c1", "საბურთალო")
        ));

        assertEquals(List.of("საბურთალო"), store.getBoards("c1"));
        List<CustomerDto> customers = store.searchCustomers("ფუად", null, "all", 0, 20);
        assertEquals(1, customers.size());
        assertEquals("საბურთალო", customers.get(0).board());
    }

    @Test
    void formulaErrorBoardsDoNotAutoFillOrderItems() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        store.loadCustomerBoards(List.of(List.of("c1", "#N/A (Did not find value 'ფუად ხატტაბ' in VLOOKUP evaluation.)")));
        store.putOrderItem(new OrderItemDto("i1", "o1", "ფუად ხატტაბ", "c1", "", "now", null, null));

        OrderItemDto item = store.getOrderItem("i1");

        assertNull(item.board());
    }
    @Test
    void loadsCanonicalCustomerBoardRowsWithHeader() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        String customerId = "5b5c84f0-d6e3-421b-9c3d-f98c73084203";

        store.loadCustomerBoards(List.of(
                List.of("customerId", "board", "createdAt", "addedBy"),
                List.of(customerId, "Central", "2026-04-28T10:15:30Z", "admin")
        ));

        assertEquals(List.of("Central"), store.getBoards(customerId));
    }

    @Test
    void loadsHumanFriendlyCustomerBoardRowsWithNameColumn() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        String customerId = "01d195dd-fcb7-4a71-98c0-88ac8fd6a6d0";

        store.loadCustomerBoards(List.of(
                List.of("customer name", "board", "customer_id", "created at", "added by"),
                List.of("Acme Ltd", "East", customerId, "2026-04-28T10:15:30Z", "admin")
        ));

        assertEquals(List.of("East"), store.getBoards(customerId));
    }

    @Test
    void infersUuidCustomerIdWhenHumanFriendlyRowHasNoHeader() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        String customerId = "d6a0f029-c5a4-45b9-a0d8-54ce1dfaf041";

        store.loadCustomerBoards(List.of(
                List.of("Acme Ltd", "West", customerId, "2026-04-28T10:15:30Z", "admin")
        ));

        assertEquals(List.of("West"), store.getBoards(customerId));
    }

    @Test
    void exactDuplicateBoardsCollapseButDifferentBoardsRemain() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        String customerId = "c1";

        store.loadCustomerBoards(List.of(
                List.of(customerId, "North"),
                List.of(customerId, "North"),
                List.of(customerId, "South")
        ));

        assertEquals(List.of("North", "South"), store.getBoards(customerId));
    }

    @Test
    void sameBoardWithDifferentAddressesCreatesDistinctCustomerRows() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        String customerId = "c1";
        store.putCustomer(new CustomerDto(customerId, "Customer", "123", 0, "admin", true, "now", "now", null, null));

        store.loadCustomerBoards(List.of(
                List.of("customerId", "board", "address", "createdAt", "addedBy"),
                List.of(customerId, "Saburtalo", "y1", "2026-04-28T10:15:30Z", "admin"),
                List.of(customerId, "Saburtalo", "y2", "2026-04-28T10:16:30Z", "admin")
        ));

        List<CustomerDto> customers = store.searchCustomers("Customer", null, "all", 0, 20);

        assertEquals(2, customers.size());
        assertEquals(List.of("y1", "y2"), customers.stream().map(CustomerDto::address).toList());
    }
}
