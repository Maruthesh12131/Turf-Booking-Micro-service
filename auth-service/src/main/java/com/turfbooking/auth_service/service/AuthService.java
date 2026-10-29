package com.turfbooking.auth_service.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.turfbooking.auth_service.dto.LoginDTO;
import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.repository.UserRepo;

import java.time.Instant;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final UserRepo userRepo;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            UserRepo userRepo) {

        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.userRepo = userRepo;
    }

    public String loginUser(LoginDTO loginDetails) {

        String email = loginDetails.getEmail();
        String password = loginDetails.getPassword();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(email, password);

        Authentication authenticated =
                authenticationManager.authenticate(authentication);

        User user = userRepo.findByEmail(authenticated.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(authenticated.getName())
                .claim("uid", user.getUserId())
                // Carried so booking-service can record the customer's contact details without calling back here.
                .claim("name", user.getName())
                .claim("phone", user.getPhoneNumber())
                .claim("role",
                        authenticated.getAuthorities()
                                .iterator()
                                .next()
                                .getAuthority())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        Jwt jwt = jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        );

        return jwt.getTokenValue();
    }
}