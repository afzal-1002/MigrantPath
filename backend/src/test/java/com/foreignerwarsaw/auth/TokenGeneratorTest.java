package com.foreignerwarsaw.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

  private final TokenGenerator tokenGenerator = new TokenGenerator();

  @Test
  void generatesUniqueRawTokensAndCorrespondingHashes() {
    TokenGenerator.GeneratedToken first = tokenGenerator.generate();
    TokenGenerator.GeneratedToken second = tokenGenerator.generate();

    assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
    assertThat(first.tokenHash()).isNotEqualTo(second.tokenHash());
    assertThat(first.tokenHash()).isEqualTo(tokenGenerator.hash(first.rawToken()));
  }

  @Test
  void hashingIsDeterministicAndNeverEqualsTheRawToken() {
    TokenGenerator.GeneratedToken generated = tokenGenerator.generate();

    assertThat(tokenGenerator.hash(generated.rawToken())).isEqualTo(generated.tokenHash());
    assertThat(generated.tokenHash()).isNotEqualTo(generated.rawToken());
  }

  @Test
  void rawTokenIsUrlSafeAndUnpadded() {
    TokenGenerator.GeneratedToken generated = tokenGenerator.generate();

    assertThat(generated.rawToken()).doesNotContain("+", "/", "=");
  }
}
