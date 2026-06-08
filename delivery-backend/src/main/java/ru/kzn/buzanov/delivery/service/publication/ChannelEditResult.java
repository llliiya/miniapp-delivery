package ru.kzn.buzanov.delivery.service.publication;

public record ChannelEditResult(boolean success, String errorMessage) {

    public static ChannelEditResult ok() {
        return new ChannelEditResult(true, null);
    }

    public static ChannelEditResult fail(String errorMessage) {
        return new ChannelEditResult(false, errorMessage);
    }
}
