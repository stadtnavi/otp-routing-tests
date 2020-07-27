package de.stadtnavi;

import static java.lang.Float.parseFloat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    }

    static HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

    public static void main(String[] args) {
        var requests =
                parseRoutes()
                        .map(r -> HttpRequest.newBuilder().uri(makeUri(r)).GET().build())
                        .collect(Collectors.toList());

        requests.forEach(
                req -> {
                    try {
                        var response = client.send(req, HttpResponse.BodyHandlers.ofString());
                        System.out.println(response.statusCode());
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                });
    }

    private static URI makeUri(Route route) {
        var from = route.from;
        var to = route.to;
        var uri =
                "https://api.mih.mitfahren-bw.de/routing/v1/router/plan?fromPlace=%f,%f&toPlace=%f,%f&mode=TRANSIT,WALK&maxWalkDistance=100000&arriveBy=false&wheelchair=false&locale=en";
        try {
            return new URI(String.format(uri, from.lat, from.lng, to.lat, to.lng));
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Stream<Route> parseRoutes() {
        var stream = App.class.getResourceAsStream("/routes.csv");
        var lines = new BufferedReader(new InputStreamReader(stream)).lines();
        var parts = lines.map(l -> l.split(","));
        return parts.map(
                p ->
                        new Route(
                                new Place(p[0], parseFloat(p[1]), parseFloat(p[2])),
                                new Place(p[3], parseFloat(p[4]), parseFloat(p[5]))));
    }
}
