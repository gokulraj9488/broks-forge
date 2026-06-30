package com.broksforge.modules.user.security;

import com.broksforge.modules.user.domain.User;
import com.broksforge.modules.user.domain.UserStatus;
import com.broksforge.modules.user.repository.UserRepository;
import com.broksforge.security.CustomUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads users for Spring Security's {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}.
 * Used by the username/password login flow; JWT-authenticated requests are
 * served from token claims without touching this service.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isLoginAllowed(),
                user.getStatus() != UserStatus.SUSPENDED,
                authorities);
    }
}
