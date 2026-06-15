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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.kzn.buzanov.delivery.dto.PublicationChannelDto;
import ru.kzn.buzanov.delivery.dto.request.CreatePublicationChannelRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchPublicationChannelRequest;
import ru.kzn.buzanov.delivery.service.PublicationChannelService;
import ru.kzn.buzanov.delivery.web.CurrentUserHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/channels")
@RequiredArgsConstructor
public class PublicationChannelController {

    private final PublicationChannelService publicationChannelService;

    @GetMapping
    public List<PublicationChannelDto> list(
            HttpServletRequest request,
            @RequestParam UUID courierServiceId,
            @RequestParam(required = false) String city) {
        var user = CurrentUserHolder.require(request);
        return publicationChannelService.list(user.userId(), courierServiceId, city);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationChannelDto create(
            HttpServletRequest request,
            @Valid @RequestBody CreatePublicationChannelRequest body) {
        var user = CurrentUserHolder.require(request);
        return publicationChannelService.create(user.userId(), body);
    }

    @PatchMapping("/{id}")
    public PublicationChannelDto patch(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody PatchPublicationChannelRequest body) {
        var user = CurrentUserHolder.require(request);
        return publicationChannelService.patch(user.userId(), id, body);
    }

    @DeleteMapping("/{id}")
    public PublicationChannelDto deactivate(HttpServletRequest request, @PathVariable UUID id) {
        var user = CurrentUserHolder.require(request);
        return publicationChannelService.deactivate(user.userId(), id);
    }
}
