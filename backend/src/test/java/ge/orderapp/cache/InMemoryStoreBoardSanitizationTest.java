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
        store.putCustomer(new CustomerDto("c1", "ფუად ხატტაბ", "123", 0, "admin", true, "now", "now", null));
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
        store.putOrderItem(new OrderItemDto("i1", "o1", "ფუად ხატტაბ", "c1", "", "now", null));

        OrderItemDto item = store.getOrderItem("i1");

        assertNull(item.board());
    }
}
