package ge.orderapp.integration.rsge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "google.sheets.enabled=false",
        "telegram.enabled=false",
        "rsge.enabled=true"
})
@Disabled("Manual environment smoke test. Run explicitly when RS.GE credentials are available.")
class RsGeTwoMonthSyncSmokeTest {

    @Autowired
    private RsGeSoapClient rsGeSoapClient;

    @Autowired
    private CustomerExtractor customerExtractor;

    @Test
    void lastTwoMonthsShouldReturnMoreThan260UniqueCustomers() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(2);

        List<Map<String, Object>> saleWaybills = rsGeSoapClient.getWaybills(startDate, endDate);
        List<Map<String, Object>> buyerWaybills = rsGeSoapClient.getBuyerWaybills(startDate, endDate);

        List<Map<String, Object>> merged = new ArrayList<>(saleWaybills.size() + buyerWaybills.size());
        merged.addAll(saleWaybills);
        merged.addAll(buyerWaybills);

        List<CustomerExtractor.ExtractedCustomer> extracted = customerExtractor.extract(merged);

        assertTrue(
                extracted.size() > 260,
                "Expected > 260 unique customers for last two months, got " + extracted.size()
        );
    }
}
