package ge.orderapp.repository;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import ge.orderapp.cache.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

@Component
@ConditionalOnProperty(name = "google.sheets.enabled", havingValue = "true")
public class SheetsClient {

    private static final Logger log = LoggerFactory.getLogger(SheetsClient.class);

    private static final String[] TAB_NAMES = {
            "Customers", "Users", "Orders", "Order_Items", "Drafts", "My_Customers", "Sync_State", "Customer_Boards"
    };

    private final Sheets sheetsService;
    private final InMemoryStore store;

    @Value("${google.sheets.spreadsheet-id}")
    private String spreadsheetId;

    private final ConcurrentLinkedQueue<WriteOperation> pendingWrites = new ConcurrentLinkedQueue<>();
    private final ReentrantLock flushLock = new ReentrantLock();

    public SheetsClient(Sheets sheetsService, InMemoryStore store) {
        this.sheetsService = sheetsService;
        this.store = store;
    }

    @PostConstruct
    public void init() {
        loadAllTabs();
        store.setReady(true);
    }

    public void loadAllTabs() {
        try {
            log.info("Loading all tabs from Google Sheets...");
            long start = System.currentTimeMillis();

            List<String> ranges = Arrays.stream(TAB_NAMES)
                    .map(name -> name + "!A:Z")
                    .toList();

            BatchGetValuesResponse response = sheetsService.spreadsheets().values()
                    .batchGet(spreadsheetId)
                    .setRanges(ranges)
                    .setValueRenderOption("UNFORMATTED_VALUE")
                    .execute();

            List<ValueRange> valueRanges = response.getValueRanges();
            if (valueRanges == null || valueRanges.size() < TAB_NAMES.length) {
                log.warn("Not all tabs returned from Sheets. Got: {}", valueRanges != null ? valueRanges.size() : 0);
            }

            for (int i = 0; i < TAB_NAMES.length && i < (valueRanges != null ? valueRanges.size() : 0); i++) {
                List<List<Object>> dataRows = valueRanges.get(i).getValues();
                if (dataRows == null) dataRows = List.of();

                switch (TAB_NAMES[i]) {
                    case "Customers" -> store.loadCustomers(dataRows);
                    case "Users" -> store.loadUsers(dataRows);
                    case "Orders" -> store.loadOrders(dataRows);
                    case "Order_Items" -> store.loadOrderItems(dataRows);
                    case "Drafts" -> store.loadDrafts(dataRows);
                    case "My_Customers" -> store.loadMyCustomers(dataRows);
                    case "Sync_State" -> store.loadSyncStates(dataRows);
                    case "Customer_Boards" -> store.loadCustomerBoards(dataRows);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("All tabs loaded in {}ms", elapsed);
        } catch (Exception e) {
            log.error("Failed to load tabs from Google Sheets", e);
            throw new RuntimeException("Failed to initialize from Google Sheets", e);
        }
    }

    // --- Write operations ---

    public void appendRow(String tab, List<Object> row) {
        pendingWrites.add(new WriteOperation(WriteType.APPEND, tab, List.of(row), -1));
    }

    public void updateRow(String tab, int rowIndex, List<Object> row) {
        pendingWrites.add(new WriteOperation(WriteType.UPDATE, tab, List.of(row), rowIndex));
    }

    @Scheduled(fixedDelayString = "${app.flush-interval-seconds:5}000")
    public void flushPendingWrites() {
        if (pendingWrites.isEmpty()) return;
        if (!flushLock.tryLock()) return;

        try {
            Map<String, List<WriteOperation>> byTab = new LinkedHashMap<>();
            WriteOperation op;
            while ((op = pendingWrites.poll()) != null) {
                byTab.computeIfAbsent(op.tab, k -> new ArrayList<>()).add(op);
            }

            for (Map.Entry<String, List<WriteOperation>> entry : byTab.entrySet()) {
                String tab = entry.getKey();
                List<WriteOperation> ops = entry.getValue();

                for (WriteOperation writeOp : ops) {
                    try {
                        if (writeOp.type == WriteType.APPEND) {
                            ValueRange body = new ValueRange()
                                    .setMajorDimension("ROWS")
                                    .setRange(tab + "!A1")
                                    .setValues(normalizeRows(writeOp.rows));
                            AppendValuesResponse appendResponse = sheetsService.spreadsheets().values()
                                    .append(spreadsheetId, tab + "!A1", body)
                                    .setValueInputOption("RAW")
                                    .setInsertDataOption("INSERT_ROWS")
                                    .setIncludeValuesInResponse(false)
                                    .execute();
                            if (appendResponse != null && appendResponse.getUpdates() != null) {
                                log.info("Sheets append: tab={}, updatedRange={}, updatedRows={}, updatedColumns={}",
                                        tab,
                                        appendResponse.getUpdates().getUpdatedRange(),
                                        appendResponse.getUpdates().getUpdatedRows(),
                                        appendResponse.getUpdates().getUpdatedColumns());
                            }
                        } else if (writeOp.type == WriteType.UPDATE) {
                            String range = tab + "!A" + (writeOp.rowIndex + 1);
                            ValueRange body = new ValueRange()
                                    .setMajorDimension("ROWS")
                                    .setValues(normalizeRows(writeOp.rows));
                            sheetsService.spreadsheets().values()
                                    .update(spreadsheetId, range, body)
                                    .setValueInputOption("RAW")
                                    .execute();
                        }
                    } catch (Exception e) {
                        log.error("Failed to flush write to tab {}: {}", tab, e.getMessage());
                        // Re-queue failed write
                        pendingWrites.add(writeOp);
                    }
                }
            }
        } finally {
            flushLock.unlock();
        }
    }

    // --- Periodic refresh ---

    @Scheduled(fixedDelayString = "${app.refresh-interval-seconds:300}000", initialDelay = 300000)
    public void periodicRefresh() {
        log.info("Periodic refresh from Google Sheets...");
        try {
            loadAllTabs();
        } catch (Exception e) {
            log.error("Periodic refresh failed", e);
        }
    }

    // --- Find row index by ID (column A) ---

    public int findRowIndex(String tab, String id) {
        try {
            List<List<Object>> values = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, tab + "!A:A")
                    .setValueRenderOption("UNFORMATTED_VALUE")
                    .execute()
                    .getValues();
            if (values == null) return -1;
            for (int i = 0; i < values.size(); i++) {
                if (!values.get(i).isEmpty() && id.equals(values.get(i).get(0).toString())) {
                    return i + 1; // 1-based for Sheets
                }
            }
        } catch (Exception e) {
            log.error("Failed to find row index in tab {}: {}", tab, e.getMessage());
        }
        return -1;
    }

    // --- Health check ---

    public boolean isHealthy() {
        try {
            sheetsService.spreadsheets().get(spreadsheetId).execute();
            return true;
        } catch (Exception e) {
            log.error("Sheets health check failed: {}", e.getMessage());
            return false;
        }
    }

    // --- Types ---

    private enum WriteType { APPEND, UPDATE }

    private record WriteOperation(WriteType type, String tab, List<List<Object>> rows, int rowIndex) {}

    private List<List<Object>> normalizeRows(List<List<Object>> rows) {
        if (rows == null) return List.of();
        List<List<Object>> normalized = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            if (row == null) {
                normalized.add(List.of());
                continue;
            }
            List<Object> sanitized = new ArrayList<>(row.size());
            for (Object cell : row) {
                if (cell == null) {
                    sanitized.add("");
                } else if (cell instanceof String s) {
                    // Remove control chars that can break Sheets row parsing.
                    sanitized.add(s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""));
                } else {
                    sanitized.add(cell);
                }
            }
            normalized.add(sanitized);
        }
        return normalized;
    }
}
