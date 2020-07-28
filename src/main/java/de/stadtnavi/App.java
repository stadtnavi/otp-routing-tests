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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record Place(String label, double lat, double lng){}
record Route(Place from, Place to) {}

public class App {
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
        var routes = buildCombinations(parseLocations());
        log.info("Running {} route combinations with 2 sets of modes.", routes.size());
        routes.forEach(
                route -> {
                    queryRoute(route, modeTransit);
                    queryRoute(route, sbahnBikeRental);
                });
    }

    static List<Route> buildCombinations(List<Place> places) {
        return places.stream()
                .flatMap(from -> places.stream().map(to -> new Route(from, to)))
                .filter(r -> !r.from().equals(r.to())).collect(Collectors.toList());
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
                    route.from(),
                    route.to(),
                    mode,
                    duration.toMillis());
        } else {
            failedRoutes.add(route);
            log.error(
                    "Route from {} to {} with modes {} failed: {}",
                    route.from(),
                    route.to(),
                    mode,
                    resp.body());
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
        var from = route.from();
        var to = route.to();
        var uri =
                "https://api.mih.mitfahren-bw.de/routing/v1/router/plan?fromPlace=%f,%f&toPlace=%f,%f&mode=%s&maxWalkDistance=15000&arriveBy=false&wheelchair=false&locale=en";
        try {
            return new URI(String.format(uri, from.lat(), from.lng(), to.lat(), to.lng(), modes));
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
