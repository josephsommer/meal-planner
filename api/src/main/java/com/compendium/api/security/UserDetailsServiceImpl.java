package com.compendium.api.security;

import com.compendium.api.domain.User;
import com.compendium.api.domain.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security asks this for a UserDetails by username during form login.
 * With exactly one UserDetailsService bean and one PasswordEncoder bean in
 * the context, Spring Boot wires a DaoAuthenticationProvider around them
 * automatically — no explicit AuthenticationManager bean needed.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user with username " + username));

        // Single role for everyone for now — there's no roles table yet and
        // nothing in the app authorizes by role.
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("ROLE_USER")
                .build();
    }
}
