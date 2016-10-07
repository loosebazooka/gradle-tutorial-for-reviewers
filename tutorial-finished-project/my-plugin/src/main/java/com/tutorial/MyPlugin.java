package com.tutorial;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;

public class MyPlugin implements Plugin<Project> {

  Project project;
  ExtensionX extension;

  @Override
  public void apply(Project project) {
    this.project = project;
    project.getLogger().lifecycle("I'm a plugin hero");

    createExtensionX();
    createTaskX();
  }

  private void createExtensionX() {
    extension = project.getExtensions().create("extensionX", ExtensionX.class);
  }

  private void createTaskX() {
    final TaskX taskX = project.getTasks().create("taskX", TaskX.class);

    project.afterEvaluate(new Action<Project>() {
      @Override
      public void execute(Project project) {
        taskX.setMessage(extension.getMessage());

        taskX.setOutputDir(new File(project.getBuildDir(), "taskX"));
      }
    });
  }
}
