/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2023 SonarSource SA
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
package org.sonarlint.eclipse.core.internal.tracking;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.Flow;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRange;

public interface Trackable {

  @Nullable
  Long getMarkerId();

  void setMarkerId(@Nullable Long id);

  /**
   * The line index, starting with 1. Null means that
   * issue does not relate to a line (file issue for example).
   */
  @Nullable
  Integer getLine();

  @Nullable
  String getMessage();

  @Nullable
  Integer getTextRangeHash();

  @Nullable
  Integer getLineHash();

  String getRuleKey();

  @Nullable
  Long getCreationDate();

  @Nullable
  String getServerIssueKey();

  boolean isResolved();

  /**
   * Can be overriden by server side issue in connected mode
   */
  @Nullable
  IssueSeverity getSeverity();

  /**
   * Original severity reported by the analyzer
   */
  IssueSeverity getRawSeverity();

  /**
   * Can be overriden by server side issue in connected mode
   */
  RuleType getType();

  /**
   * Original type reported by the analyzer
   */
  RuleType getRawType();
  
  /** New CCT clean code attribute / category which cannot be overwritten */
  CleanCodeAttribute getCleanCodeAttribute();
  
  /** New CCT impacts can be overridden by analyzer */
  Map<SoftwareQuality, ImpactSeverity> getImpacts();
  
  /** New CCT impacts as originally specified in the rule */
  Map<SoftwareQuality, ImpactSeverity> getRawImpacts();

  @Nullable
  TextRange getTextRange();

  List<Flow> getFlows();

  List<QuickFix> getQuickFix();

  Optional<String> getRuleDescriptionContextKey();

}
