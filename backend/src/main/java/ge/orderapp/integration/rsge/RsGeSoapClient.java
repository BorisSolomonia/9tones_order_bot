package ge.orderapp.integration.rsge;

import ge.orderapp.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public interface RsGeSoapClient {
    /** Fetch sale waybills (we are the seller). */
    List<Map<String, Object>> getWaybills(LocalDate startDate, LocalDate endDate);

    /** Fetch purchase/buyer waybills (we are the buyer). */
    List<Map<String, Object>> getBuyerWaybills(LocalDate startDate, LocalDate endDate);
}

@Component
@ConditionalOnProperty(name = "rsge.enabled", havingValue = "true")
class RealRsGeSoapClient implements RsGeSoapClient {

    private static final Logger log = LoggerFactory.getLogger(RealRsGeSoapClient.class);

    // Known container element names in RS.GE SOAP responses
    private static final Set<String> CONTAINER_KEYS = Set.of(
            "WAYBILL_LIST", "WAYBILL", "BUYER_WAYBILL", "PURCHASE_WAYBILL",
            "waybill_list", "waybill", "buyer_waybill", "purchase_waybill",
            "WaybillList", "Waybill", "BuyerWaybill", "PurchaseWaybill",
            "RESULT", "Result", "result"
    );

    // Amount fields used for richness scoring
    private static final Set<String> AMOUNT_FIELDS = Set.of(
            "FULL_AMOUNT", "TOTAL_AMOUNT", "GROSS_AMOUNT", "NET_AMOUNT",
            "AMOUNT_LARI", "AMOUNT", "SUM", "SUMA", "VALUE", "VALUE_LARI",
            "full_amount", "total_amount", "gross_amount", "net_amount",
            "amount_lari", "amount", "sum", "suma", "value", "value_lari"
    );

    @Value("${rsge.endpoint}")
    private String endpoint;

    @Value("${rsge.su}")
    private String username;

    @Value("${rsge.sp}")
    private String password;

    @Value("${rsge.timeout:120}")
    private int timeoutSeconds;

    @Value("${rsge.connect-timeout-seconds:30}")
    private int connectTimeoutSeconds;

    @Value("${rsge.chunk-days:3}")
    private int chunkDays;

    @Value("${rsge.chunk-parallelism:3}")
    private int chunkParallelism;

    @Value("${rsge.soap-namespace:http://tempuri.org/}")
    private String soapNamespace;

    @Value("${rsge.date-format:yyyy-MM-dd'T'HH:mm:ss}")
    private String dateFormatPattern;

    @Value("${rsge.debug:false}")
    private boolean debugEnabled;

    @Value("${rsge.debug-sample-count:3}")
    private int debugSampleCount;

    @Value("${rsge.debug-response-snippet-length:0}")
    private int debugResponseSnippetLength;

    private HttpClient httpClient;
    private ExecutorService chunkExecutor;

    @PostConstruct
    public void initClient() {
        int effectiveParallelism = Math.max(1, chunkParallelism);
        this.chunkExecutor = Executors.newFixedThreadPool(effectiveParallelism);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        log.info("RS.GE client initialized: endpoint={}, timeoutSeconds={}, connectTimeoutSeconds={}, chunkDays={}, chunkParallelism={}, httpVersion={}, namespace={}",
                endpoint, timeoutSeconds, connectTimeoutSeconds, chunkDays, effectiveParallelism, "HTTP_1_1", soapNamespace);
    }

