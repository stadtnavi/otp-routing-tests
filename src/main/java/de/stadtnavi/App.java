package de.stadtnavi;

import static java.lang.Float.parseFloat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    public static class Route {
        Place from;
        Place to;

        public Route(Place from, Place to) {
            this.from = from;
            this.to = to;
        }
    }

    public static class Place {
        String label;
        double lat, lng;

        public Place(String label, double lat, double lng) {
            this.label = label;
            this.lat = lat;
            this.lng = lng;
        }

        @Override
        public String toString() {
            return label + "(" + lat + "," + lng + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Place place = (Place) o;
            return Double.compare(place.lat, lat) == 0 &&
                    Double.compare(place.lng, lng) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lat, lng);
        }
    }

    static Logger log = LoggerFactory.getLogger("log");

    static HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

    static String modeTransit = "TRANSIT,WALK";
    static String sbahnBikeRental = "WALK,BICYCLE_RENT,RAIL";

    static ConcurrentLinkedQueue<Route> failedRoutes = new ConcurrentLinkedQueue<>();

    public static void main(String[] args) {
        buildCombinations(parseLocations())
                .forEach(
                        route -> {
                            queryRoute(route, modeTransit);
                            queryRoute(route, sbahnBikeRental);
                        });
    }

    static Stream<Route> buildCombinations(List<Place> places) {
        return places.stream()
                .flatMap(from -> places.stream().map(to -> new Route(from, to)))
                .filter(r -> !r.from.equals(r.to));
    }

    static void queryRoute(Route route, String mode) {
        var req = HttpRequest.newBuilder().uri(makeUri(route, mode)).GET().build();
        try {
            var start = Instant.now();
            var response = client.send(req, HttpResponse.BodyHandlers.ofString());
            var end = Instant.now();
            var duration = Duration.between(start, end).abs();
            report(route, mode, response, duration);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void report(Route route, String mode, HttpResponse<String> resp, Duration duration) {
        var isSuccess = isSuccess(resp);
        if (isSuccess) {
            log.info(
                    "Route from {} to {} with modes {} took {} ms.",
                    route.from,
                    route.to,
                    mode,
                    duration.toMillis());
        } else {
            failedRoutes.add(route);
            log.error("Route from {} to {} with modes {} failed: {}", route.from, route.to, mode, resp.body());
        }
    }

    static boolean isSuccess(HttpResponse<String> resp) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode json = mapper.readTree(resp.body());
            return json.get("plan").get("itineraries").size() > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    static URI makeUri(Route route, String modes) {
        var from = route.from;
        var to = route.to;
        var uri =
                "https://api.mih.mitfahren-bw.de/routing/v1/router/plan?fromPlace=%f,%f&toPlace=%f,%f&mode=%s&maxWalkDistance=15000&arriveBy=false&wheelchair=false&locale=en";
        try {
            return new URI(String.format(uri, from.lat, from.lng, to.lat, to.lng, modes));
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    static List<Place> parseLocations() {
        var stream = App.class.getResourceAsStream("/locations.csv");
        var lines = new BufferedReader(new InputStreamReader(stream)).lines();
        var parts = lines.map(l -> l.split(","));
        return parts.map(p -> new Place(p[0], parseFloat(p[1]), parseFloat(p[2])))
                .collect(Collectors.toList());
    }
}
