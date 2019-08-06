/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class NewModuleWizardFixture extends AbstractWizardFixture<NewModuleWizardFixture> {

  public static NewModuleWizardFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Create New Module"));
    return new NewModuleWizardFixture(ideFrameFixture, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;

  private NewModuleWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    super(NewModuleWizardFixture.class, ideFrameFixture.robot(), dialog);
    myIdeFrameFixture = ideFrameFixture;
  }

  @NotNull
  public NewModuleWizardFixture chooseModuleType(String name) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.replaceCellReader((list, index) -> ((ModuleGalleryEntry)list.getModel().getElementAt(index)).getName());
    listFixture.clickItem(name);
    return this;
  }

  @NotNull
  public NewModuleWizardFixture chooseActivity(String activity) {
    new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class)).clickItem(activity);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> getConfigureAndroidModuleStep() {
    JRootPane rootPane = findStepWithTitle("Configure the new module");
    return new ConfigureAndroidModuleStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureDynamicFeatureStepFixture<NewModuleWizardFixture> clickNextToDynamicFeature() {
    chooseModuleType("Dynamic Feature Module");
    clickNext();
    JRootPane rootPane = findStepWithTitle("Configure your module");
    return new ConfigureDynamicFeatureStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureDynamicFeatureStepFixture<NewModuleWizardFixture> clickNextToInstantDynamicFeature() {
    chooseModuleType(message("android.wizard.module.new.dynamic.module.instant"));
    clickNext();
    JRootPane rootPane = findStepWithTitle("Configure your module");
    return new ConfigureDynamicFeatureStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextToAndroidLibrary() {
    chooseModuleType(message("android.wizard.module.new.library"));
    clickNextToStep(message("android.wizard.module.new.library"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureJavaLibraryStepFixture<NewModuleWizardFixture> clickNextToJavaLibary() {
    chooseModuleType(message("android.wizard.module.new.java.library"));
    clickNextToStep(message("android.wizard.module.config.title"));

    return new ConfigureJavaLibraryStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> clickNextToBenchmarkModule() {
    chooseModuleType(message("android.wizard.module.new.benchmark.module.app"));
    clickNextToStep(message("android.wizard.module.config.title"));
    return new ConfigureAndroidModuleStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public ConfigureNewModuleFromJarStepFixture<NewModuleWizardFixture> clickNextToModuleFromJar() {
    chooseModuleType(message("android.wizard.module.import.title"));
    clickNextToStep(message("android.wizard.module.import.library.title"));
    return new ConfigureNewModuleFromJarStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public NewModuleWizardFixture clickNextToStep(String name) {
    GuiTests.findAndClickButton(this, "Next");
    Wait.seconds(5).expecting("next step to appear").until(
      () -> robot().finder().findAll(target(), JLabelMatcher.withText(name).andShowing()).size() == 1);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickFinish() {
    super.clickFinish(Wait.seconds(5));
    GuiTests.waitForProjectIndexingToFinish(myIdeFrameFixture.getProject());

    return myIdeFrameFixture;
  }
}