    @PreDestroy
    public void shutdown() {
        if (chunkExecutor == null) return;
        chunkExecutor.shutdown();
        try {
            if (!chunkExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                chunkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            chunkExecutor.shutdownNow();
        }
    }

    @Override
    public List<Map<String, Object>> getWaybills(LocalDate startDate, LocalDate endDate) {
        validateConfiguration();
        log.info("Fetching sale waybills from RS.ge: {} to {}", startDate, endDate);

        String startStr = startDate.atStartOfDay().format(dateFormatter());
        String endStr = endDate.plusDays(1).atStartOfDay().format(dateFormatter());

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("su", username);
        params.put("sp", password);
        addSellerUnId(params);
        params.put("create_date_s", startStr);
        params.put("create_date_e", endStr);

        try {
            return callSoapWithRetry("get_waybills", params);
        } catch (Exception e) {
            log.error("Failed to fetch sale waybills: {}", e.getMessage());
            throw new ExternalServiceException("RS.ge", e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getBuyerWaybills(LocalDate startDate, LocalDate endDate) {
        validateConfiguration();
        log.info("Fetching buyer waybills from RS.ge: {} to {}", startDate, endDate);

        String startStr = startDate.atStartOfDay().format(dateFormatter());
        String endStr = endDate.plusDays(1).atStartOfDay().format(dateFormatter());

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("su", username);
        params.put("sp", password);
        addSellerUnId(params);
        params.put("create_date_s", startStr);
        params.put("create_date_e", endStr);

        try {
            return callSoapWithRetry("get_buyer_waybills", params);
        } catch (Exception e) {
            log.error("Failed to fetch buyer waybills: {}", e.getMessage());
            throw new ExternalServiceException("RS.ge", e.getMessage(), e);
        }
    }

    /** Extract seller_un_id from username if it contains a colon (format: username:seller_id). */
    private void addSellerUnId(LinkedHashMap<String, String> params) {
        if (username != null && username.contains(":")) {
            String sellerId = username.substring(username.indexOf(':') + 1);
            if (!sellerId.isBlank()) {
                params.put("seller_un_id", sellerId);
            }
        }
    }

    private List<Map<String, Object>> callSoapWithRetry(String operation, LinkedHashMap<String, String> params) throws Exception {
        log.info("RS.ge SOAP call operation={} create_date_s={} create_date_e={} su={} seller_un_id={}",
                operation, params.get("create_date_s"), params.get("create_date_e"),
                maskUsername(username), params.get("seller_un_id"));
        String response = sendSoapRequest(operation, params);
        Map<String, Object> result = parseSoapResponse(response, operation);
        int statusCode = getStatusCode(result);
        log.info("RS.ge SOAP operation={} status={}", operation, statusCode);

        // -101: missing seller credentials â€” retry with full username as seller_un_id
        if (statusCode == -101) {
            String existingSellerId = params.get("seller_un_id");
            if (existingSellerId == null || existingSellerId.isBlank()) {
                log.warn("RS.ge returned -101, retrying with full username as seller_un_id");
                params.put("seller_un_id", username);
                response = sendSoapRequest(operation, params);
                result = parseSoapResponse(response, operation);
                statusCode = getStatusCode(result);
                log.info("RS.ge SOAP retry operation={} status={}", operation, statusCode);
                if (statusCode == -101) {
                    throw new ExternalServiceException("RS.ge", "Missing seller credentials (after retry)");
                }
            } else {
                throw new ExternalServiceException("RS.ge", "Missing seller credentials");
            }
        }

        if (statusCode == -1064) {
            log.info("Date range too large, splitting into chunks");
            return fetchInChunks(operation, params);
        }
        if (statusCode != 0 && statusCode != 1) {
            log.warn("RS.ge returned non-success status: operation={}, status={}, keys={}",
                    operation, statusCode, result.keySet());
        }
        List<Map<String, Object>> extracted = extractWaybillsDeep(result);
        log.info("RS.ge SOAP operation={} extractedWaybills={}", operation, extracted.size());
        if (debugEnabled) {
            if (extracted.isEmpty()) {
                log.debug("RS.ge SOAP operation={} resultKeys={}", operation, result.keySet());
            }
            logDebugSamples(operation, extracted);
        }
        return extracted;
    }

    private List<Map<String, Object>> fetchInChunks(String operation, LinkedHashMap<String, String> originalParams) {
        LocalDate startInclusive = LocalDate.parse(originalParams.get("create_date_s").substring(0, 10));
        LocalDate endExclusive = LocalDate.parse(originalParams.get("create_date_e").substring(0, 10));
        if (!endExclusive.isAfter(startInclusive)) return List.of();
        LocalDate endInclusive = endExclusive.minusDays(1);
        long effectiveChunkDays = Math.max(1, chunkDays);

        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
        LocalDate chunkStart = startInclusive;

        while (!chunkStart.isAfter(endInclusive)) {
            LocalDate chunkEnd = chunkStart.plusDays(effectiveChunkDays - 1L);
            if (chunkEnd.isAfter(endInclusive)) chunkEnd = endInclusive;

            final LocalDate s = chunkStart;
            final LocalDate e = chunkEnd;

            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    LinkedHashMap<String, String> chunkParams = new LinkedHashMap<>(originalParams);
                    chunkParams.put("create_date_s", s.atStartOfDay().format(dateFormatter()));
                    chunkParams.put("create_date_e", e.plusDays(1).atStartOfDay().format(dateFormatter()));
                    log.debug("Fetching chunk: {} to {}", s, e);
                    String resp = sendSoapRequest(operation, chunkParams);
                    Map<String, Object> res = parseSoapResponse(resp, operation);
                    int statusCode = getStatusCode(res);
                    if (statusCode != 0 && statusCode != 1) {
                        log.warn("RS.ge SOAP operation={} chunk {}..{} status={}", operation, s, e, statusCode);
                    }
                    List<Map<String, Object>> extracted = extractWaybillsDeep(res);
                    if (debugEnabled) {
                        logDebugSamples(operation, extracted);
                    }
                    return extracted;
                } catch (Exception ex) {
                    log.error("Error fetching chunk {} to {}: {}", s, e, ex.getMessage());
                    throw new RuntimeException(ex);
                }
            }, chunkExecutor));
            chunkStart = chunkEnd.plusDays(1);
        }
        log.info("RS.ge chunked fetch prepared: operation={}, chunks={}, start={}, end={}, chunkDays={}",
                operation, futures.size(), startInclusive, endInclusive, effectiveChunkDays);

        try {
            return futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (isTooManyConcurrentStreams(cause)) {
                log.warn("RS.ge chunk fetch hit stream limit; retrying sequentially: operation={}, chunks={}", operation, futures.size());
                return fetchInChunksSequential(operation, originalParams, startInclusive, endInclusive, effectiveChunkDays);
            }
            throw new ExternalServiceException("RS.ge", "Chunk fetch failed: " + cause.getMessage(), cause);
        }
    }

