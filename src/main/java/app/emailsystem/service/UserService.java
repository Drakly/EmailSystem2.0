package app.emailsystem.service;

import app.emailsystem.dto.UserDTO;
import app.emailsystem.entity.User;
import app.emailsystem.exception.EmailSystemException;
import app.emailsystem.exception.ResourceNotFoundException;
import app.emailsystem.repository.UserRepository;
import app.emailsystem.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(UserDTO userDTO) {
        log.info("Creating new user with email: {}", userDTO.getEmail());
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new EmailSystemException("Email already exists");
        }

        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());

        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user details for email: {}", email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(UUID id) {
        log.debug("Getting user with id: {}", id);
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        log.debug("Getting user with email: {}", email);
        return userRepository.findByEmail(email);
    }

    public boolean existsById(UUID id) {
        return userRepository.existsById(id);
    }

    @Transactional
    public User updateUser(UUID id, UserDTO userDTO) {
        log.info("Updating user with id: {}", id);
        User user = getUserById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        if (!user.getEmail().equals(userDTO.getEmail()) && 
            userRepository.existsByEmail(userDTO.getEmail())) {
            throw new EmailSystemException("Email already exists");
        }

        user.setEmail(userDTO.getEmail());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        log.info("Deleting user with id: {}", id);
        if (!existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
} 