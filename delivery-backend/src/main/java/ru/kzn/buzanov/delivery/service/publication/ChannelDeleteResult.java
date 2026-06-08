package ru.kzn.buzanov.delivery.service.publication;

public record ChannelDeleteResult(
        boolean success,
        String errorMessage
) {
    public static ChannelDeleteResult ok() {
        return new ChannelDeleteResult(true, null);
    }

    public static ChannelDeleteResult fail(String errorMessage) {
        return new ChannelDeleteResult(false, errorMessage);
    }
}