    private List<Map<String, Object>> fetchInChunksSequential(String operation,
                                                              LinkedHashMap<String, String> originalParams,
                                                              LocalDate startInclusive,
                                                              LocalDate endInclusive,
                                                              long effectiveChunkDays) {
        List<Map<String, Object>> merged = new ArrayList<>();
        LocalDate chunkStart = startInclusive;
        while (!chunkStart.isAfter(endInclusive)) {
            LocalDate chunkEnd = chunkStart.plusDays(effectiveChunkDays - 1L);
            if (chunkEnd.isAfter(endInclusive)) chunkEnd = endInclusive;

            try {
                LinkedHashMap<String, String> chunkParams = new LinkedHashMap<>(originalParams);
                chunkParams.put("create_date_s", chunkStart.atStartOfDay().format(dateFormatter()));
                chunkParams.put("create_date_e", chunkEnd.plusDays(1).atStartOfDay().format(dateFormatter()));
                log.debug("Sequential chunk fetch: {} to {}", chunkStart, chunkEnd);
                String resp = sendSoapRequest(operation, chunkParams);
                Map<String, Object> res = parseSoapResponse(resp, operation);
                merged.addAll(extractWaybillsDeep(res));
            } catch (Exception ex) {
                throw new ExternalServiceException("RS.ge",
                        "Sequential chunk fetch failed for " + chunkStart + ".." + chunkEnd + ": " + ex.getMessage(), ex);
            }

            chunkStart = chunkEnd.plusDays(1);
        }
        return merged;
    }

