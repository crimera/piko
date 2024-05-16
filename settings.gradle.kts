include("dummy")

rootProject.name = "piko-twitter-revanced-patches"

buildCache {
    local {
        isEnabled = !System.getenv().containsKey("CI")
    }
}
