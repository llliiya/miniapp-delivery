package ru.kzn.buzanov.delivery.dto;

public record ProvisioningCredentialsDto(
        String login,
        String temporaryPassword,
        boolean mustChangePassword
) {
    public static ProvisioningCredentialsDto fromProvision(String login, String temporaryPassword) {
        return new ProvisioningCredentialsDto(login, temporaryPassword, true);
    }
}
