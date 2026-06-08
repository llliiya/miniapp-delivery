package ru.kzn.buzanov.delivery.service.publication;

public record ChannelPublishResult(
        boolean success,
        String externalMessageId,
        String errorMessage
) {
    public static ChannelPublishResult ok(String externalMessageId) {
        return new ChannelPublishResult(true, externalMessageId, null);
    }

    public static ChannelPublishResult fail(String errorMessage) {
        return new ChannelPublishResult(false, null, errorMessage);
    }
}
