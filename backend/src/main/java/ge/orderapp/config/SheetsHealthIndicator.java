package ge.orderapp.config;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.repository.SheetsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SheetsHealthIndicator implements HealthIndicator {

    private final InMemoryStore store;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public SheetsHealthIndicator(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public Health health() {
        if (!store.isReady()) {
            return Health.down().withDetail("reason", "Store not ready").build();
        }
        if (sheetsClient != null && !sheetsClient.isHealthy()) {
            return Health.down().withDetail("reason", "Sheets API unreachable").build();
        }
        return Health.up().build();
    }
}
