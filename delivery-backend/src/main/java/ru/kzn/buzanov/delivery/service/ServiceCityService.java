package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PublicationChannelRepository;
import ru.kzn.buzanov.delivery.util.CityNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceCityService {

    private final OrganizationRepository organizationRepository;
    private final PublicationChannelRepository channelRepository;
    private final AccessControlService accessControl;

    @Transactional(readOnly = true)
    public List<String> listCities(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        accessControl.requireCourierService(courierServiceId);

        Set<String> cities = new LinkedHashSet<>();
        for (String raw : organizationRepository.findDistinctRestaurantCities(courierServiceId)) {
            String normalized = CityNormalizer.normalize(raw);
            if (normalized != null) {
                cities.add(normalized);
            }
        }
        for (String raw : channelRepository.findDistinctCities(courierServiceId)) {
            String normalized = CityNormalizer.normalize(raw);
            if (normalized != null) {
                cities.add(normalized);
            }
        }
        List<String> sorted = new ArrayList<>(cities);
        sorted.sort(Comparator.comparing(String::toLowerCase));
        return sorted;
    }
}
