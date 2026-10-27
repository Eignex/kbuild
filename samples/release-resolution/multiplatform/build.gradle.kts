plugins { id("com.eignex.kmp") }
group = "org.example"
kotlin { jvm() }
eignexPublish {
    description.set("Anonymous multiplatform release resolution probe.")
    githubRepo.set("Eignex/kbuild")
}
