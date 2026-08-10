package ru.kzn.buzanov.delivery.fulfillment.restaurant;

public class RestaurantServiceUnavailableException extends RuntimeException {

    public RestaurantServiceUnavailableException(String message) {
        super(message);
    }

    public RestaurantServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
