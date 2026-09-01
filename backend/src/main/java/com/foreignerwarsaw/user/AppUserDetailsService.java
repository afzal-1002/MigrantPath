package com.foreignerwarsaw.user;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges our {@link User} to Spring Security. {@link
 * org.springframework.security.authentication.dao.DaoAuthenticationProvider} (auto-configured once
 * a {@code UserDetailsService} and {@code PasswordEncoder} are both beans - see SecurityConfig)
 * uses this, and by default masks a genuine {@link UsernameNotFoundException} as {@link
 * org.springframework.security.authentication.BadCredentialsException} before it reaches calling
 * code - the account-enumeration protection required by brief §9/§46 on the login path comes from
 * that default behavior, not custom code here.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;
  private final Clock clock;

  public AppUserDetailsService(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UsernameNotFoundException("No user for email"));
    return toPrincipal(user);
  }

  private AppUserPrincipal toPrincipal(User user) {
    boolean accountNonLocked = !isCurrentlyLocked(user);
    return new AppUserPrincipal(
        user.getId(),
        user.getEmail(),
        user.getPasswordHash(),
        user.isEmailVerified(),
        accountNonLocked,
        user.getRoles().stream().map(Role::getCode).toList());
  }

  private boolean isCurrentlyLocked(User user) {
    Instant lockedUntil = user.getLockedUntil();
    return lockedUntil != null && lockedUntil.isAfter(clock.instant());
  }
}
