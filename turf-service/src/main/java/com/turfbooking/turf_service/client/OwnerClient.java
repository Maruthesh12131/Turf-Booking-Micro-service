package com.turfbooking.turf_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.turfbooking.turf_service.exception.OwnerNotVerifiedException;
import com.turfbooking.turf_service.exception.OwnerServiceUnavailableException;
import com.turfbooking.turf_service.exception.TurfAccessDeniedException;

/**
 * Resolves the caller's owner profile through owner-service. The caller's own bearer token is forwarded,
 * so owner-service derives the profile from the validated token and nothing client-supplied is trusted.
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

    private record OwnerProfile(Long ownerId, String verificationStatus) {
    }

    /** @throws TurfAccessDeniedException if the caller has no owner profile or owner-service rejects the token */
    public Long resolveOwnerId(String bearerToken) {
        return fetchProfile(bearerToken).ownerId();
    }

    /**
     * Same as {@link #resolveOwnerId}, but the owner must also be VERIFIED. Fails closed: if owner-service cannot be
     * reached the status is unknown, so this throws OwnerServiceUnavailableException rather than letting the owner through.
     *
     * @throws OwnerNotVerifiedException if the owner is PENDING or REJECTED
     */
    public Long resolveVerifiedOwnerId(String bearerToken) {
        OwnerProfile profile = fetchProfile(bearerToken);
        if (!"VERIFIED".equals(profile.verificationStatus())) {
            throw new OwnerNotVerifiedException();
        }
        return profile.ownerId();
    }

    private OwnerProfile fetchProfile(String bearerToken) {
        try {
            OwnerProfile profile = restClient.get()
                    .uri("/owners/me")
                    .headers(h -> h.setBearerAuth(bearerToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new TurfAccessDeniedException();
                    })
                    .body(OwnerProfile.class);
            if (profile == null || profile.ownerId() == null) {
                throw new TurfAccessDeniedException();
            }
            return profile;
        } catch (RestClientException e) {
            throw new OwnerServiceUnavailableException("Owner service is unavailable", e);
        }
    }
}
