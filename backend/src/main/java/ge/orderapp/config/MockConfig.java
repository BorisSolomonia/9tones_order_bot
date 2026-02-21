package ge.orderapp.config;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.response.*;
import ge.orderapp.repository.SheetsClient;
import ge.orderapp.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Configuration
@Profile("mock")
public class MockConfig {

    private static final Logger log = LoggerFactory.getLogger(MockConfig.class);

    private final InMemoryStore store;
    private final PasswordService passwordService;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public MockConfig(InMemoryStore store, PasswordService passwordService) {
        this.store = store;
        this.passwordService = passwordService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initMockData() {
        // If Sheets loaded real users, don't overwrite them with mock data
        if (!store.getAllUsers().isEmpty()) {
            log.info("Users already loaded from Sheets, skipping mock data seed");
            store.setReady(true);
            return;
        }

        log.info("Seeding initial data (Users tab is empty)...");
        String now = Instant.now().toString();
        String today = LocalDate.now().toString();

        // --- Users ---
        String adminHash = passwordService.hash("admin123");
        String managerHash = passwordService.hash("manager123");

        UserDto admin = new UserDto("u1", "admin", "ადმინი", "ADMIN", true, now);
        UserDto giorgi = new UserDto("u2", "giorgi", "გიორგი", "MANAGER", true, now);

        store.putUser(admin, adminHash);
        store.putUser(giorgi, managerHash);

        // Persist seed users to Sheets so they survive periodic refresh
        if (sheetsClient != null) {
            sheetsClient.appendRow("Users", List.of(
                    admin.userId(), admin.username(), adminHash,
                    admin.displayName(), admin.role(), "TRUE", admin.createdAt()));
            sheetsClient.appendRow("Users", List.of(
                    giorgi.userId(), giorgi.username(), managerHash,
                    giorgi.displayName(), giorgi.role(), "TRUE", giorgi.createdAt()));
        }

        // --- Sample Customers (only if no customers from RS.GE sync) ---
        if (sheetsClient == null) {
            String[][] customerData = {
                    {"c1", "შპს თასთი", "404476988", "15"},
                    {"c2", "შპს ბახუსი", "205197070", "12"},
                    {"c50", "შპს ლობიანი", "202033060", "0"},
            };

            for (String[] cd : customerData) {
                store.putCustomer(new CustomerDto(
                        cd[0], cd[1], cd[2], Integer.parseInt(cd[3]),
                        "rsge_sync", true, now, now));
            }

            store.addMyCustomer(new MyCustomerDto("u2", "შპს ბახუსი", "c2", now));
        }

        store.setReady(true);
        log.info("Seed data initialized: 2 users (admin/admin123, giorgi/manager123)");
    }
}