    private boolean isTooManyConcurrentStreams(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains("too many concurrent streams")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private String sendSoapRequest(String operation, LinkedHashMap<String, String> params) throws Exception {
        String soapBody = buildSoapEnvelope(operation, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + soapNamespace + operation + "\"")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(soapBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("RS.ge HTTP response: operation={}, status={}, bodySize={}",
                operation, response.statusCode(), response.body() != null ? response.body().length() : 0);
        if (debugEnabled && debugResponseSnippetLength > 0 && response.body() != null) {
            log.debug("RS.ge HTTP body snippet: operation={}, status={}, snippet={}",
                    operation, response.statusCode(), snippet(response.body(), debugResponseSnippetLength));
        }
        if (response.statusCode() != 200 && response.statusCode() != 500) {
            String snippet = response.body() == null ? "" : response.body().substring(0, Math.min(500, response.body().length()));
            throw new ExternalServiceException("RS.ge", "HTTP " + response.statusCode() + " body=" + snippet);
        }
        return response.body();
    }

    private String buildSoapEnvelope(String operation, LinkedHashMap<String, String> params) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            body.append(String.format("<%s>%s</%s>", entry.getKey(), xmlEscape(entry.getValue()), entry.getKey()));
        }
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                               xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <%s xmlns="%s">
                      %s
                    </%s>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(operation, soapNamespace, body.toString(), operation);
    }

