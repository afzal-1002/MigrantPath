package com.foreignerwarsaw.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The {@link UserDetails} Spring Security authenticates and stores in the session. {@code
 * accountNonLocked} is computed once, at load time, by {@link AppUserDetailsService} (using the
 * injected {@link java.time.Clock}, not {@code Instant.now()} - brief §36) rather than recomputed
 * here, keeping this a plain immutable value object.
 *
 * <p>{@code enabled} reflects email verification. {@code UserStatus.DISABLED} is a reserved future
 * status with no code path that sets it in Phase 2 - see {@link UserStatus} - so today {@code
 * enabled == false} only ever means "not yet verified," which is exactly what lets {@link
 * com.foreignerwarsaw.auth.LoginService} map a {@link
 * org.springframework.security.authentication.DisabledException} to the {@code EMAIL_NOT_VERIFIED}
 * error code unambiguously.
 */
public final class AppUserPrincipal implements UserDetails {

  private final UUID userId;
  private final String email;
  private final String passwordHash;
  private final boolean enabled;
  private final boolean accountNonLocked;
  private final List<String> roleCodes;

  public AppUserPrincipal(
      UUID userId,
      String email,
      String passwordHash,
      boolean enabled,
      boolean accountNonLocked,
      List<String> roleCodes) {
    this.userId = userId;
    this.email = email;
    this.passwordHash = passwordHash;
    this.enabled = enabled;
    this.accountNonLocked = accountNonLocked;
    this.roleCodes = List.copyOf(roleCodes);
  }

  public UUID getUserId() {
    return userId;
  }

  public List<String> getRoleCodes() {
    return roleCodes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roleCodes.stream().map(code -> new SimpleGrantedAuthority("ROLE_" + code)).toList();
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return accountNonLocked;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
