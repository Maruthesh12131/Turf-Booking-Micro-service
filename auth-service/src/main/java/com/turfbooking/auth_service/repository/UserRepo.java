package com.turfbooking.auth_service.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.turfbooking.auth_service.entity.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<User, Integer> {
    public Optional<User> findByEmail(String email);

}