/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.templates;

import static com.android.tools.idea.templates.Template.ATTR_CONSTRAINTS;
import static com.android.tools.idea.templates.Template.ATTR_DESCRIPTION;
import static com.android.tools.idea.templates.Template.ATTR_FORMAT;
import static com.android.tools.idea.templates.Template.ATTR_NAME;
import static com.android.tools.idea.templates.Template.ATTR_VALUE;
import static com.android.tools.idea.templates.Template.CURRENT_FORMAT;
import static com.android.tools.idea.templates.Template.RELATIVE_FILES_FORMAT;
import static com.android.tools.idea.templates.Template.TAG_PARAMETER;
import static com.android.tools.idea.templates.Template.TAG_THUMB;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * An ADT template along with metadata
 */
public class TemplateMetadata {
  /**
   * Constraints that can be applied to template that helps the UI add a
   * validator etc for user input. These are typically combined into a set
   * of constraints via an EnumSet.
   */
  public enum TemplateConstraint {
    ANDROIDX,
    KOTLIN
  }

  public static final String TAG_CATEGORY = "category";
  public static final String TAG_FORM_FACTOR = "formfactor";

  public static final String ATTR_DEPENDENCIES_MULTIMAP = "dependenciesMultimap";

  private static final String ATTR_TEMPLATE_REVISION = "revision";
  private static final String ATTR_MIN_BUILD_API = "minBuildApi";
  private static final String ATTR_MIN_API = "minApi";

  private final Document myDocument;
  private final Map<String, Parameter> myParameterMap;

  private String myFormFactor;
  private String myCategory;
  private final Multimap<Parameter, Parameter> myRelatedParameters;

  TemplateMetadata(@NotNull Document document) {
    myDocument = document;

    NodeList parameters = myDocument.getElementsByTagName(TAG_PARAMETER);
    myParameterMap = new LinkedHashMap<>(parameters.getLength());
    for (int index = 0, max = parameters.getLength(); index < max; index++) {
      Element element = (Element)parameters.item(index);
      Parameter parameter = new Parameter(this, element);
      if (parameter.id != null) {
        myParameterMap.put(parameter.id, parameter);
      }
    }

    NodeList categories = myDocument.getElementsByTagName(TAG_CATEGORY);
    if (categories.getLength() > 0) {
      Element element = (Element)categories.item(0);
      if (element.hasAttribute(ATTR_VALUE)) {
        myCategory = element.getAttribute(ATTR_VALUE);
      }
    }

    NodeList formFactors = myDocument.getElementsByTagName(TAG_FORM_FACTOR);
    if (formFactors.getLength() > 0) {
      Element element = (Element)formFactors.item(0);
      if (element.hasAttribute(ATTR_VALUE)) {
        myFormFactor = element.getAttribute(ATTR_VALUE);
      }
    }
    myRelatedParameters = computeRelatedParameters();
  }

  private Multimap<Parameter, Parameter> computeRelatedParameters() {
    ImmutableMultimap.Builder<Parameter, Parameter> builder = ImmutableMultimap.builder();
    for (Parameter p : myParameterMap.values()) {
      for (Parameter p2 : myParameterMap.values()) {
        if (p == p2) {
          continue;
        }
        if (p.isRelated(p2)) {
          builder.put(p, p2);
        }
      }
    }
    return builder.build();
  }

  @Nullable
  public String getTitle() {
    return getAttrNonEmpty(ATTR_NAME);
  }

  @Nullable
  public String getDescription() {
    return getAttrNonEmpty(ATTR_DESCRIPTION);
  }

  public int getMinSdk() {
    return getInteger(ATTR_MIN_API, 1);
  }

  public int getMinBuildApi() {
    return getInteger(ATTR_MIN_BUILD_API, 1);
  }

  @NotNull
  public EnumSet<TemplateConstraint> getConstraints() {
    String constraintString = myDocument.getDocumentElement().getAttribute(ATTR_CONSTRAINTS);
    if (!isNullOrEmpty(constraintString)) {
      List<TemplateConstraint> constraintsList = new ArrayList<>();
      for (String constraint : Splitter.on('|').omitEmptyStrings().trimResults().split(constraintString)) {
        constraintsList.add(TemplateConstraint.valueOf(constraint.toUpperCase(Locale.US)));
      }
      return EnumSet.copyOf(constraintsList);
    }

    return EnumSet.noneOf(TemplateConstraint.class);
  }

  public int getRevision() {
    return getInteger(ATTR_TEMPLATE_REVISION, 1);
  }

