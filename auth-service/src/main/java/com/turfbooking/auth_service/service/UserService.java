package com.turfbooking.auth_service.service;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.turfbooking.auth_service.repository.UserRepo;
import com.turfbooking.auth_service.entity.User;
import com.turfbooking.auth_service.dto.SignupDTO;
import com.turfbooking.auth_service.enums.Role;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import com.turfbooking.auth_service.event.OwnerRegisteredEvent;
import com.turfbooking.auth_service.messaging.OwnerEventPublishException;
import com.turfbooking.auth_service.messaging.OwnerEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
@Service
public class UserService implements UserDetailsService {
    private static final String PHONE_PATTERN = "^\\+?[0-9]{7,15}$";
    private final UserRepo userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OwnerEventPublisher ownerEventPublisher;
    public UserService (UserRepo userRepository, PasswordEncoder passwordEncoder, OwnerEventPublisher ownerEventPublisher){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.ownerEventPublisher = ownerEventPublisher;
    }

    public Optional<User> findByEmail(String email){
        Optional<User> user = userRepository.findByEmail(email);
        return user;
    }

    @Transactional
    public User saveUser(SignupDTO signup){
        Role role = signup.getRole() == null ? Role.CUSTOMER : signup.getRole();
        if(role == Role.ADMIN){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot self-register with this role");
        }
        // Every account needs a phone number (bookings show it to the turf owner). Checked before anything is saved,
        // so owner-service never receives a number it would reject and leaves the user without a profile.
        if(signup.getPhoneNumber() == null || !signup.getPhoneNumber().matches(PHONE_PATTERN)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid phoneNumber is required");
        }
        User newUser = new User();
        newUser.setEmail(signup.getEmail());
        newUser.setName(signup.getName());
        newUser.setPhoneNumber(signup.getPhoneNumber());
        newUser.setPasswordHash(passwordEncoder.encode(signup.getPassword()));
        newUser.setRole(role);
        // Flush now so a duplicate email fails here, before anything is sent to the broker.
        User saved = userRepository.saveAndFlush(newUser);
        if(role == Role.OWNER){
            String businessName = signup.getBusinessName() == null || signup.getBusinessName().isBlank()
                    ? signup.getName() : signup.getBusinessName();
            // Sent before the commit: if the broker cannot take it, the exception below rolls the user back,
            // so a failed signup never leaves an owner without a profile.
            try {
                ownerEventPublisher.publish(new OwnerRegisteredEvent(saved.getUserId().longValue(), businessName, signup.getPhoneNumber()));
            } catch (OwnerEventPublishException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Owner registration is temporarily unavailable, please try again later", e);
            }
        }
        return saved;
    }

    
    @Override 
    public UserDetails loadUserByUsername(String email){
        Optional<User> user = userRepository.findByEmail(email);
        if(user.isPresent()){
            User foundUser = user.get();
            return org.springframework.security.core.userdetails.User.builder() 
                    .username(foundUser.getEmail())
                    .password(foundUser.getPasswordHash())
                    .roles(foundUser.getRole().name())
                    .build();
        } else {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
    }
}