    private Map<String, Object> parseSoapResponse(String xml, String operation) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        NodeList faults = doc.getElementsByTagName("faultstring");
        if (faults.getLength() > 0) {
            throw new ExternalServiceException("RS.ge", faults.item(0).getTextContent());
        }
        Node resultNode = findResultNode(doc, operation);
        if (resultNode == null) {
            log.warn("RS.ge SOAP parse: no {}Result element found", operation);
            if (debugEnabled && debugResponseSnippetLength > 0) {
                log.debug("RS.ge SOAP operation={} missing Result node; xml snippet={}",
                        operation, snippet(xml, debugResponseSnippetLength));
            }
            return new HashMap<>();
        }
        return nodeToMap(resultNode);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> map = new HashMap<>();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            String name = child.getNodeName();
            NodeList grandchildren = child.getChildNodes();
            if (grandchildren.getLength() == 1 && grandchildren.item(0).getNodeType() == Node.TEXT_NODE) {
                map.put(name, child.getTextContent());
            } else {
                Object existing = map.get(name);
                Map<String, Object> childMap = nodeToMap(child);
                if (existing instanceof List) {
                    ((List<Map<String, Object>>) existing).add(childMap);
                } else if (existing != null) {
                    List<Object> list = new ArrayList<>();
                    list.add(existing);
                    list.add(childMap);
                    map.put(name, list);
                } else {
                    map.put(name, childMap);
                }
            }
        }
        return map;
    }

    private int getStatusCode(Map<String, Object> result) {
        Object status = result.get("STATUS");
        if (status == null) {
            Object inner = result.get("RESULT");
            if (inner instanceof Map) status = ((Map<?, ?>) inner).get("STATUS");
        }
        if (status == null) return 0;
        try { return Integer.parseInt(status.toString()); } catch (NumberFormatException e) { return 0; }
    }

    /**
     * BFS traversal of the parsed SOAP response to find all waybill-like maps.
     * RS.GE responses nest waybills inside containers like WAYBILL_LIST, WAYBILL, BUYER_WAYBILL, etc.
     * The same waybill ID can appear at multiple depths with varying completeness.
     * We keep the richest representation via completeness scoring (matching Tasty ERP logic).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractWaybillsDeep(Map<String, Object> root) {
        Object unwrapped = root.get("RESULT");
        if (!(unwrapped instanceof Map)) unwrapped = root;

        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(unwrapped);

        while (!queue.isEmpty()) {
            Object cur = queue.poll();
            if (cur == null) continue;
            if (cur instanceof List<?> list) { list.forEach(queue::add); continue; }
            if (!(cur instanceof Map<?, ?>)) continue;
            if (!visited.add(cur)) continue;
            Map<String, Object> map = (Map<String, Object>) cur;

            if (isWaybillCandidate(map)) {
                String id = firstNonBlank(map, "ID", "id", "waybill_id", "waybillId", "WaybillId", "WAYBILL_ID");
                if (id == null) id = "unknown_" + byId.size();
                // Keep the richer representation (more complete data)
                Map<String, Object> existing = byId.get(id);
                if (existing == null) {
                    byId.put(id, map);
                } else {
                    byId.put(id, chooseRicherWaybill(existing, map));
                }
            }

            // Prioritize known container keys, then traverse all values
            for (String containerKey : CONTAINER_KEYS) {
                Object containerVal = map.get(containerKey);
                if (containerVal instanceof Map || containerVal instanceof List) {
                    queue.add(containerVal);
                }
            }
            for (Object value : map.values()) {
                if (value instanceof Map || value instanceof List) queue.add(value);
            }
        }

        log.debug("BFS extracted {} unique waybills", byId.size());
        return new ArrayList<>(byId.values());
    }

    private boolean isWaybillCandidate(Map<String, Object> map) {
        String id = firstNonBlank(map, "ID", "id", "waybill_id", "waybillId", "WaybillId", "WAYBILL_ID");
        if (id == null) return false;
        return map.containsKey("BUYER_TIN") || map.containsKey("buyer_tin") || map.containsKey("BuyerTin")
                || map.containsKey("SELLER_TIN") || map.containsKey("seller_tin") || map.containsKey("SellerTin")
                || map.containsKey("STATUS") || map.containsKey("status") || map.containsKey("Status")
                || map.containsKey("CREATE_DATE") || map.containsKey("create_date") || map.containsKey("CreateDate")
                || map.containsKey("FULL_AMOUNT") || map.containsKey("full_amount") || map.containsKey("FullAmount")
                || map.containsKey("TOTAL_AMOUNT") || map.containsKey("total_amount");
    }

    /**
     * Pick the waybill map with higher completeness score.
     * Scoring matches Tasty ERP: amount fields=+20, date=+8, TINs=+3 each, status=+1, field count bonus.
     */
    private Map<String, Object> chooseRicherWaybill(Map<String, Object> a, Map<String, Object> b) {
        int scoreA = waybillCompletenessScore(a);
        int scoreB = waybillCompletenessScore(b);
        if (scoreA != scoreB) return scoreA >= scoreB ? a : b;
        // Tie-breaker: more fields wins
        return a.size() >= b.size() ? a : b;
    }

    private int waybillCompletenessScore(Map<String, Object> map) {
        int score = 0;
        // Amount fields are the most valuable indicator of completeness
        for (String key : AMOUNT_FIELDS) {
            if (map.containsKey(key)) { score += 20; break; }
        }
        // Date field
        if (map.containsKey("CREATE_DATE") || map.containsKey("create_date") || map.containsKey("CreateDate")
                || map.containsKey("WAYBILL_DATE") || map.containsKey("waybill_date") || map.containsKey("DATE")) {
            score += 8;
        }
        // TIN fields
        if (map.containsKey("BUYER_TIN") || map.containsKey("buyer_tin") || map.containsKey("BuyerTin")) score += 3;
        if (map.containsKey("SELLER_TIN") || map.containsKey("seller_tin") || map.containsKey("SellerTin")) score += 3;
        // Status
        if (map.containsKey("STATUS") || map.containsKey("status") || map.containsKey("Status")) score += 1;
        // Field count bonus (up to 10 points)
        score += Math.min(map.size(), 50) / 5;
        return score;
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !v.toString().isBlank()) return v.toString().trim();
        }
        return null;
    }

    private Node findResultNode(Document doc, String operation) {
        String target = operation + "Result";
        NodeList direct = doc.getElementsByTagName(target);
        if (direct.getLength() > 0) {
            return direct.item(0);
        }
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node == null) continue;
            String nodeName = node.getNodeName();
            if (target.equals(nodeName) || nodeName.endsWith(":" + target)) {
                return node;
            }
        }
        return null;
    }

    private void logDebugSamples(String operation, List<Map<String, Object>> waybills) {
        int limit = Math.min(Math.max(debugSampleCount, 0), waybills.size());
        for (int i = 0; i < limit; i++) {
            Map<String, Object> wb = waybills.get(i);
            String id = firstNonBlank(wb, "ID", "id", "waybill_id", "waybillId", "WaybillId", "WAYBILL_ID");
            Object date = wb.getOrDefault("CREATE_DATE", wb.getOrDefault("create_date", wb.getOrDefault("WAYBILL_DATE", null)));
            Object status = wb.getOrDefault("STATUS", wb.getOrDefault("status", null));
            Object amount = wb.getOrDefault("FULL_AMOUNT", wb.getOrDefault("full_amount", wb.getOrDefault("TOTAL_AMOUNT", null)));
            Object buyerTin = wb.getOrDefault("BUYER_TIN", wb.getOrDefault("buyer_tin", wb.getOrDefault("BuyerTin", null)));
            Object sellerTin = wb.getOrDefault("SELLER_TIN", wb.getOrDefault("seller_tin", wb.getOrDefault("SellerTin", null)));
            log.debug("RS.ge sample op={} idx={} id={} date={} status={} amount={} buyerTin={} sellerTin={}",
                    operation, i, id, date, status, amount, buyerTin, sellerTin);
        }
    }

    private String snippet(String input, int maxLen) {
        if (input == null) return "";
        String s = input.replace("\r", " ").replace("\n", " ").trim();
        if (maxLen <= 0 || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    private String xmlEscape(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> { if (c >= 0x20 || c == 0x09 || c == 0x0A || c == 0x0D) sb.append(c); }
            }
        }
        return sb.toString();
    }

    private DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofPattern(dateFormatPattern);
    }

    private void validateConfiguration() {
        log.info("RS.GE config check: endpointSet={}, suSet={}, spSet={}, suMasked={}",
                endpoint != null && !endpoint.isBlank(),
                username != null && !username.isBlank(),
                password != null && !password.isBlank(),
                maskUsername(username));
        if (endpoint == null || endpoint.isBlank()) {
            throw new ExternalServiceException("RS.ge", "RSGE_ENDPOINT is not configured");
        }
        if (username == null || username.isBlank()) {
            throw new ExternalServiceException("RS.ge", "RSGE_SU is not configured");
        }
        if (password == null || password.isBlank()) {
            throw new ExternalServiceException("RS.ge", "RSGE_SP is not configured");
        }
    }

    private String maskUsername(String input) {
        if (input == null || input.isBlank()) return "<empty>";
        if (input.length() <= 4) return "****";
        return input.substring(0, 2) + "****" + input.substring(input.length() - 2);
    }
}

