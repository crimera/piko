include("dummy")

rootProject.name = "piko-twitter-patches"

buildCache {
    local {
        isEnabled = !System.getenv().containsKey("CI")
    }
}
