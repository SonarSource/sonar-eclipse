/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2020 SonarSource SA
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.ui.internal.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.sonarlint.eclipse.core.internal.preferences.RuleConfig;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RulesConfigurationPartTest {

  private static final RuleKey ACTIVE = mockRuleKey("ACTIVE");
  private static final RuleKey ACTIVE_EXCLUDED = mockRuleKey("ACTIVE_EXCLUDED");
  private static final RuleKey INACTIVE = mockRuleKey("INACTIVE");
  private static final RuleKey INACTIVE_INCLUDED = mockRuleKey("INACTIVE_INCLUDED");

  private RulesConfigurationPart newSampleConfigurationPart() {
    Collection<StandaloneRuleDetails> allRuleDetails = Arrays.asList(
      mockRuleDetails(ACTIVE, true),
      mockRuleDetails(ACTIVE_EXCLUDED, true),
      mockRuleDetails(INACTIVE, false),
      mockRuleDetails(INACTIVE_INCLUDED, false));

    Collection<RuleKey> excluded = Arrays.asList(ACTIVE_EXCLUDED, INACTIVE);
    Collection<RuleKey> included = Arrays.asList(INACTIVE_INCLUDED, ACTIVE);

    Map<String, String> languages = new HashMap<>();
    languages.put("java", "Java");
    return new RulesConfigurationPart(languages, allRuleDetails, excluded, included);
  }

  private static RuleKey mockRuleKey(String key) {
    return new RuleKey("squid", key);
  }

  private StandaloneRuleDetails mockRuleDetails(RuleKey ruleKey, boolean activeByDefault) {
    StandaloneRuleDetails ruleDetails = mock(StandaloneRuleDetails.class);
    when(ruleDetails.getKey()).thenReturn(ruleKey.toString());
    when(ruleDetails.isActiveByDefault()).thenReturn(activeByDefault);
    when(ruleDetails.getLanguageKey()).thenReturn("java");
    return ruleDetails;
  }

  @Test
  public void merges_exclusions_correctly() {
    RulesConfigurationPart underTest = newSampleConfigurationPart();

    Collection<RuleConfig> rules = underTest.computeRulesConfig();
    assertThat(rules).extracting(RuleConfig::getKey, RuleConfig::isActive).containsOnly(tuple(ACTIVE_EXCLUDED.toString(), false), tuple(INACTIVE_INCLUDED.toString(), true));
  }

  @Test
  public void reset_to_defaults_clears_exclusions_and_inclusions() {
    RulesConfigurationPart underTest = newSampleConfigurationPart();

    underTest.resetToDefaults();

    Collection<RuleConfig> rules = underTest.computeRulesConfig();
    assertThat(rules).isEmpty();
  }
}
