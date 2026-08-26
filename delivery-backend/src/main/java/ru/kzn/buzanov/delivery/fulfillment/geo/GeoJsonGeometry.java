package ru.kzn.buzanov.delivery.fulfillment.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical GeoJSON Polygon / MultiPolygon. Coordinates are [lon, lat].
 * Color and other presentation fields are rejected — not stored.
 */
public final class GeoJsonGeometry {

    public static final String TYPE_POLYGON = "Polygon";
    public static final String TYPE_MULTI_POLYGON = "MultiPolygon";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double EPSILON = 1e-12;

    private final String type;
    private final String canonicalJson;
    private final List<Polygon> polygons;

    private GeoJsonGeometry(String type, String canonicalJson, List<Polygon> polygons) {
        this.type = type;
        this.canonicalJson = canonicalJson;
        this.polygons = List.copyOf(polygons);
    }

    public String type() {
        return type;
    }

    public String canonicalJson() {
        return canonicalJson;
    }

    public List<Polygon> polygons() {
        return polygons;
    }

    public static GeoJsonGeometry parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("geometry is required");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception ex) {
            throw invalid("geometry must be valid GeoJSON");
        }
        return parse(root);
    }

    public static GeoJsonGeometry parse(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid("geometry must be a GeoJSON object");
        }
        // Only type + coordinates — reject color/properties/crs presentation fields.
        if (root.size() != 2 || !root.has("type") || !root.has("coordinates")) {
            throw invalid("geometry must contain only type and coordinates");
        }
        String type = root.get("type").asText();
        JsonNode coordinates = root.get("coordinates");
        List<Polygon> polygons;
        if (TYPE_POLYGON.equals(type)) {
            polygons = List.of(parsePolygonCoordinates(coordinates));
        } else if (TYPE_MULTI_POLYGON.equals(type)) {
            if (coordinates == null || !coordinates.isArray() || coordinates.isEmpty()) {
                throw invalid("MultiPolygon coordinates must be a non-empty array");
            }
            List<Polygon> parsed = new ArrayList<>();
            for (JsonNode polygonNode : coordinates) {
                parsed.add(parsePolygonCoordinates(polygonNode));
            }
            polygons = parsed;
        } else {
            throw invalid("geometry type must be Polygon or MultiPolygon");
        }
        try {
            ObjectNode canonical = MAPPER.createObjectNode();
            canonical.put("type", type);
            canonical.set("coordinates", toCoordinatesNode(type, polygons));
            return new GeoJsonGeometry(type, MAPPER.writeValueAsString(canonical), polygons);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("geometry could not be canonicalized");
        }
    }

    public boolean covers(double lon, double lat) {
        requireFiniteCoordinate(lon, lat);
        for (Polygon polygon : polygons) {
            if (coversPolygon(polygon, lon, lat)) {
                return true;
            }
        }
        return false;
    }

    private static boolean coversPolygon(Polygon polygon, double lon, double lat) {
        if (onRing(polygon.exterior(), lon, lat)) {
            return true;
        }
        if (!rayCast(polygon.exterior(), lon, lat)) {
            return false;
        }
        for (List<double[]> hole : polygon.holes()) {
            if (onRing(hole, lon, lat)) {
                return true;
            }
            if (rayCast(hole, lon, lat)) {
                return false;
            }
        }
        return true;
    }

    private static Polygon parsePolygonCoordinates(JsonNode coordinates) {
        if (coordinates == null || !coordinates.isArray() || coordinates.isEmpty()) {
            throw invalid("Polygon coordinates must be a non-empty array of rings");
        }
        List<List<double[]>> rings = new ArrayList<>();
        for (JsonNode ringNode : coordinates) {
            rings.add(parseRing(ringNode));
        }
        List<double[]> exterior = rings.getFirst();
        List<List<double[]>> holes = rings.size() == 1 ? List.of() : List.copyOf(rings.subList(1, rings.size()));
        return new Polygon(exterior, holes);
    }

    private static List<double[]> parseRing(JsonNode ringNode) {
        if (ringNode == null || !ringNode.isArray()) {
            throw invalid("each ring must be an array of positions");
        }
        List<double[]> positions = new ArrayList<>();
        for (JsonNode positionNode : ringNode) {
            positions.add(parsePosition(positionNode));
        }
        if (positions.size() < 4) {
            throw invalid("each ring must have at least 4 positions (3 unique vertices, closed)");
        }
        double[] first = positions.getFirst();
        double[] last = positions.getLast();
        if (!samePoint(first, last)) {
            positions.add(new double[] {first[0], first[1]});
        }
        int unique = uniqueVertexCount(positions);
        if (unique < 3) {
            throw invalid("each ring must contain at least 3 unique vertices");
        }
        if (signedArea(positions) == 0d) {
            throw invalid("geometry ring must enclose a non-zero area");
        }
        return List.copyOf(positions);
    }

    private static double[] parsePosition(JsonNode positionNode) {
        if (positionNode == null || !positionNode.isArray() || positionNode.size() < 2) {
            throw invalid("each position must be [lon, lat]");
        }
        double lon = asFinite(positionNode.get(0), "longitude");
        double lat = asFinite(positionNode.get(1), "latitude");
        if (lon < -180d || lon > 180d || lat < -90d || lat > 90d) {
            throw invalid("coordinates must be lon[-180..180], lat[-90..90]");
        }
        return new double[] {lon, lat};
    }

    private static double asFinite(JsonNode node, String field) {
        if (node == null || !node.isNumber()) {
            throw invalid(field + " must be a number");
        }
        double value = node.asDouble();
        if (!Double.isFinite(value)) {
            throw invalid(field + " must be finite");
        }
        return value;
    }

    public static void requireFiniteCoordinate(double lon, double lat) {
        if (!Double.isFinite(lon) || !Double.isFinite(lat)
                || lon < -180d || lon > 180d || lat < -90d || lat > 90d) {
            throw invalid("coordinates must be lon[-180..180], lat[-90..90]");
        }
    }

    private static boolean samePoint(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) <= EPSILON && Math.abs(a[1] - b[1]) <= EPSILON;
    }

    private static int uniqueVertexCount(List<double[]> ring) {
        int unique = 0;
        for (int i = 0; i < ring.size() - 1; i++) {
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (samePoint(ring.get(i), ring.get(j))) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique++;
            }
        }
        return unique;
    }

    private static double signedArea(List<double[]> ring) {
        double sum = 0d;
        for (int i = 0; i < ring.size() - 1; i++) {
            double[] a = ring.get(i);
            double[] b = ring.get(i + 1);
            sum += (a[0] * b[1]) - (b[0] * a[1]);
        }
        return sum / 2d;
    }

    private static boolean onRing(List<double[]> ring, double lon, double lat) {
        for (int i = 0; i < ring.size() - 1; i++) {
            if (onSegment(ring.get(i), ring.get(i + 1), lon, lat)) {
                return true;
            }
        }
        return false;
    }

    private static boolean onSegment(double[] a, double[] b, double lon, double lat) {
        double cross = (lon - a[0]) * (b[1] - a[1]) - (lat - a[1]) * (b[0] - a[0]);
        if (Math.abs(cross) > 1e-9) {
            return false;
        }
        double minX = Math.min(a[0], b[0]) - EPSILON;
        double maxX = Math.max(a[0], b[0]) + EPSILON;
        double minY = Math.min(a[1], b[1]) - EPSILON;
        double maxY = Math.max(a[1], b[1]) + EPSILON;
        return lon >= minX && lon <= maxX && lat >= minY && lat <= maxY;
    }

    /**
     * Even-odd ray cast. Horizontal ray to +lon.
     */
    private static boolean rayCast(List<double[]> ring, double lon, double lat) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 2; i < ring.size() - 1; j = i++) {
            double xi = ring.get(i)[0];
            double yi = ring.get(i)[1];
            double xj = ring.get(j)[0];
            double yj = ring.get(j)[1];
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / (yj - yi + 0.0) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static JsonNode toCoordinatesNode(String type, List<Polygon> polygons) {
        if (TYPE_POLYGON.equals(type)) {
            return ringsToNode(polygons.getFirst());
        }
        ArrayNode multi = MAPPER.createArrayNode();
        for (Polygon polygon : polygons) {
            multi.add(ringsToNode(polygon));
        }
        return multi;
    }

    private static ArrayNode ringsToNode(Polygon polygon) {
        ArrayNode rings = MAPPER.createArrayNode();
        rings.add(ringToNode(polygon.exterior()));
        for (List<double[]> hole : polygon.holes()) {
            rings.add(ringToNode(hole));
        }
        return rings;
    }

    private static ArrayNode ringToNode(List<double[]> ring) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (double[] point : ring) {
            ArrayNode pos = MAPPER.createArrayNode();
            pos.add(point[0]);
            pos.add(point[1]);
            arr.add(pos);
        }
        return arr;
    }

    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(detail);
    }

    public record Polygon(List<double[]> exterior, List<List<double[]>> holes) {
    }
}
