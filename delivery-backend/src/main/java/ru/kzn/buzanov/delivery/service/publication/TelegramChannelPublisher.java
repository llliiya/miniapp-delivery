package ru.kzn.buzanov.delivery.service.publication;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.PublicationChannel;

@Slf4j
@Component
public class TelegramChannelPublisher {

    private final TelegramBot telegramBot;
    private final TelegramKeyboardFactory keyboardFactory;
    private final OrderMessageFormatter messageFormatter;

    public TelegramChannelPublisher(
            @Autowired(required = false) TelegramBot telegramBot,
            TelegramKeyboardFactory keyboardFactory,
            OrderMessageFormatter messageFormatter) {
        this.telegramBot = telegramBot;
        this.keyboardFactory = keyboardFactory;
        this.messageFormatter = messageFormatter;
    }

    public ChannelPublishResult publishOrder(PublicationChannel channel, DeliveryOrder order) {
        if (telegramBot == null) {
            return ChannelPublishResult.fail("Telegram bot not configured");
        }
        try {
            long chatId = Long.parseLong(channel.getExternalId().trim());
            log.info(
                    "Публикация заказа #{} в Telegram. Канал: {}. ChatId: {}",
                    order.getPublicNumber(),
                    channel.getName(),
                    chatId);
            String text = messageFormatter.formatOrderCardTelegramHtml(order, null);
            InlineKeyboardMarkup keyboard = buildOrderKeyboard(channel, order, chatId);
            SendResponse response = telegramBot.execute(
                    new SendMessage(chatId, text).parseMode(ParseMode.HTML).replyMarkup(keyboard));
            return toResult(response);
        } catch (NumberFormatException e) {
            log.warn("Telegram response: ok=false description=invalid telegram chat id");
            return ChannelPublishResult.fail("invalid telegram chat id");
        } catch (Exception e) {
            log.warn("Telegram response: ok=false description={}", e.getMessage());
            return ChannelPublishResult.fail(e.getMessage());
        }
    }

    public ChannelEditResult editOrder(
            PublicationChannel channel,
            String chatId,
            String messageId,
            DeliveryOrder order,
            String courierName) {
        if (telegramBot == null) {
            return ChannelEditResult.fail("Telegram bot not configured");
        }
        if (messageId == null || messageId.isBlank()) {
            return ChannelEditResult.fail("message id is empty");
        }
        if (chatId == null || chatId.isBlank()) {
            return ChannelEditResult.fail("chat id is empty");
        }
        try {
            long cid = Long.parseLong(chatId.trim());
            int mid = Integer.parseInt(messageId.trim());
            String text = messageFormatter.formatOrderCardTelegramHtml(order, courierName);
            InlineKeyboardMarkup keyboard = buildOrderKeyboard(channel, order, cid);
            log.info(
                    "Редактирование сообщения заказа #{} в Telegram. Канал: {}. ChatId: {}. MessageId: {}. status={}",
                    order.getPublicNumber(),
                    channel.getName(),
                    cid,
                    mid,
                    order.getStatus());
            BaseResponse response = telegramBot.execute(
                    new EditMessageText(cid, mid, text)
                            .parseMode(ParseMode.HTML)
                            .replyMarkup(keyboard));
            if (response.isOk()) {
                return ChannelEditResult.ok();
            }
            if (isMessageNotModified(response)) {
                return ChannelEditResult.ok();
            }
            String err = response.description() != null ? response.description() : "telegram edit failed";
            log.warn("Telegram editMessage: ok=false description={}", err);
            return ChannelEditResult.fail(err);
        } catch (NumberFormatException e) {
            return ChannelEditResult.fail("invalid chat id or message id");
        } catch (Exception e) {
            log.warn("Telegram editMessage: ok=false description={}", e.getMessage());
            return ChannelEditResult.fail(e.getMessage());
        }
    }

    private InlineKeyboardMarkup buildOrderKeyboard(PublicationChannel channel, DeliveryOrder order, long chatId) {
        if (order.getStatus() == OrderStatus.waiting_for_courier && order.getCourierUserId() == null) {
            InlineKeyboardButton button = keyboardFactory.buildOrderOpenButton(
                    order.getId(), channel.getChatType(), chatId);
            return new InlineKeyboardMarkup(button);
        }
        return new InlineKeyboardMarkup();
    }

    private static boolean isMessageNotModified(BaseResponse response) {
        String description = response.description();
        return description != null && description.toLowerCase().contains("message is not modified");
    }

    private ChannelPublishResult toResult(SendResponse response) {
        if (response.isOk() && response.message() != null) {
            log.info("Telegram response: ok=true messageId={}", response.message().messageId());
            return ChannelPublishResult.ok(String.valueOf(response.message().messageId()));
        }
        String err = response.description() != null ? response.description() : "telegram send failed";
        log.warn("Telegram response: ok=false description={}", err);
        return ChannelPublishResult.fail(err);
    }
}
