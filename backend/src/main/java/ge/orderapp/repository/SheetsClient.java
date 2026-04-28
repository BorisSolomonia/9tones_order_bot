package ge.orderapp.repository;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.support.CustomerBoardRows;
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

            int deletedRows = cleanupDuplicateCustomerBoardRows();
            if (deletedRows > 0) {
                log.warn("Removed {} Customer_Boards rows from Google Sheets; reloading board state", deletedRows);
                reloadCustomerBoards();
            }
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
            doFlush();
        } finally {
            flushLock.unlock();
        }
    }

    // --- Periodic refresh ---

    @Scheduled(fixedDelayString = "${app.refresh-interval-seconds:300}000", initialDelay = 300000)
    public void periodicRefresh() {
        log.info("Periodic refresh from Google Sheets...");
        try {
            // Flush all pending writes FIRST so the reload sees up-to-date data.
            // Without this, customers added by a concurrent sync but not yet flushed
            // would be removed from memory by the retainAll in loadCustomers(), causing
            // the next sync to re-append them as "new" — producing duplicates.
            flushLock.lock();
            try {
                doFlush();
            } finally {
                flushLock.unlock();
            }
            loadAllTabs();
        } catch (Exception e) {
            log.error("Periodic refresh failed", e);
        }
    }

    private void doFlush() {
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

    public int removeCustomerBoardRows(String customerId, String board) {
        try {
            List<Integer> rowIndexes = findCustomerBoardRowIndexes(customerId, board);
            if (rowIndexes.isEmpty()) {
                return 0;
            }
            deleteRows("Customer_Boards", rowIndexes);
            return rowIndexes.size();
        } catch (Exception e) {
            log.error("Failed to remove Customer_Boards rows for customerId={}, board={}: {}", customerId, board, e.getMessage());
            throw new RuntimeException("Failed to remove customer board rows from Google Sheets", e);
        }
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

    private void reloadCustomerBoards() throws Exception {
        List<List<Object>> values = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Customer_Boards!A:Z")
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValues();
        store.loadCustomerBoards(values != null ? values : List.of());
    }

    private int cleanupDuplicateCustomerBoardRows() throws Exception {
        List<List<Object>> rows = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Customer_Boards!A:Z")
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValues();
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        Map<String, Integer> newestRowByKey = new HashMap<>();
        Map<String, String> newestTimestampByKey = new HashMap<>();
        List<Integer> rowsToDelete = new ArrayList<>();
        int invalidRows = 0;
        int duplicateRows = 0;
        CustomerBoardRows.Header header = CustomerBoardRows.detectHeader(rows);

        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            CustomerBoardRows.ParsedRow parsed = CustomerBoardRows.parse(row, header);
            if (parsed.headerRow() || parsed.customerId().isBlank()) {
                continue;
            }
            if (parsed.board() == null) {
                if (parsed.invalidBoard()) {
                    rowsToDelete.add(i + 1);
                    invalidRows++;
                }
                continue;
            }

            String key = parsed.customerId() + "\u0000" + parsed.board();
            String timestamp = cell(row, 2);
            Integer existingRow = newestRowByKey.get(key);
            if (existingRow == null) {
                newestRowByKey.put(key, i + 1);
                newestTimestampByKey.put(key, timestamp);
                continue;
            }

            String existingTimestamp = newestTimestampByKey.get(key);
            if (compareIsoTimestamps(timestamp, existingTimestamp) >= 0) {
                rowsToDelete.add(existingRow);
                duplicateRows++;
                newestRowByKey.put(key, i + 1);
                newestTimestampByKey.put(key, timestamp);
            } else {
                rowsToDelete.add(i + 1);
                duplicateRows++;
            }
        }

        if (rowsToDelete.isEmpty()) {
            return 0;
        }

        log.warn("Customer_Boards cleanup deleting rows: total={}, duplicates={}, invalid={}",
                rowsToDelete.size(), duplicateRows, invalidRows);
        deleteRows("Customer_Boards", rowsToDelete);
        return rowsToDelete.size();
    }

    private List<Integer> findCustomerBoardRowIndexes(String customerId, String board) throws Exception {
        List<List<Object>> rows = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "Customer_Boards!A:Z")
                .setValueRenderOption("UNFORMATTED_VALUE")
                .execute()
                .getValues();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<Integer> matches = new ArrayList<>();
        String normalizedBoard = CustomerBoardRows.normalizeBoard(board);
        CustomerBoardRows.Header header = CustomerBoardRows.detectHeader(rows);
        for (int i = 0; i < rows.size(); i++) {
            CustomerBoardRows.ParsedRow parsed = CustomerBoardRows.parse(rows.get(i), header);
            if (customerId.equals(parsed.customerId()) && Objects.equals(normalizedBoard, parsed.board())) {
                matches.add(i + 1);
            }
        }
        return matches;
    }

    private void deleteRows(String tab, List<Integer> oneBasedRowIndexes) throws Exception {
        Integer sheetId = getSheetId(tab);
        List<Request> requests = oneBasedRowIndexes.stream()
                .sorted(Comparator.reverseOrder())
                .map(rowIndex -> new Request().setDeleteDimension(new DeleteDimensionRequest()
                        .setRange(new DimensionRange()
                                .setSheetId(sheetId)
                                .setDimension("ROWS")
                                .setStartIndex(rowIndex - 1)
                                .setEndIndex(rowIndex))))
                .toList();

        BatchUpdateSpreadsheetRequest request = new BatchUpdateSpreadsheetRequest().setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, request).execute();
    }

    private Integer getSheetId(String tab) throws Exception {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        if (spreadsheet.getSheets() == null) {
            throw new IllegalStateException("No sheets found in spreadsheet");
        }
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties() != null && tab.equals(sheet.getProperties().getTitle())) {
                return sheet.getProperties().getSheetId();
            }
        }
        throw new IllegalArgumentException("Sheet not found: " + tab);
    }

    private String cell(List<Object> row, int index) {
        if (row == null || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    private int compareIsoTimestamps(String a, String b) {
        if (a == null || a.isBlank()) {
            return (b == null || b.isBlank()) ? 0 : -1;
        }
        if (b == null || b.isBlank()) {
            return 1;
        }
        return a.compareTo(b);
    }
}
