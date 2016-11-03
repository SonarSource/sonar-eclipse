/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2016 SonarSource SA
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

import org.sonarlint.eclipse.core.internal.markers.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;

public class IssueTrackable implements Trackable {

  private final Issue issue;
  private final TextRange textRange;
  private final Integer textRangeHash;

  public IssueTrackable(Issue issue, TextRange textRange, String textRangeContent) {
    this.issue = issue;
    this.textRange = textRange;
    this.textRangeHash = textRangeContent != null ? textRangeContent.replaceAll("[\\s]", "").hashCode() : null;
  }

  @Override
  public Integer getLine() {
    return issue.getStartLine();
  }

  @Override
  public String getMessage() {
    return issue.getMessage();
  }

  @Override
  public Integer getTextRangeHash() {
    return textRangeHash;
  }

  @Override
  public Integer getLineHash() {
    // TODO implement using same algorithm as used by SonarQube
    return null;
  }

  @Override
  public String getRuleKey() {
    return issue.getRuleKey();
  }

  @Override
  public String getSeverity() {
    return issue.getSeverity();
  }

  @Override
  public TextRange getTextRange() {
    return textRange;
  }

  @Override
  public String getServerIssueKey() {
    return null;
  }

  @Override
  public Long getCreationDate() {
    return null;
  }

  @Override
  public boolean isResolved() {
    return false;
  }

  @Override
  public String getAssignee() {
    return "";
  }
}
