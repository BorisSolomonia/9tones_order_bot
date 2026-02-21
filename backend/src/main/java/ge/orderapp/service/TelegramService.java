package ge.orderapp.service;

import ge.orderapp.dto.response.OrderDto;
import ge.orderapp.dto.response.OrderItemDto;
import ge.orderapp.integration.telegram.TelegramBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final TelegramBotClient telegramClient;

    @Value("${telegram.message.date-format:dd.MM.yyyy}")
    private String messageDateFormat;

    @Value("${telegram.message.order-title:Order}")
    private String orderTitle;

    @Value("${telegram.message.manager-label:Manager}")
    private String managerLabel;

    @Value("${telegram.message.date-label:Date}")
    private String dateLabel;

    @Value("${telegram.message.customers-label:Customers}")
    private String customersLabel;

    @Value("${telegram.message.total-label:Total}")
    private String totalLabel;

    @Value("${telegram.message.total-suffix:customers}")
    private String totalSuffix;

    public TelegramService(TelegramBotClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public boolean sendOrderMessage(OrderDto order, List<OrderItemDto> items) {
        String message = formatOrderMessage(order, items);
        boolean sent = telegramClient.sendMessage(message);
        if (sent) {
            log.info("Telegram message sent for order: {}", order.orderId());
        } else {
            log.warn("Telegram message NOT sent for order: {}", order.orderId());
        }
        return sent;
    }

    private String formatOrderMessage(OrderDto order, List<OrderItemDto> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCB <b>").append(escapeHtml(orderTitle)).append("</b>\n");
        sb.append("\uD83D\uDC64 ").append(escapeHtml(managerLabel)).append(": ")
                .append(escapeHtml(order.managerName())).append("\n");

        String displayDate = LocalDate.now().format(DateTimeFormatter.ofPattern(messageDateFormat));
        sb.append("\uD83D\uDCC5 ").append(escapeHtml(dateLabel)).append(": ").append(displayDate).append("\n\n");

        sb.append(escapeHtml(customersLabel)).append(":\n");
        for (int i = 0; i < items.size(); i++) {
            OrderItemDto item = items.get(i);
            sb.append(i + 1).append(". ").append(escapeHtml(item.customerName()));
            if (item.comment() != null && !item.comment().isBlank()) {
                sb.append(" - ").append(escapeHtml(item.comment()));
            }
            sb.append("\n");
        }

        sb.append("\n").append(escapeHtml(totalLabel)).append(": ")
                .append(items.size()).append(" ").append(escapeHtml(totalSuffix));
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
