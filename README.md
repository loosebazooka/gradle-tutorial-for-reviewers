# This is real old don't use this.
----------------------------------

# gradle-tutorial-for-reviewers
A simple gradle plugin development tutorial so we can get more team members up
to speed on gradle plugin development.

## Set up a skeleton gradle project
This repository includes a very basic skeleton gradle project. You can just
clone it and start from there

```
gradle-tutorial-for-reviewers
├── my-plugin 
│   ├── build.gradle
│   ├── gradle/ 
│   ├── gradlew
│   └── src/main/ 
│       └── java
│           └── MyPlugin.java
│
├── README.md (this tutorial)
│
└── test-project
    ├── build.gradle
    ├── gradle/
    ├── gradlew
    └── src/main/
        └── java
            └── Main.java
        

```
We'll do our development in `my-plugin` and test our plugin in `test-project`


## A most basic Plugin
Gradle plugins are just classes that extend Plugin<Project>. Most of our
plugins are written directly in Java (for better or worse), not Groovy, so this
code may not look exactly like the Gradle tutorials.

I work in Intellij, and you do too probably, import the project
(my-plugin/build.gradle) into intellij and go edit `MyPlugin.java` to make a simple plugin

**`my-plugin/src/main/java/com/tutorial/MyPlugin.java`**
```java
package com.tutorial;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class MyPlugin implements Plugin<Project> {

  private Project project;

  @Override
  public void apply(Project project) {
    this.project = project;

    project.getLogger().lifecycle("I'm a plugin hero");
  }
}

```
The `apply` function is the entry point to all plugins, so lets just print something
out here when our plugin is applied.

### Building a plugin
Now that we've written a most simple plugin, we need to build it,
use `build` to build+test or `assemble` just to build the jar.

```
> cd my-plugin
> ./gradlew assemble 
```

###Using a Plugin
Okay great, now there's a jar in `my-plugin/build/libs` but how do I even use it?
I usually just publish the jar to my local maven repository and reference it there.

```
> ./gradlew install

FAILED
Task 'install' not found in root project 'my-plugin'.
```
Oh, hang on this doesn't work, modify your `my-plugin/build.gradle` to include the
maven plugin that lets us do this.
```groovy
apply plugin: 'maven'
```
then run install again
```
> ./gradlew install
```

This usually throws it your home directory `/.m2/`, so lets go take a look in there 
(tree is a fun program that you should install to help look at directories)

```
> tree ~/.m2/repository/

~/.m2/repository/com/tutorial/
└── my-plugin
  ├── 1.0
  │   ├── my-plugin-1.0.jar
  │   └── my-plugin-1.0.pom
  └── maven-metadata-local.xml

```
so now that it's there, lets try to use it somewhere, like our conveniently present
`test-project`, edit the build.gradle file to apply our plugin and how to find
it (via the buildscript closure)

**`test-project/build.gradle`**
```groovy
buildscript {
  repositories {
    mavenLocal() // this is the .m2 in your home directory
  }
  dependencies {
    classpath "com.tutorial:my-plugin:1.0"
  }
}

apply plugin: 'com.tutorial.my-plugin'
```
classpath is usually `group:artifact:version`, but where did we ever set these? take a
look at `my-plugin/build.gradle` for `group` (com.tutorial) and `version` (1.0),
`artifact` is autoconfigured by gradle as the project name (my-plugin)

Lets try to build the test project
```
> cd test-project
> ./gradlew assemble

FAILED
Plugin with id 'com.tutorial.my-plugin` not found