@Component
@ConditionalOnProperty(name = "rsge.enabled", havingValue = "false", matchIfMissing = true)
class MockRsGeSoapClient implements RsGeSoapClient {

    private static final Logger log = LoggerFactory.getLogger(MockRsGeSoapClient.class);

    @Override
    public List<Map<String, Object>> getWaybills(LocalDate startDate, LocalDate endDate) {
        log.info("[Mock] Returning sample sale waybills for {} to {}", startDate, endDate);
        return List.of(
                Map.of("ID", "wb1", "BUYER_TIN", "999999999", "BUYER_NAME", "Sample Buyer One", "STATUS", "1", "CREATE_DATE", startDate.toString()),
                Map.of("ID", "wb2", "BUYER_TIN", "888888888", "BUYER_NAME", "Sample Buyer Two", "STATUS", "1", "CREATE_DATE", startDate.toString())
        );
    }

    @Override
    public List<Map<String, Object>> getBuyerWaybills(LocalDate startDate, LocalDate endDate) {
        log.info("[Mock] Returning sample buyer waybills for {} to {}", startDate, endDate);
        return List.of(
                Map.of("ID", "wb3", "SELLER_TIN", "777777777", "SELLER_NAME", "Sample Supplier", "STATUS", "1", "CREATE_DATE", startDate.toString())
        );
    }
}

