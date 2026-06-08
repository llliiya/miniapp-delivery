package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.CreateRestaurantResponse;
import ru.kzn.buzanov.delivery.dto.OrganizationDto;
import ru.kzn.buzanov.delivery.dto.request.CreateRestaurantRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchOrganizationRequest;
import ru.kzn.buzanov.delivery.service.RestaurantService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRestaurantResponse create(HttpServletRequest request, @Valid @RequestBody CreateRestaurantRequest body) {
        var user = CurrentUserHolder.require(request);
        return restaurantService.create(user.userId(), body);
    }

    @GetMapping
    public List<OrganizationDto> list(HttpServletRequest request) {
        var user = CurrentUserHolder.require(request);
        return restaurantService.list(user.userId());
    }

    @GetMapping("/{id}")
    public OrganizationDto get(HttpServletRequest request, @PathVariable UUID id) {
        var user = CurrentUserHolder.require(request);
        return restaurantService.get(user.userId(), id);
    }

    @PatchMapping("/{id}")
    public OrganizationDto patch(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody PatchOrganizationRequest body) {
        var user = CurrentUserHolder.require(request);
        return restaurantService.patch(user.userId(), id, body);
    }
}
