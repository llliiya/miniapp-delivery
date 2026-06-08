package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.PickupPointDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePickupPointRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchPickupPointRequest;
import ru.kzn.buzanov.delivery.service.PickupPointService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PickupPointController {

    private final PickupPointService pickupPointService;

    @GetMapping("/restaurants/{restaurantId}/pickup-points")
    public List<PickupPointDto> list(HttpServletRequest request, @PathVariable UUID restaurantId) {
        var user = CurrentUserHolder.require(request);
        return pickupPointService.list(user.userId(), restaurantId);
    }

    @PostMapping("/restaurants/{restaurantId}/pickup-points")
    @ResponseStatus(HttpStatus.CREATED)
    public PickupPointDto create(
            HttpServletRequest request,
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreatePickupPointRequest body) {
        var user = CurrentUserHolder.require(request);
        return pickupPointService.create(user.userId(), restaurantId, body);
    }

    @PatchMapping("/pickup-points/{id}")
    public PickupPointDto patch(
            HttpServletRequest request,
            @PathVariable("id") UUID pointId,
            @RequestBody PatchPickupPointRequest body) {
        var user = CurrentUserHolder.require(request);
        return pickupPointService.patch(user.userId(), pointId, body);
    }

    @DeleteMapping("/pickup-points/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable("id") UUID pointId) {
        var user = CurrentUserHolder.require(request);
        pickupPointService.delete(user.userId(), pointId);
    }
}
