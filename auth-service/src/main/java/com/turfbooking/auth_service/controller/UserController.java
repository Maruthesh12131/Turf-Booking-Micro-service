package com.turfbooking.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.turfbooking.auth_service.dto.LoginDTO;
import com.turfbooking.auth_service.dto.SignupDTO;
import com.turfbooking.auth_service.dto.UserResponseDTO;
import com.turfbooking.auth_service.service.AuthService;
import com.turfbooking.auth_service.service.UserService;

@RestController
@CrossOrigin
public class UserController {
    UserService userService;
    AuthService authService;

    public UserController(UserService userService, AuthService authService){
        this.userService = userService;
        this.authService = authService;
    }

    // A logged-in user may look up their own record only. Any other email answers 404, exactly like an unknown one,
    // so this cannot be used to find out which accounts exist.
    @GetMapping("/users/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@AuthenticationPrincipal Jwt jwt, @PathVariable String email) {
        if (!email.equalsIgnoreCase(jwt.getSubject())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return userService.findByEmail(jwt.getSubject())
                .map(UserResponseDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/users/create")
    public UserResponseDTO createUser(@RequestBody SignupDTO signup){
        return UserResponseDTO.from(userService.saveUser(signup));
    }

    @PostMapping("/users/login")
    public String userLogin(@RequestBody LoginDTO loginDetails){
        return authService.loginUser(loginDetails);
    }
}
