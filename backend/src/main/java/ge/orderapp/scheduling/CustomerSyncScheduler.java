package ge.orderapp.scheduling;

import ge.orderapp.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomerSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(CustomerSyncScheduler.class);

    private final SyncService syncService;

    @Value("${rsge.enabled:false}")
    private boolean rsgeEnabled;

    public CustomerSyncScheduler(SyncService syncService) {
        this.syncService = syncService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!rsgeEnabled) {
            log.info("RS.GE sync disabled, skipping startup sync");
            return;
        }

        try {
            Thread.sleep(5000); // Let app fully start
            boolean hasAnyHistory = syncService.hasSyncHistory();
            boolean hasSuccessHistory = syncService.hasSuccessfulSyncHistory();
            log.info("Startup sync check: rsgeEnabled={}, hasSyncHistory={}, hasSuccessfulSyncHistory={}",
                    rsgeEnabled, hasAnyHistory, hasSuccessHistory);
            if (!hasSuccessHistory) {
                log.info("No successful sync history found, triggering full sync...");
                syncService.triggerSync("FULL", null);
            } else {
                log.info("Sync history exists, triggering daily sync...");
                syncService.triggerSync("DAILY", null);
            }
        } catch (Exception e) {
            log.error("Startup sync failed", e);
        }
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Tbilisi")
    public void dailySync() {
        if (!rsgeEnabled) return;
        log.info("Triggering scheduled daily sync...");
        try {
            syncService.triggerSync("DAILY", null);
        } catch (Exception e) {
            log.error("Scheduled daily sync failed", e);
        }
    }
}
