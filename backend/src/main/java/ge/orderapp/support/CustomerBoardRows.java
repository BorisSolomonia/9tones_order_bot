package ge.orderapp.support;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CustomerBoardRows {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern ISO_TIMESTAMP_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}([T ].*)?$");

    private CustomerBoardRows() {
    }

    public static Header detectHeader(List<List<Object>> rows) {
        if (rows == null) return Header.none();
        for (List<Object> row : rows) {
            Header header = detectHeaderInRow(row);
            if (header.present()) {
                return header;
            }
            if (row != null && row.stream().anyMatch(cell -> !cell(cell).isBlank())) {
                return Header.none();
            }
        }
        return Header.none();
    }

    public static ParsedRow parse(List<Object> row, Header header) {
        if (row == null || row.isEmpty()) {
            return ParsedRow.empty();
        }
        if (detectHeaderInRow(row).present()) {
            return ParsedRow.forHeader();
        }

        String customerId;
        String rawBoard;
        if (header != null && header.present() && header.customerIdIndex() >= 0 && header.boardIndex() >= 0) {
            customerId = cell(row, header.customerIdIndex());
            rawBoard = cell(row, header.boardIndex());
        } else {
            int uuidIndex = findUuidIndex(row);
            if (uuidIndex >= 0) {
                customerId = cell(row, uuidIndex);
                rawBoard = cell(row, inferBoardIndex(row, uuidIndex));
            } else {
                customerId = cell(row, 0);
                rawBoard = cell(row, 1);
            }
        }

        String board = normalizeBoard(rawBoard);
        boolean hasCustomerId = !customerId.isBlank();
        boolean invalidBoard = hasCustomerId && !rawBoard.isBlank() && board == null;
        return new ParsedRow(customerId, board, false, hasCustomerId, invalidBoard);
    }

    public static String normalizeBoard(String board) {
        if (board == null) return null;
        String trimmed = board.trim();
        if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("=")) {
            return null;
        }
        return trimmed;
    }

    public static boolean isUuidLike(String value) {
        return value != null && UUID_PATTERN.matcher(value.trim()).matches();
    }

    private static Header detectHeaderInRow(List<Object> row) {
        int customerIdIndex = -1;
        int boardIndex = -1;
        if (row == null) {
            return Header.none();
        }
        for (int i = 0; i < row.size(); i++) {
            String normalized = normalizeHeader(cell(row, i));
            if (isCustomerIdHeader(normalized)) {
                customerIdIndex = i;
            } else if (isBoardHeader(normalized)) {
                boardIndex = i;
            }
        }
        if (customerIdIndex >= 0 && boardIndex >= 0) {
            return new Header(customerIdIndex, boardIndex, true);
        }
        return Header.none();
    }

    private static int findUuidIndex(List<Object> row) {
        for (int i = 0; i < row.size(); i++) {
            if (isUuidLike(cell(row, i))) {
                return i;
            }
        }
        return -1;
    }

    private static int inferBoardIndex(List<Object> row, int customerIdIndex) {
        if (customerIdIndex == 0) {
            return 1;
        }
        if (customerIdIndex == 2 && isBoardCandidate(cell(row, 1))) {
            return 1;
        }
        if (customerIdIndex == 1 && isBoardCandidate(cell(row, 2))) {
            return 2;
        }

        for (int i = customerIdIndex + 1; i < row.size(); i++) {
            if (isBoardCandidate(cell(row, i))) {
                return i;
            }
        }
        for (int i = customerIdIndex - 1; i >= 0; i--) {
            if (isBoardCandidate(cell(row, i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBoardCandidate(String value) {
        String board = normalizeBoard(value);
        if (board == null) return false;
        if (isUuidLike(board)) return false;
        if (ISO_TIMESTAMP_PATTERN.matcher(board).matches()) return false;
        String lower = board.toLowerCase(Locale.ROOT);
        return !"true".equals(lower) && !"false".equals(lower) && !"customerid".equals(normalizeHeader(board));
    }

    private static boolean isCustomerIdHeader(String value) {
        return "customerid".equals(value) || "customeruuid".equals(value);
    }

    private static boolean isBoardHeader(String value) {
        return "board".equals(value) || "boards".equals(value);
    }

    private static String normalizeHeader(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String cell(List<Object> row, int index) {
        if (row == null || index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    private static String cell(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record Header(int customerIdIndex, int boardIndex, boolean present) {
        public static Header none() {
            return new Header(-1, -1, false);
        }
    }

    public record ParsedRow(String customerId, String board, boolean headerRow, boolean hasCustomerId,
                            boolean invalidBoard) {
        public static ParsedRow empty() {
            return new ParsedRow("", null, false, false, false);
        }

        public static ParsedRow forHeader() {
            return new ParsedRow("", null, true, false, false);
        }
    }
}
