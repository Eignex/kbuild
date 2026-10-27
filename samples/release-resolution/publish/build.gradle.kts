plugins {
    `java-library`
    id("com.eignex.publish")
}
group = "org.example"
eignexPublish {
    description.set("Downstream Central publishing remains available.")
    githubRepo.set("Eignex/kbuild")
}
