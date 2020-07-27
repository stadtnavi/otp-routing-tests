package de.stadtnavi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class App {

    static HttpClient client =
            HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();

    public static void main(String[] args) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                new URI(
                                        "https://api.mih.mitfahren-bw.de/routing/v1/router/plan?fromPlace=48.73264383158835%2C9.112472534179686&toPlace=48.68370757165364%2C8.998832702636719&time=11%3A06am&date=07-27-2020&mode=TRANSIT%2CWALK&maxWalkDistance=804.672&arriveBy=false&wheelchair=false&locale=en"))
                        .GET()
                        .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.statusCode());
        System.out.println(response.body());
    }
}
