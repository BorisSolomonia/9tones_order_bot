package ge.orderapp.integration.rsge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CustomerExtractor {

    private static final Logger log = LoggerFactory.getLogger(CustomerExtractor.class);

    public record ExtractedCustomer(String tin, String name) {}

    private static final String[] STATUS_KEYS = {"STATUS", "status", "Status"};
    private static final String[] BUYER_TIN_KEYS = {
            "BUYER_TIN", "buyer_tin", "BuyerTin", "buyerTin", "BUYER_UN_ID", "buyer_un_id", "BuyerUnId"
    };
    private static final String[] BUYER_NAME_KEYS = {
            "BUYER_NAME", "buyer_name", "BuyerName", "buyerName", "BUYER", "buyer", "Buyer"
    };
    private static final String[] SELLER_TIN_KEYS = {
            "SELLER_TIN", "seller_tin", "SellerTin", "sellerTin", "SELLER_UN_ID", "seller_un_id", "SellerUnId"
    };
    private static final String[] SELLER_NAME_KEYS = {
            "SELLER_NAME", "seller_name", "SellerName", "sellerName", "SELLER", "seller", "Seller"
    };

    /**
     * Extract unique customers from waybills.
     * Checks all case variants: UPPER_CASE, lower_case, CamelCase (matching Tasty ERP).
     */
    public List<ExtractedCustomer> extract(List<Map<String, Object>> waybills) {
        Map<String, ExtractedCustomer> byTin = new LinkedHashMap<>();
        int skipped = 0;
        int missingTin = 0;
        int missingName = 0;

        for (Map<String, Object> wb : waybills) {
            // Skip cancelled waybills (status -1 or -2)
            Object statusObj = getField(wb, STATUS_KEYS);
            if (statusObj != null) {
                try {
                    int status = Integer.parseInt(statusObj.toString());
                    if (status == -1 || status == -2) {
                        skipped++;
                        continue;
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Extract BUYER (customer on sale waybills)
            missingTin += addCustomer(byTin, wb, BUYER_TIN_KEYS, BUYER_NAME_KEYS);
            // Extract SELLER (customer on purchase/return waybills)
            missingTin += addCustomer(byTin, wb, SELLER_TIN_KEYS, SELLER_NAME_KEYS);

            String buyerName = getStringField(wb, BUYER_NAME_KEYS);
            String sellerName = getStringField(wb, SELLER_NAME_KEYS);
            if (buyerName == null) missingName++;
            if (sellerName == null) missingName++;
        }

        log.info("Extracted {} unique customers from {} waybills (skippedCancelled={}, missingTinCandidates={}, missingNameCandidates={})",
                byTin.size(), waybills.size(), skipped, missingTin, missingName);
        if (byTin.isEmpty() && !waybills.isEmpty()) {
            log.warn("Customer extraction produced 0 customers. Possible causes: only cancelled waybills, missing BUYER/SELLER TIN fields, or unsupported payload keys.");
        }
        return new ArrayList<>(byTin.values());
    }

    private int addCustomer(Map<String, ExtractedCustomer> byTin, Map<String, Object> wb,
                             String[] tinKeys, String[] nameKeys) {
        String tin = getStringField(wb, tinKeys);
        String name = getStringField(wb, nameKeys);

        if (tin == null || tin.isBlank()) return 1;

        // Normalize TIN: strip whitespace, hyphens, dots, underscores (matching Tasty ERP TinValidator)
        tin = tin.trim().replaceAll("[\\s\\-._]+", "");

        if (tin.isEmpty()) return 1;

        // Keep richer name (longer = more complete)
        ExtractedCustomer existing = byTin.get(tin);
        if (existing == null || (name != null && (existing.name() == null || name.length() > existing.name().length()))) {
            byTin.put(tin, new ExtractedCustomer(tin, name != null ? name.trim() : tin));
        }
        return 0;
    }

    private Object getField(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private String getStringField(Map<String, Object> map, String... keys) {
        Object value = getField(map, keys);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }
}
