package com.turfbooking.booking_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.turfbooking.booking_service.exception.OwnerAccessDeniedException;
import com.turfbooking.booking_service.exception.OwnerServiceUnavailableException;

/**
 * Finds the caller's owner id through owner-service. The caller's own bearer token is forwarded, so owner-service
 * derives the profile from the validated token and nothing client-supplied is trusted. The same owner id is what
 * turf-service puts on each turf, which is how a booking is tied to the owner whose turf it is on.
 */
@Component
public class OwnerClient {

    private final RestClient restClient;

    public OwnerClient(@Value("${owner-service.url}") String ownerServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = RestClient.builder().baseUrl(ownerServiceUrl).requestFactory(factory).build();
    }

    private record OwnerProfile(Long ownerId) {
    }

    /** @throws OwnerAccessDeniedException if the caller has no owner profile or owner-service rejects the token */
    public Long resolveOwnerId(String bearerToken) {
        try {
            OwnerProfile profile = restClient.get()
                    .uri("/owners/me")
                    .headers(h -> h.setBearerAuth(bearerToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new OwnerAccessDeniedException();
                    })
                    .body(OwnerProfile.class);
            if (profile == null || profile.ownerId() == null) {
                throw new OwnerAccessDeniedException();
            }
            return profile.ownerId();
        } catch (RestClientException e) {
            throw new OwnerServiceUnavailableException("Owner service is unavailable", e);
        }
    }
}
