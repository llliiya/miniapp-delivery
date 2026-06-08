package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.RestaurantChannelsResponseDto;
import ru.kzn.buzanov.delivery.dto.request.ReplaceRestaurantChannelsRequest;
import ru.kzn.buzanov.delivery.service.RestaurantChannelService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RestaurantChannelController {

    private final RestaurantChannelService restaurantChannelService;

    @GetMapping("/restaurants/{restaurantId}/channels")
    public RestaurantChannelsResponseDto list(HttpServletRequest request, @PathVariable UUID restaurantId) {
        var user = CurrentUserHolder.require(request);
        return restaurantChannelService.list(user.userId(), restaurantId);
    }

    @PutMapping("/restaurants/{restaurantId}/channels")
    public RestaurantChannelsResponseDto replace(
            HttpServletRequest request,
            @PathVariable UUID restaurantId,
            @RequestBody ReplaceRestaurantChannelsRequest body) {
        var user = CurrentUserHolder.require(request);
        return restaurantChannelService.replace(user.userId(), restaurantId, body);
    }
}
