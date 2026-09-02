package com.foreignerwarsaw.reference.country;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "what reference-relevant groups does this country belong to as of this date" - nothing
 * more. This is <b>not</b> the Phase 6 rules engine (brief §69): it never returns an immigration
 * recommendation, only a classification fact ("Germany belongs to EU_MEMBER/EEA/SCHENGEN today"),
 * which a future rule condition consumes as one input among several (purpose, employment, family
 * circumstances, ...).
 *
 * <p><b>Nothing in this class is a universal legal definition of "third-country national."</b>
 * Different EU/Polish legal instruments define that term differently, particularly regarding people
 * who enjoy equivalent free-movement rights without being EU/EEA/EFTA nationals in the strict sense
 * (e.g. Swiss nationals under the 1999 EU-Swiss bilateral agreement, or UK nationals retaining
 * rights under the Withdrawal Agreement despite the UK no longer being an EU member). {@link
 * #isOutsideEuEeaSwissFreeMovementGroup} is a narrow, honestly-named structural fact about
 * <i>country group membership only</i> - it deliberately does not decide anyone's legal status.
 * ASSESSMENT_DECISION_TREE.md's eventual {@code THIRD_COUNTRY_NATIONAL} classification (Step 1) is
 * a <i>different</i>, person-level, procedure-specific concept a future phase computes from this
 * fact <b>plus</b> other inputs (a Withdrawal-Agreement case flag, a stateless flag, etc.) - never
 * from this method alone. Which legal definition of "third-country national" applies to a specific
 * procedure is a Phase 6 rules-engine decision, not something this class should ever decide
 * unilaterally for every future caller.
 */
@Service
public class CountryClassificationService {

  /**
   * The three {@code LEGAL}-typed groups (V9/V10) whose membership takes a country outside the
   * EU/EEA/Swiss free-movement framework when absent - deliberately excludes {@code SCHENGEN}
   * (border-control cooperation, not a free-movement/residence-rights framework - brief's "keep
   * Schengen independent" instruction) and the {@code CONVENIENCE}-typed {@code EU_EEA_SWISS}
   * aggregate (which is itself defined in terms of these three groups plus Switzerland's bilateral
   * agreement - checking it directly here would be circular).
   */
  private static final Set<String> EU_EEA_SWISS_FREE_MOVEMENT_GROUPS =
      Set.of("EU_MEMBER", "EEA", "EFTA");

  private final CountryRepository countryRepository;
  private final CountryGroupMembershipRepository membershipRepository;

  public CountryClassificationService(
      CountryRepository countryRepository, CountryGroupMembershipRepository membershipRepository) {
    this.countryRepository = countryRepository;
    this.membershipRepository = membershipRepository;
  }

  /**
   * Every group the country belonged to on {@code evaluationDate} (brief §24/§42) - memberships
   * legitimately overlap (e.g. a country is both EEA and SCHENGEN), by design; this returns all of
   * them, not a single "the" classification.
   */
  @Transactional(readOnly = true)
  public List<String> classificationsFor(String countryCode, LocalDate evaluationDate) {
    Country country = requireCountry(countryCode);
    return membershipRepository
        .findActiveMembershipsForCountry(country.getId(), evaluationDate)
        .stream()
        .map(m -> m.getCountryGroup().getCode())
        .toList();
  }

  /**
   * Whether {@code countryCode} was a member of {@code groupCode} on {@code evaluationDate} - the
   * building block a future rule condition calls (brief §41's exact example: {@code isMember("DE",
   * "EU", evaluationDate)}).
   */
  @Transactional(readOnly = true)
  public boolean isMember(String countryCode, String groupCode, LocalDate evaluationDate) {
    return classificationsFor(countryCode, evaluationDate).contains(groupCode);
  }

  /**
   * Derived, never stored (ADR-006): true iff the country has no active membership in {@code
   * EU_MEMBER}, {@code EEA}, or {@code EFTA} as of {@code evaluationDate} (Switzerland is covered
   * via its {@code EFTA} membership - no separate check needed; {@code EEA != EFTA} - Iceland,
   * Liechtenstein, and Norway hold both, Switzerland holds only {@code EFTA}).
   *
   * <p><b>This is a structural fact about country-group membership, not a legal
   * "third-country-national" determination</b> - see this class's Javadoc. Renamed deliberately
   * (was {@code isThirdCountry}) so its narrow scope can't be misread as a universal legal
   * definition every future rule automatically trusts: a caller that actually needs "is this person
   * a third-country national for procedure X" must apply procedure X's specific legal definition in
   * Phase 6, using this method's result as one input among several - never this method's result
   * standing in for that determination on its own.
   */
  @Transactional(readOnly = true)
  public boolean isOutsideEuEeaSwissFreeMovementGroup(
      String countryCode, LocalDate evaluationDate) {
    Set<String> classifications = Set.copyOf(classificationsFor(countryCode, evaluationDate));
    return classifications.stream().noneMatch(EU_EEA_SWISS_FREE_MOVEMENT_GROUPS::contains);
  }

  private Country requireCountry(String countryCode) {
    return countryRepository
        .findByCodeIgnoreCase(countryCode)
        .orElseThrow(() -> new CountryNotFoundException(countryCode));
  }
}
