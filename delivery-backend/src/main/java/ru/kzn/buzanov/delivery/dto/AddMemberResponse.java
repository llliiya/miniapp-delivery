package ru.kzn.buzanov.delivery.dto;

public record AddMemberResponse(
        MemberDto member,
        ProvisioningCredentialsDto credentials
) {
}
