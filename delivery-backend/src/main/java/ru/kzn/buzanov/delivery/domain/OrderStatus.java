package ru.kzn.buzanov.delivery.domain;

public enum OrderStatus {
    waiting_for_courier,
    courier_heading_to_pickup,
    courier_delivering,
    completed,
    cancelled
}
