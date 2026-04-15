package ge.orderapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.request.CreateCustomerRequest;
import ge.orderapp.exception.NotFoundException;
import ge.orderapp.repository.SheetsClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerServiceBoardPersistenceTest {

    @Test
    void removeBoardDeletesAllDuplicateMatchesFromMemoryAndSheets() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        CustomerService service = new CustomerService(store);
        TestSheetsClient sheetsClient = new TestSheetsClient(store, 2);
        ReflectionTestUtils.setField(service, "sheetsClient", sheetsClient);

        String customerId = service.create(new CreateCustomerRequest("შპს ტიფლის ფაბ", "123456789"), "admin").customerId();
        store.addBoard(customerId, "დიღომი");
        store.addBoard(customerId, "დიღომი");

        service.removeBoard(customerId, "დიღომი");

        assertEquals(0, store.getBoards(customerId).size());
        assertEquals(customerId, sheetsClient.lastCustomerId);
        assertEquals("დიღომი", sheetsClient.lastBoard);
    }

    @Test
    void removeBoardThrowsWhenAbsentInBothMemoryAndSheets() {
        InMemoryStore store = new InMemoryStore(new ObjectMapper());
        CustomerService service = new CustomerService(store);
        TestSheetsClient sheetsClient = new TestSheetsClient(store, 0);
        ReflectionTestUtils.setField(service, "sheetsClient", sheetsClient);

        String customerId = service.create(new CreateCustomerRequest("შპს ტიფლის ფაბ", "123456789"), "admin").customerId();

        assertThrows(NotFoundException.class, () -> service.removeBoard(customerId, "ვარკეთილი"));
        assertEquals(customerId, sheetsClient.lastCustomerId);
        assertEquals("ვარკეთილი", sheetsClient.lastBoard);
    }

    private static final class TestSheetsClient extends SheetsClient {
        private final int removedCount;
        private String lastCustomerId;
        private String lastBoard;

        private TestSheetsClient(InMemoryStore store, int removedCount) {
            super(null, store);
            this.removedCount = removedCount;
        }

        @Override
        public int removeCustomerBoardRows(String customerId, String board) {
            this.lastCustomerId = customerId;
            this.lastBoard = board;
            return removedCount;
        }
    }
}
