/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package vifim.repairer

import org.gradle.api.Project
import org.gradle.api.Plugin
import vifim.repairer.Recipe.*

/**
 * A simple 'hello world' plugin.
 */
class RepairerPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Register a task
        project.tasks.register("greeting") {
            doLast {
                println("Hello from plugin 'repairer.greeting'")
            }
        }
        project.tasks.register("repairer") {
            doLast {
                // SayHelloRecipe().apply { setFullyQualifiedClassName("vifim.repairer.test")}
                SayHelloRecipe shr = new SayHelloRecipe();
                // shr.apply{ setFullyQualifiedClassName("vifim.repairer.test")}
            }
        }
    }
}