```
Plugins are referenced by Id, but we never really added that meta data, so lets go do that.
Create a new file for storing this meta-data (in META-INF)
```
my-plugin/src/main/resources/META-INF/gradle-plugins/com.tutorial.my-plugin.properties
```
and reference our Plugin implementation class

**`com.tutorial.my-plugin.properties`**
```
implementation-class=com.tutorial.MyPlugin
```

Now just run reinstall our plugin into the local maven repository to reflect the
changes
```
> cd my-plugin
> ./gradlew install
```
Lets go back into the test-project directory and try to build again.
```
> cd test-project
> ./gradlew assemble
I'm a plugin hero
:clean
:compileJava UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:jar
:assemble
```
Notice before `clean` our little statement is printed out, congratulations you
wrote a plugin.

## Tasks
Now that we've done some simple plugining, lets get into the meatier parts of plugin
development : Tasks. We use tasks to trigger almost everything in our
plugins, so lets start by writing a simple Task.

Most tasks extend gradle's DefaultTask, which gives us some stuff, likes
access to the "project" object. So lets create a new Java Class in
`my-plugin` that does something simple.

**`my-plugin/src/main/java/com/tutorial/TaskX.java`**
```java
package com.tutorial;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class TaskX extends DefaultTask {

  @TaskAction
  public void doSomething() {
    getProject().getLogger().lifecycle("I'm task X");
  }
}
```

As you can see `@TaskAction` is the task entry point, and we're printing something
there. So how do we make this task accessible to everyone, lets go add it to our 
plugin definition in `MyPlugin.java`

Creating the task is pretty simple, just add a small method that does this to
`MyPlugin` and call it from apply

```java
public void apply(Project project) {
  ...
  ...
  
  createTaskX();
}

