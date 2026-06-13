package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.PartnerProgramDto;
import ru.kzn.buzanov.delivery.service.PartnerProgramService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PartnerProgramController {

    private final PartnerProgramService partnerProgramService;

    @GetMapping("/couriers/{memberId}/partner-program")
    public PartnerProgramDto getCourierPartnerProgram(
            HttpServletRequest request,
            @PathVariable UUID memberId) {
        var user = CurrentUserHolder.require(request);
        return partnerProgramService.getForCourierMember(user.userId(), memberId);
    }

    @GetMapping("/restaurants/{restaurantId}/partner-program")
    public PartnerProgramDto getRestaurantPartnerProgram(
            HttpServletRequest request,
            @PathVariable UUID restaurantId) {
        var user = CurrentUserHolder.require(request);
        return partnerProgramService.getForRestaurant(user.userId(), restaurantId);
    }
}