  @Nullable
  public String getCategory() {
    return myCategory;
  }

  @Nullable
  public String getFormFactor() {
    return myFormFactor;
  }

  /**
   * Get the default thumbnail path (the first choice, essentially).
   */
  @Nullable
  public String getThumbnailPath() {
    return getThumbnailPath(null);
  }

  /**
   * Get the active thumbnail path, which requires a callback that takes a parameter ID and returns
   * its value. Using that information, we can select the associated thumbnail path and return it.
   */
  @Nullable
  public String getThumbnailPath(Function<String, Object> produceParameterValue) {
    // Apply selector logic. Pick the thumb first thumb that satisfies the largest number
    // of conditions.
    NodeList thumbs = myDocument.getElementsByTagName(TAG_THUMB);
    if (thumbs.getLength() == 0) {
      return null;
    }

    int bestMatchCount = 0;
    Element bestMatch = null;

    for (int i = 0, n = thumbs.getLength(); i < n; i++) {
      Element thumb = (Element)thumbs.item(i);

      NamedNodeMap attributes = thumb.getAttributes();
      if (bestMatch == null && attributes.getLength() == 0) {
        bestMatch = thumb;
      }
      else if (attributes.getLength() > bestMatchCount) {
        boolean match = true;
        for (int j = 0, max = attributes.getLength(); j < max; j++) {
          Attr attribute = (Attr)attributes.item(j);
          String variableName = attribute.getName();
          String thumbNailValue = attribute.getValue();

          if (produceParameterValue == null || !thumbNailValue.equals(produceParameterValue.apply(variableName))) {
            match = false;
            break;
          }
        }
        if (match) {
          bestMatch = thumb;
          bestMatchCount = attributes.getLength();
        }
      }
    }

    if (bestMatch != null) {
      NodeList children = bestMatch.getChildNodes();
      for (int i = 0, n = children.getLength(); i < n; i++) {
        Node child = children.item(i);
        if (child.getNodeType() == Node.TEXT_NODE) {
          return child.getNodeValue().trim();
        }
      }
    }

    return null;
  }

  public boolean isSupported() {
    String versionString = myDocument.getDocumentElement().getAttribute(ATTR_FORMAT);
    if (versionString != null && !versionString.isEmpty()) {
      try {
        int version = Integer.parseInt(versionString);
        return version <= CURRENT_FORMAT;
      }
      catch (NumberFormatException nfe) {
        return false;
      }
    }

    // Older templates without version specified: supported
    return true;
  }

  public boolean useImplicitRootFolder() {
    String format = myDocument.getDocumentElement().getAttribute(ATTR_FORMAT);
    if (format == null || format.isEmpty()) {
      // If no format is specified, assume this is an old format:
      return true;
    }
    try {
      int version = Integer.parseInt(format);
      return version < RELATIVE_FILES_FORMAT;
    }
    catch (NumberFormatException ignore) {
      // If we cannot parse the format string assume this is an old format:
      return true;
    }
  }

  /**
   * Returns the list of available parameters
   */
  @NotNull
  public Collection<Parameter> getParameters() {
    return myParameterMap.values();
  }

  /**
   * Returns the parameter of the given id, or null if not found
   *
   * @param id the id of the target parameter
   * @return the corresponding parameter, or null if not found
   */
  @Nullable
  public Parameter getParameter(@NotNull String id) {
    return myParameterMap.get(id);
  }

  @Nullable
  private String getAttrNonEmpty(@NotNull String attrName) {
    String attr = myDocument.getDocumentElement().getAttribute(attrName);
    return (attr == null || attr.isEmpty()) ? null : attr;
  }

  private int getInteger(@NotNull String attrName, int defaultValue) {
    try {
      return Integer.parseInt(myDocument.getDocumentElement().getAttribute(attrName));
    }
    catch (NumberFormatException nfe) {
      // Templates aren't allowed to contain codenames, should always be an integer
      //LOG.warn(nfe);
      return defaultValue;
    }
    catch (RuntimeException e) {
      return defaultValue;
    }
  }

  /**
   * Computes a suitable build api string, e.g. for API level 18 the build
   * API string is "18".
   */
  @NotNull
  public static String getBuildApiString(@NotNull AndroidVersion version) {
    return version.isPreview() ? AndroidTargetHash.getPlatformHashString(version) : version.getApiString();
  }

  /**
   * Gets all the params that share a type constraint with the given param,
   */
  @NotNull
  public Collection<Parameter> getRelatedParams(Parameter param) {
    return myRelatedParameters.get(param);
  }
}
