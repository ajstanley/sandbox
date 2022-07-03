import plugins.DockerComposePlugin
import plugins.DockerComposePlugin.DockerCompose

// Convenience to take everything down regardless of subproject started it.
tasks.register("down") {
    subprojects {
        dependsOn(tasks.named<DockerCompose>("down"))
    }
}

subprojects {
    apply<DockerComposePlugin>()
    afterEvaluate {
        tasks.named<DockerCompose>("up") {
            // Down any other running service before starting another.
            val downTask = tasks.named<DockerCompose>("down")
            dependsOn(parent!!.subprojects.map { it.tasks.named<DockerCompose>("down") }.filter { it != downTask })
        }
        tasks.withType<DockerCompose> {
            val tag = (properties["docker.tags"] as String).split(",").first()
            environment.put("TAG", tag)
        }
    }
}
