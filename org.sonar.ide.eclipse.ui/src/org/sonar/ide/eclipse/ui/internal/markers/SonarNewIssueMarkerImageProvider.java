/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.ui.internal.markers;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.texteditor.IAnnotationImageProvider;
import org.sonar.ide.eclipse.ui.internal.SonarImages;

public class SonarNewIssueMarkerImageProvider implements IAnnotationImageProvider {

  @Override
  public ImageDescriptor getImageDescriptor(String imageDescritporId) {
    return null;
  }

  @Override
  public String getImageDescriptorId(Annotation annotation) {
    return null;
  }

  @Override
  public Image getManagedImage(Annotation annotation) {
    return SonarImages.IMG_NEW_ISSUE;
  }

}
