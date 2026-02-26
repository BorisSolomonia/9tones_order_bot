package ge.orderapp.service;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.response.CustomerDto;
import ge.orderapp.dto.response.SyncStateDto;
import ge.orderapp.exception.ConflictException;
import ge.orderapp.integration.rsge.CustomerExtractor;
import ge.orderapp.integration.rsge.RsGeSoapClient;
import ge.orderapp.repository.SheetsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final InMemoryStore store;
    private final RsGeSoapClient rsGeSoapClient;
    private final CustomerExtractor customerExtractor;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    @Value("${rsge.full-sync-months:2}")
    private int fullSyncMonths;

    @Value("${app.sync.max-retries:3}")
    private int maxRetries;

    @Value("${app.sync.retry-delays-ms:2000,4000,8000}")
    private String retryDelaysMs;

    @Value("${app.sync.source-user:rsge_sync}")
    private String syncSourceUser;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public SyncService(InMemoryStore store, RsGeSoapClient rsGeSoapClient, CustomerExtractor customerExtractor) {
        this.store = store;
        this.rsGeSoapClient = rsGeSoapClient;
        this.customerExtractor = customerExtractor;
    }

    public SyncStateDto triggerSync(String type, String dateStr) {
        if (!syncInProgress.compareAndSet(false, true)) {
            throw new ConflictException("Sync already in progress");
        }

        String syncId = UUID.randomUUID().toString();
        LocalDate startDate;
        LocalDate endDate = LocalDate.now();

        if ("FULL".equalsIgnoreCase(type)) {
            startDate = endDate.minusMonths(fullSyncMonths);
        } else if ("DAILY".equalsIgnoreCase(type)) {
            startDate = dateStr != null && !dateStr.isBlank() ? LocalDate.parse(dateStr) : endDate.minusDays(1);
        } else {
            startDate = endDate.minusDays(1);
        }
        log.info("Sync queued: syncId={}, type={}, dateStr={}, startDate={}, endDate={}, retries={}, retryDelays={}",
                syncId, type, dateStr, startDate, endDate, maxRetries, retryDelaysMs);

        SyncStateDto state = new SyncStateDto(
                syncId, type.toUpperCase(), startDate.toString(), endDate.toString(),
                "RUNNING", 0, 0, "", Instant.now().toString(), "");
        store.addSyncState(state);

        // Run async
        Thread.startVirtualThread(() -> executeSyncWithRetry(syncId, type.toUpperCase(), startDate, endDate));

        return state;
    }

    private void executeSyncWithRetry(String syncId, String type, LocalDate startDate, LocalDate endDate) {
        long[] delays = parseRetryDelays();

        try {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    log.info("Sync attempt started: syncId={}, type={}, attempt={}, startDate={}, endDate={}",
                            syncId, type, attempt + 1, startDate, endDate);
                    executeSync(syncId, type, startDate, endDate);
                    log.info("Sync attempt succeeded: syncId={}, type={}, attempt={}", syncId, type, attempt + 1);
                    return;
                } catch (Exception e) {
                    if (attempt < maxRetries) {
                        long delay = delays[Math.min(attempt, delays.length - 1)];
                        log.warn("Sync attempt failed: syncId={}, type={}, attempt={}, retryInMs={}, error={}",
                                syncId, type, attempt + 1, delay, e.getMessage(), e);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Sync retry sleep interrupted: syncId={}, type={}, attempt={}",
                                    syncId, type, attempt + 1, ie);
                            return;
                        }
                    } else {
                        String diagnosticError = buildDiagnosticError(e);
                        log.error("Sync failed after retries: syncId={}, type={}, maxRetries={}, error={}",
                                syncId, type, maxRetries, diagnosticError, e);
                        SyncStateDto failed = new SyncStateDto(
                                syncId, type, startDate.toString(), endDate.toString(),
                                "FAILED", 0, 0, diagnosticError, "", Instant.now().toString());
                        store.updateSyncState(failed);
                    }
                }
            }
        } finally {
            syncInProgress.set(false);
        }
    }

    private long[] parseRetryDelays() {
        String[] parts = retryDelaysMs.split(",");
        List<Long> parsed = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                parsed.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // ignore invalid delay token
            }
        }
        if (parsed.isEmpty()) return new long[]{2000L};
        return parsed.stream().mapToLong(Long::longValue).toArray();
    }

    private void executeSync(String syncId, String type, LocalDate startDate, LocalDate endDate) {
        log.info("Starting sync: syncId={}, type={}, startDate={}, endDate={}, sheetsEnabled={}",
                syncId, type, startDate, endDate, sheetsClient != null);

        // Fetch BOTH sale waybills (get_waybills) and purchase waybills (get_buyer_waybills)
        // to capture all counterparties — matching Tasty ERP's dual-fetch approach
        List<Map<String, Object>> saleWaybills = rsGeSoapClient.getWaybills(startDate, endDate);
        log.info("Sale waybills fetched: syncId={}, count={}", syncId, saleWaybills.size());

        List<Map<String, Object>> buyerWaybills;
        try {
            buyerWaybills = rsGeSoapClient.getBuyerWaybills(startDate, endDate);
            log.info("Buyer waybills fetched: syncId={}, count={}", syncId, buyerWaybills.size());
        } catch (Exception e) {
            log.warn("Buyer waybill fetch failed, continuing with sale only: syncId={}, error={}",
                    syncId, e.getMessage(), e);
            buyerWaybills = List.of();
        }

        // Merge both lists for customer extraction
        List<Map<String, Object>> allWaybills = new ArrayList<>(saleWaybills.size() + buyerWaybills.size());
        allWaybills.addAll(saleWaybills);
        allWaybills.addAll(buyerWaybills);

        List<CustomerExtractor.ExtractedCustomer> extracted = customerExtractor.extract(allWaybills);
        log.info("Customer extraction done: syncId={}, mergedWaybills={}, extractedCustomers={}",
                syncId, allWaybills.size(), extracted.size());
        if (allWaybills.isEmpty()) {
            log.error("Sync diagnostic: syncId={} produced 0 merged waybills for range {}..{}. Check RS.GE credentials/permissions and response status codes in logs.",
                    syncId, startDate, endDate);
        }

        int added = 0;
        for (CustomerExtractor.ExtractedCustomer ec : extracted) {
            CustomerDto existing = store.getCustomerByTin(ec.tin());
            if (existing == null) {
                String customerId = UUID.randomUUID().toString();
                String now = Instant.now().toString();
                CustomerDto newCustomer = new CustomerDto(
                        customerId, ec.name(), ec.tin(), 0,
                        syncSourceUser, true, now, now, null);
                store.putCustomer(newCustomer);

                if (sheetsClient != null) {
                    sheetsClient.appendRow("Customers", List.of(
                            customerId, ec.name(), ec.tin(), 0,
                            syncSourceUser, "TRUE", now, now));
                }
                added++;
            }
        }
        log.info("Customer upsert summary: syncId={}, extracted={}, added={}, existing={}",
                syncId, extracted.size(), added, extracted.size() - added);
        if (extracted.isEmpty() || added == 0) {
            logSyncDiagnostics(syncId, startDate, endDate, allWaybills, extracted, added);
        }

        SyncStateDto completed = new SyncStateDto(
                syncId, type, startDate.toString(), endDate.toString(),
                "SUCCESS", extracted.size(), added, "",
                "", Instant.now().toString());
        store.updateSyncState(completed);

        if (sheetsClient != null) {
            sheetsClient.appendRow("Sync_State", List.of(
                    completed.syncId(), completed.syncType(), completed.startDate(),
                    completed.endDate(), completed.status(), completed.customersFound(),
                    completed.customersAdded(), completed.errorMessage(),
                    completed.startedAt(), completed.completedAt()));
        }

        log.info("Sync completed: syncId={}, found={}, added={}", syncId, extracted.size(), added);
    }

    public SyncStateDto getLatestStatus() {
        return store.getLatestSyncState();
    }

    public List<SyncStateDto> getHistory(int limit) {
        return store.getSyncStates(limit);
    }

    public boolean hasSyncHistory() {
        return store.hasSyncHistory();
    }

    public boolean hasSuccessfulSyncHistory() {
        return store.hasSuccessfulSyncHistory();
    }

    private void logSyncDiagnostics(String syncId,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    List<Map<String, Object>> waybills,
                                    List<CustomerExtractor.ExtractedCustomer> extracted,
                                    int added) {
        long cancelled = waybills.stream().filter(this::isCancelledWaybill).count();
        long withBuyerTin = waybills.stream().filter(w -> hasAnyNonBlank(w, "BUYER_TIN", "buyer_tin", "BuyerTin", "BUYER_UN_ID", "buyer_un_id", "BuyerUnId")).count();
        long withSellerTin = waybills.stream().filter(w -> hasAnyNonBlank(w, "SELLER_TIN", "seller_tin", "SellerTin", "SELLER_UN_ID", "seller_un_id", "SellerUnId")).count();
        long withAnyTin = waybills.stream().filter(w -> hasAnyNonBlank(w,
                "BUYER_TIN", "buyer_tin", "BuyerTin", "BUYER_UN_ID", "buyer_un_id", "BuyerUnId",
                "SELLER_TIN", "seller_tin", "SellerTin", "SELLER_UN_ID", "seller_un_id", "SellerUnId")).count();

        Set<String> rawUniqueTin = new LinkedHashSet<>();
        for (Map<String, Object> wb : waybills) {
            collectTin(rawUniqueTin, wb, "BUYER_TIN", "buyer_tin", "BuyerTin", "BUYER_UN_ID", "buyer_un_id", "BuyerUnId");
            collectTin(rawUniqueTin, wb, "SELLER_TIN", "seller_tin", "SellerTin", "SELLER_UN_ID", "seller_un_id", "SellerUnId");
        }

        List<String> sampleStatuses = waybills.stream()
                .map(w -> valueOfAny(w, "STATUS", "status", "Status"))
                .filter(Objects::nonNull)
                .limit(8)
                .collect(Collectors.toList());

        log.warn("Sync diagnostics: syncId={}, range={}..{}, waybillsTotal={}, cancelled={}, withBuyerTin={}, withSellerTin={}, withAnyTin={}, rawUniqueTins={}, extractedUnique={}, added={}",
                syncId, startDate, endDate, waybills.size(), cancelled, withBuyerTin, withSellerTin, withAnyTin, rawUniqueTin.size(), extracted.size(), added);
        if (!sampleStatuses.isEmpty()) {
            log.warn("Sync diagnostics: syncId={} sampleStatuses={}", syncId, sampleStatuses);
        }
        if (!waybills.isEmpty()) {
            Map<String, Object> first = waybills.get(0);
            log.warn("Sync diagnostics: syncId={} firstWaybillKeys={}", syncId, first.keySet());
        }
        if (extracted.isEmpty()) {
            log.error("Sync diagnostics: syncId={} extracted 0 customers. Likely causes: RS.GE returned cancelled/empty waybills, no TIN fields, or key mismatch.",
                    syncId);
        } else if (added == 0) {
            log.warn("Sync diagnostics: syncId={} extracted customers already existed in store/sheets. No new rows were added.", syncId);
        }
    }

    private boolean isCancelledWaybill(Map<String, Object> wb) {
        String status = valueOfAny(wb, "STATUS", "status", "Status");
        if (status == null) return false;
        return "-1".equals(status) || "-2".equals(status);
    }

    private boolean hasAnyNonBlank(Map<String, Object> map, String... keys) {
        return valueOfAny(map, keys) != null;
    }

    private String valueOfAny(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value == null) continue;
            String s = value.toString().trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private void collectTin(Set<String> tinSet, Map<String, Object> wb, String... keys) {
        String tin = valueOfAny(wb, keys);
        if (tin == null) return;
        String normalized = tin.replaceAll("[\\s\\-._]+", "");
        if (!normalized.isBlank()) {
            tinSet.add(normalized);
        }
    }

    private String buildDiagnosticError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String topType = throwable.getClass().getSimpleName();
        String topMsg = throwable.getMessage() == null ? "" : throwable.getMessage();
        String rootType = root.getClass().getSimpleName();
        String rootMsg = root.getMessage() == null ? "" : root.getMessage();

        String diagnostic = String.format("%s: %s | root=%s: %s", topType, topMsg, rootType, rootMsg).trim();
        if (diagnostic.length() <= 900) return diagnostic;
        return diagnostic.substring(0, 900) + "...";
    }
}
