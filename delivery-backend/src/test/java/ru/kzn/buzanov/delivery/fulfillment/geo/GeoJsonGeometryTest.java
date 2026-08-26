package ru.kzn.buzanov.delivery.fulfillment.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoJsonGeometryTest {

    private static final String SQUARE = """
            {
              "type": "Polygon",
              "coordinates": [
                [
                  [49.0, 55.7],
                  [49.2, 55.7],
                  [49.2, 55.9],
                  [49.0, 55.9],
                  [49.0, 55.7]
                ]
              ]
            }
            """;

    @Test
    void pointInsidePolygon() {
        GeoJsonGeometry geometry = GeoJsonGeometry.parse(SQUARE);
        assertThat(geometry.covers(49.1, 55.8)).isTrue();
    }

    @Test
    void pointOutsidePolygon() {
        GeoJsonGeometry geometry = GeoJsonGeometry.parse(SQUARE);
        assertThat(geometry.covers(49.3, 55.8)).isFalse();
    }

    @Test
    void boundaryPointIsInside() {
        GeoJsonGeometry geometry = GeoJsonGeometry.parse(SQUARE);
        assertThat(geometry.covers(49.0, 55.8)).isTrue();
        assertThat(geometry.covers(49.1, 55.7)).isTrue();
    }

    @Test
    void rejectsEmptyGeometryAndTooFewPoints() {
        assertThatThrownBy(() -> GeoJsonGeometry.parse("{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GeoJsonGeometry.parse("""
                {"type":"Polygon","coordinates":[[[49.0,55.7],[49.1,55.7],[49.0,55.7]]]}
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPresentationFields() {
        assertThatThrownBy(() -> GeoJsonGeometry.parse("""
                {"type":"Polygon","coordinates":[[[49.0,55.7],[49.2,55.7],[49.2,55.9],[49.0,55.9],[49.0,55.7]]],
                 "color":"green"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only type and coordinates");
    }

    @Test
    void multiPolygonMatchesAnyPart() {
        String multi = """
                {
                  "type": "MultiPolygon",
                  "coordinates": [
                    [[[49.0,55.7],[49.1,55.7],[49.1,55.8],[49.0,55.8],[49.0,55.7]]],
                    [[[49.3,55.7],[49.4,55.7],[49.4,55.8],[49.3,55.8],[49.3,55.7]]]
                  ]
                }
                """;
        GeoJsonGeometry geometry = GeoJsonGeometry.parse(multi);
        assertThat(geometry.covers(49.05, 55.75)).isTrue();
        assertThat(geometry.covers(49.35, 55.75)).isTrue();
        assertThat(geometry.covers(49.2, 55.75)).isFalse();
    }
}
