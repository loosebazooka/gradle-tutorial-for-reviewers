package com.tutorial;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class TaskX extends DefaultTask {

  private String message;
  private File outputDir;

  @OutputDirectory
  public File getOutputDir() {
    return outputDir;
  }

  public void setOutputDir(File outputDir) {
    this.outputDir = outputDir;
  }

  @Input
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @TaskAction
  public void doSomething() throws FileNotFoundException, UnsupportedEncodingException {
    getProject().getLogger().lifecycle("I'm task X and my message is : " + message);

    File outputFile = new File(outputDir, "outputFile.txt");
    try (PrintWriter writer = new PrintWriter(outputFile, "UTF-8")) {
      writer.println("I'm task X and I'm writing to file : " + message);
    }
  }
}
