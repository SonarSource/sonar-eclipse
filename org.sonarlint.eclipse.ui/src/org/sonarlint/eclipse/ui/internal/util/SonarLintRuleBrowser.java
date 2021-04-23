/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2021 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.util;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Composite;
import org.sonarlint.eclipse.core.internal.preferences.RuleConfig;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;

public class SonarLintRuleBrowser extends SonarLintWebView {

  private RuleDetails ruleDetails;

  public SonarLintRuleBrowser(Composite parent, boolean useEditorFontSize) {
    super(parent, useEditorFontSize);
  }

  @Override
  protected String body() {
    if (ruleDetails == null) {
      return "<small><em>(No rules selected)</em></small>";
    } else {
      String ruleName = ruleDetails.getName();
      String ruleKey = ruleDetails.getKey();
      String htmlDescription = ruleDetails.getHtmlDescription();
      if (ruleDetails instanceof ConnectedRuleDetails) {
        String extendedDescription = ((ConnectedRuleDetails) ruleDetails).getExtendedDescription();
        if (StringUtils.isNotBlank(extendedDescription)) {
          htmlDescription += "<div>" + extendedDescription + "</div>";
        }
      }
      String ruleDetailsMarkup = "";
      if(ruleDetails instanceof StandaloneRuleDetails) {
    	  ruleDetailsMarkup = renderRuleParams((StandaloneRuleDetails)ruleDetails);
      }
      String type = ruleDetails.getType();
      String typeImg64 = type != null ? getAsBase64(SonarLintImages.getTypeImage(type)) : "";
      String severity = ruleDetails.getSeverity();
      String severityImg64 = getAsBase64(SonarLintImages.getSeverityImage(severity));
      return "<h1><span class=\"rulename\">"
        + escapeHTML(ruleName) + "</span><span class=\"rulekey\"> (" + ruleKey + ")</span></h1>"
        + "<div class=\"typeseverity\">"
        + "<img class=\"typeicon\" alt=\"" + type + "\" src=\"data:image/gif;base64," + typeImg64 + "\">"
        + "<span>" + clean(type) + "</span>"
        + "<img class=\"severityicon\" alt=\"" + severity + "\" src=\"data:image/gif;base64," + severityImg64 + "\">"
        + "<span>" + clean(severity) + "</span>"
        + "</div>"
        + "<div class=\"rule-desc\">" + htmlDescription + "</div>"
        + "<div>" + ruleDetailsMarkup + "</div>";
    }
  }


  private String renderRuleParams(StandaloneRuleDetails ruleDetails) {
    if (!ruleDetails.paramDetails().isEmpty()) {
      return "<table class=\"rule-params\">" +
        "<caption><h2>Parameters</h2></caption>" +
        "<tr class='thead'>" +
        "<td colspan=\"2\">" +
        "Following parameter values can be set in <a href='" + RULES_CONFIGURATION_LINK + "'>Rules Configuration</a>.\n" +
        "</td>" +
        "</tr>" +
        ruleDetails.paramDetails().stream().map(param -> renderRuleParam(param, ruleDetails)).collect(Collectors.joining("\n")) +
        "</table>";
    } else {
      return "";
    }
  }

  private static String renderRuleParam(StandaloneRuleParam param, StandaloneRuleDetails ruleDetails) {
    String paramDescription = param.description() != null ? "<p>" + param.description() + "</p>" : "";
    String paramDefaultValue = param.defaultValue();
    String defaultValue = paramDefaultValue != null ? paramDefaultValue : "(none)";
    String currentValue = getRuleParamValue(ruleDetails.getKey(), param.name()).orElse(defaultValue);
    return "<tr class='tbody'>" +
      // The <br/> elements are added to simulate a "vertical-align: top" (not supported by Java 11 CSS renderer)
      "<th>" + param.name() + "<br/><br/><br/></th>" +
      "<td>" +
      paramDescription +
      "<p><small>Current value: <code>" + currentValue + "</code></small></p>" +
      "<p><small>Default value: <code>" + defaultValue + "</code></small></p>" +
      "</td>" +
      "</tr>";
  }

  private static Optional<String> getRuleParamValue(String ruleKey, String paramName) {
    Collection<RuleConfig> rulesConfig = SonarLintGlobalConfiguration.readRulesConfig();
    Optional<RuleConfig> ruleConfig = rulesConfig.stream()
      .filter(r -> r.getKey().equals(ruleKey))
      .filter(RuleConfig::isActive)
      .filter(r -> r.getParams().keySet().contains(paramName))
      .findFirst();
    if (!ruleConfig.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(ruleConfig.get().getParams().get(paramName));
  }

  public void updateRule(@Nullable RuleDetails ruleDetails) {
    this.ruleDetails = ruleDetails;
    refresh();
  }

  public static String escapeHTML(String s) {
    StringBuilder out = new StringBuilder(Math.max(16, s.length()));
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c > 127 || c == '"' || c == '<' || c == '>' || c == '&') {
        out.append("&#");
        out.append((int) c);
        out.append(';');
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String clean(@Nullable String txt) {
    if (txt == null) {
      return "";
    }
    return StringUtils.capitalize(txt.toLowerCase(Locale.ENGLISH).replace("_", " "));
  }

  private static String getAsBase64(@Nullable Image image) {
    if (image == null) {
      return "";
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageLoader loader = new ImageLoader();
    loader.data = new ImageData[] {image.getImageData()};
    loader.save(out, SWT.IMAGE_PNG);
    return Base64.getEncoder().encodeToString(out.toByteArray());
  }

}
