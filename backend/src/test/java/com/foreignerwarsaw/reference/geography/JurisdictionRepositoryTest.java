package com.foreignerwarsaw.reference.geography;

import static org.assertj.core.api.Assertions.assertThat;

import com.foreignerwarsaw.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * The self-referencing NATIONAL -> REGIONAL -> MUNICIPAL tree (V14/V15) against the real database -
 * proves the parent chain actually round-trips through Hibernate, not just that the migration's own
 * SQL FKs are self-consistent.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class JurisdictionRepositoryTest {

  @Autowired private JurisdictionRepository jurisdictionRepository;

  @Test
  void poland_isNationalWithNoParentNoRegionNoCity() {
    Jurisdiction poland = jurisdictionRepository.findByCode("PL").orElseThrow();

    assertThat(poland.getJurisdictionType()).isEqualTo(JurisdictionType.NATIONAL);
    assertThat(poland.getParentJurisdiction()).isNull();
    assertThat(poland.getRegion()).isNull();
    assertThat(poland.getCity()).isNull();
  }

  @Test
  void mazowieckie_isRegionalWithPolandAsParentAndARegionButNoCity() {
    Jurisdiction mazowieckie = jurisdictionRepository.findByCode("PL_MAZOWIECKIE").orElseThrow();

    assertThat(mazowieckie.getJurisdictionType()).isEqualTo(JurisdictionType.REGIONAL);
    assertThat(mazowieckie.getParentJurisdiction().getCode()).isEqualTo("PL");
    assertThat(mazowieckie.getRegion().getCode()).isEqualTo("MAZOWIECKIE");
    assertThat(mazowieckie.getCity()).isNull();
  }

  @Test
  void warsaw_isMunicipalWithMazowieckieAsParentAndBothARegionAndACity() {
    Jurisdiction warsaw = jurisdictionRepository.findByCode("PL_MAZOWIECKIE_WARSAW").orElseThrow();

    assertThat(warsaw.getJurisdictionType()).isEqualTo(JurisdictionType.MUNICIPAL);
    assertThat(warsaw.getParentJurisdiction().getCode()).isEqualTo("PL_MAZOWIECKIE");
    // Grandparent is reachable by walking the tree, not by a shortcut FK.
    assertThat(warsaw.getParentJurisdiction().getParentJurisdiction().getCode()).isEqualTo("PL");
    assertThat(warsaw.getRegion().getCode()).isEqualTo("MAZOWIECKIE");
    assertThat(warsaw.getCity().getCode()).isEqualTo("WARSAW");
  }
}