private void createTaskX() {
  project.getTasks().create("taskX", TaskX.class);
}
```

and we've created a task that you can use.  Run `./gradlew install` again to update the local
maven repository. And try see updates in the `test-project`

```
> cd test-project
> ./gradlew assemble
I'm a plugin hero
:compileJava UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:jar UP-TO-DATE
:assemble UP-TO-DATE
```

ugh, where's my `taskX`, it's not automatically run... but why is `I'm a plugin hero` printing?
There are multiple phases to a gradle execution, better explained [here](https://docs.gradle.org/current/userguide/build_lifecycle.html).
Long story short, when our plugin is applied, we're executing during the 'configuration' phase and that
occurs no matter what. Our task, `taskX` is only executed conditionally in the 'execution' phase. Now
lets go ahead and trigger that execution by calling the task explicitly

```
> ./gradlew taskX
I'm a plugin hero
:taskX
I'm task X
```
Nice, now we've written a task. Lets try to deal with customizing this task. 

### Customizing Tasks
There are two ways
to do this, via inputs to the task directly (we don't normally do this) and through extensions. We'll
quickly go over directly setting properties on a task and then do extensions (which we use in 
our plugins pretty extensively).

First we need to add some inputs to our task. Lets modify `TaskX.java` to add a `message` parameter
and modify our `@taskAction` to print it out.
```java
package com.tutorial;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class TaskX extends DefaultTask {

  private String message;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @TaskAction
  public void doSomething() {
    getProject().getLogger().lifecycle("I'm task X and my message is : " + message);
  }
}
```
Cool, we don't really need to modify our plugin definition at this point, just 
run `./gradlew install` on `my-plugin` to update.

Lets move back to `test-project` and see what's going on with this task.
```
> ./gradlew taskX

I'm a plugin hero
:taskX
I'm task X and my message is : null
```
Nice, but we need to set a message, so lets go modify `test-project/build.gradle`
to adjust this input. Put this code at the end, (at least after applying our plugin)
so `taskX` is defined and can be modified

```groovy
taskX {
  message = "woop woop"
}
```
and run `taskX` again

```
> ./gradlew taskX

I'm a plugin hero
:taskX
I'm task X and my message is : woop woop
```
Perfect. But how do I make `message` required so that I get a build failure instead
of null. This can be establishing using inputs/outputs described in a little more detail
[here](https://docs.gradle.org/current/userguide/more_about_tasks.html), which also double
as incremental build hints for the system. Anyway, lets do that...

On the "getter" <-- this is important, groovy development puts it on the member definition,
but in java, you gotta put it on the getter, lets put an `@Input` annotation.

```java
import org.gradle.api.tasks.Input;

class TaskX ... {
  ...
  @Input
  public String getMessage() {
    return message;
  }
```

Install my-plugin again `./gradlew install`, and on `test-project/build.gradle` remove
the block configuring `taskX.message` (you can comment it out)
```groovy
// taskX {
//   message = "woop woop"
// }
```

and run `taskX` again on `test-project`
```
> ./gradlew taskX
I'm a plugin hero
:taskX FAILED

* What went wrong:
A problem was found with the configuration of task ':taskX'.
> No value has been specified for property 'message'.
```
Okay, great, it failed because the property wasn't set, but you'll notice our message
`I'm a plugin hero` is still printed out because it occurred during the configuration phase, but 
the failure happened much later (during the execution phase). Lets uncomment that section and try again. 
```
> ./gradlew taskX
I'm a plugin hero
:taskX
I'm task X and my message is : woop woop
```
Success!! We've now configured a task. Unfortunately, that's now how plugins historically
have been exposing task configuration. They use `extensions`, which we'll now get to.

#### Optional Inputs
Sometimes you'll see `@Input` accompanied by `@Optional`, this simply means gradle wont fail
if the value isn't set, so we could have done something like.
```
@Optional
@Input
public void getInput() {
   ...
}
```

### Plugin Extensions
An extension in the simplest form is just a POJO. So lets start there, create a new 
java class to be our extension:

**`my-plugin/src/main/java/com/tutorial/ExtensionX.java`**
``` java
package com.tutorial;

public class ExtensionX {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
```

Lets add this extension to our project, go back to `my-plugin/../MyPlugin.java` and
create a small method to initialize the extension, and call it from
the apply method. We want to save the extension result so we can use it later.

```java
ExtensionX extension;

public void apply(Project project) {
  ...
  ...
  createExtensionX();
  createTaskX();
}

private void createExtensionX() {
  extension = project.getExtensions().create("extensionX", ExtensionX.class);
}
```
We haven't yet attached the extension to the task yet, lets just assign it.
When you create a task, you can configure it at creation time by adding an
`Action` parameter, so we have to modify `createTaskX()` to set the message
from the extension.
```java
import org.gradle.api.Action;

...

public class MyPlugin ... {
  ...
  private void createTaskX() {
    project.getTasks().create("taskX", TaskX.class, new Action<TaskX>() {
      @Override
      public void execute(TaskX taskX) {
        taskX.setMessage(extension.getMessage());
      }
    });
  }
  ...
}

```
So, this seems like it should work, `./gradlew install` to update the repository

Lets try to use it in our test-project, first we want to
remove the earlier task configuration we did in `test-project/build.gradle`,
so **remove** the block
```groovy
taskX {
  message = "woop woop"
}
```
and replace it with
```groovy
extensionX {
  message = "pow wow"
}
```
and try to run `taskX`
```
> ./gradlew taskX
I'm a plugin hero
:taskX FAILED

FAILURE: Build failed with an exception.

* What went wrong:
A problem was found with the configuration of task ':taskX'.
> No value has been specified for property 'message'.

```
This is the same error we saw earlier when the input wasn't configured correctly
on the task, that's because the extension isn't actually setting the task property
correctly. This comes back to the build lifecycle of gradle. When the plugin
is being "applied", the build file hasn't been fully parsed, so the extension value
hasn't been set. When we did
```
taskX.setMessage(extension.getMessage());
```
it was essentially useless, we have to wait till after "configuration" is done so
that the extension is populated. You can use a super weird mechanism called
`conventionMapping` which delays evaluation of everything till it is used. The 
easier way is to just register a callback till after the project is evaluated,
lets just do that, go back and edit `MyPlugin.java` and delay configuration by
using `project.afterEvaluate`. This looks a lot different, I'm sorry you have to rewrite
so much code, sometimes you gotta do what you gotta do.

```java
private void createTaskX() {
  final TaskX taskX = project.getTasks().create("taskX", TaskX.class);

  project.afterEvaluate(new Action<Project>() {
    @Override
    public void execute(Project project) {
      taskX.setMessage(extension.getMessage());
    }
  });
}
```
So run `./gradlew install` again try running `taskX` again on `test-project` with
the updated code.

```
> ./gradlew taskX

I'm a plugin hero
:taskX
I'm task X and my message is : pow wow
```
Hurray, our extension now works. 

### UP-TO-DATE tasks
Sometimes you see a task as "UP-TO-DATE", this is because gradle is checking that the
tasks inputs and outputs haven't changed. More reading [here](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks)
if you want more information.

Anyway, lets just use a simple example to get you familiar with how this works.

A task will not be `UP-TO-DATE` unless it has at least one output. So lets make our
task actually do something. So lets add a new outputDirectory to our Task and write
something in some file we put there. Update `my-plugin/../TaskX.java`

```java

...

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

public class TaskX extends DefaultTask {

  private String mess...
  
  private File outputDir;

  @OutputDirectory
  public File getOutputDir() {
    return outputDir;
  }

  public void setOutputDir(File outputDir) {
    this.outputDir = outputDir;
  }

  @Input
  ...
  
  @TaskAction
  public void doSomething() throws FileNotFoundException, UnsupportedEncodingException {
    getProject().getLo...

    File outputFile = new File(outputDir, "outputFile.txt");
    try (PrintWriter writer = new PrintWriter(outputFile, "UTF-8")) {
      writer.println("I'm task X and I'm writing to file : " + message);
    }
  }
}
```

Install the plugin again `./gradlew install`, and run taskX
```
> ./gradlew taskX

I'm a plugin hero
:taskX FAILED

FAILURE: Build failed with an exception.

* What went wrong:
A problem was found with the configuration of task ':taskX'.
> No value has been specified for property 'outputDir'.

```

Right, cause we never actually set the OutputDirectory (it's required since 
we didn't mark it `@Optional`) so lets go configure that in our plugin
definition `my-plugin/../MyPlugin.java`

```java
private void createTaskX() {
  final TaskX taskX = project.getTasks().create("taskX", TaskX.class);

   project.afterEvaluate(new Action<Project>() {
     @Override
     public void execute(Project project) {
       taskX.setMessage(extension.getMessage());
       taskX.setOutputDir(new File(project.getBuildDir(), "taskX")); // <-- add this
     }
   });
}
```
Why `project.getBuildDir()`? It's just the `build` directory in your gradle project where
all build artifacts go, so for `test-project` it gets set to `test-project/build/taskX`

Okay, we should be good to go now right?

Install the plugin again `./gradlew install`, and run `taskX` on `test-project`

```
> ./gradlew taskX

I'm a plugin hero
:taskX
I'm task X and my message is : pow wow
```

Sweet, lets inspect the `build/taskX` directory

```
> ls build/taskX
outputFile.txt

> cat build/taskX/outputFile.txt
I'm task X and I'm writing to file : pow wow
```
Cool, but we really want to see how this UP-TO-DATE thing works so run `taskX` again.

```
> ./gradlew taskX
I'm a plugin hero
:taskX UP-TO-DATE
```
Nice, it didn't run again, so how do we trigger this? One way is to actually just change where
the directory is pointing like `outputDir = "xyz"`, another way to is to modify the outputs that
were produced.

```
> echo "some more data" >> "build/taskX/outputFile.txt"

> cat build/taskX/outputFile.txt
I'm task X and I'm writing to file : pow wow
some more data

> ./gradlew taskX
I'm a plugin hero
:taskX
I'm task X and my message is : pow wow

> cat build/taskX/outputFile.txt
Install the plugin again `./gradlew install`, and run taskX
```
And that's mostly how UP-TO-DATE works, there more to it described in the reading linked at
the beginning of this section.

##Testing (coming soon)
