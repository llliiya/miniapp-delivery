package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.MeResponseDto;
import ru.kzn.buzanov.delivery.dto.request.SetActiveOrganizationRequest;
import ru.kzn.buzanov.delivery.service.MeService;
import ru.kzn.buzanov.delivery.web.CurrentUser;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @GetMapping("/me")
    public ResponseEntity<MeResponseDto> me(HttpServletRequest request) {
        CurrentUser user = CurrentUserHolder.require(request);
        return ResponseEntity.ok(meService.getMe(user.userId()));
    }

    @PatchMapping("/me/active-organization")
    public ResponseEntity<MeResponseDto> setActiveOrganization(
            HttpServletRequest request,
            @Valid @RequestBody SetActiveOrganizationRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return ResponseEntity.ok(meService.setActiveOrganization(user.userId(), body));
    }
}
