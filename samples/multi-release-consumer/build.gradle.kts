plugins { application }

repositories {
    maven { url = uri("../jvm-multi-release/build/staging-repo") }
    mavenCentral()
}

dependencies { implementation("com.eignex:jvm-multi-release-jvm:1.0-SNAPSHOT") }

// Every smoke run consumes the publication just staged by the producer.
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

application {
    mainClass.set("consumer.Main")
    mainModule.set("consumer")
}

for (jdk in listOf(17, 25)) {
    tasks.register<JavaExec>("runOnJava$jdk") {
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(application.mainClass)
        modularity.inferModulePath.set(false)
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdk)) })
        args(if (jdk >= 25) "ffm" else "portable")
    }
    tasks.register<JavaExec>("runModuleOnJava$jdk") {
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set(application.mainClass)
        mainModule.set(application.mainModule)
        modularity.inferModulePath.set(true)
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(jdk)) })
        args(if (jdk >= 25) "ffm" else "portable", "module")
    }
}
