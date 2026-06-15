package ru.kzn.buzanov.delivery.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.AddMemberResponse;
import ru.kzn.buzanov.delivery.dto.MemberDto;
import ru.kzn.buzanov.delivery.dto.OrganizationDto;
import ru.kzn.buzanov.delivery.dto.ProvisioningCredentialsDto;
import ru.kzn.buzanov.delivery.dto.request.AddMemberRequest;
import ru.kzn.buzanov.delivery.dto.request.CreateOrganizationRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchMemberRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchOrganizationRequest;
import ru.kzn.buzanov.delivery.service.MemberService;
import ru.kzn.buzanov.delivery.service.OrganizationService;
import ru.kzn.buzanov.delivery.service.ServiceCityService;
import ru.kzn.buzanov.delivery.web.CurrentUser;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final MemberService memberService;
    private final ServiceCityService serviceCityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDto create(HttpServletRequest request, @Valid @RequestBody CreateOrganizationRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return organizationService.create(user.userId(), body);
    }

    @GetMapping
    public List<OrganizationDto> list(HttpServletRequest request) {
        CurrentUser user = CurrentUserHolder.require(request);
        return organizationService.list(user.userId());
    }

    @GetMapping("/{courierServiceId}/cities")
    public List<String> listCities(
            HttpServletRequest request,
            @PathVariable UUID courierServiceId) {
        CurrentUser user = CurrentUserHolder.require(request);
        return serviceCityService.listCities(user.userId(), courierServiceId);
    }

    @GetMapping("/{id}")
    public OrganizationDto get(HttpServletRequest request, @PathVariable UUID id) {
        CurrentUser user = CurrentUserHolder.require(request);
        return organizationService.get(user.userId(), id);
    }

    @PatchMapping("/{id}")
    public OrganizationDto patch(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody PatchOrganizationRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return organizationService.patch(user.userId(), id, body);
    }

    @GetMapping("/{id}/members")
    public List<MemberDto> listMembers(HttpServletRequest request, @PathVariable UUID id) {
        CurrentUser user = CurrentUserHolder.require(request);
        return memberService.list(user.userId(), id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public AddMemberResponse addMember(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return memberService.add(user.userId(), id, body);
    }

    @PatchMapping("/{id}/members/{userId}")
    public MemberDto patchMember(
            HttpServletRequest request,
            @PathVariable UUID id,
            @PathVariable Long userId,
            @RequestBody PatchMemberRequest body) {
        CurrentUser user = CurrentUserHolder.require(request);
        return memberService.patch(user.userId(), id, userId, body);
    }

    @PostMapping("/{id}/members/{userId}/reset-access")
    public ProvisioningCredentialsDto resetMemberAccess(
            HttpServletRequest request,
            @PathVariable UUID id,
            @PathVariable Long userId) {
        CurrentUser user = CurrentUserHolder.require(request);
        return memberService.resetAccess(user.userId(), id, userId);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            HttpServletRequest request,
            @PathVariable UUID id,
            @PathVariable Long userId) {
        CurrentUser user = CurrentUserHolder.require(request);
        memberService.removeFromOrganization(user.userId(), id, userId);
    }
}
