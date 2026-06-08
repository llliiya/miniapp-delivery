package ru.kzn.buzanov.delivery.dto.request;

import java.util.List;
import java.util.UUID;

public record ReplaceRestaurantChannelsRequest(List<UUID> channelIds) {
}
