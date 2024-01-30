# 👋🧩 ReVanced Patches template

This is a template for creating a new ReVanced Patches repository.  
The repository can have multiple patches, and patches from other repositories can be used together.

For an example repository, see [ReVanced Patches](https://github.com/revanced/revanced-patches).

##  🚀 Get started

To start using this template, follow these steps:

1. [Create a new repository using this template](https://github.com/new?template_name=revanced-patches-template&template_owner=ReVanced)
2. Set up the [build.gradle.kts](build.gradle.kts) file (Match the [group of the project](build.gradle.kts#L8), [manifest attributes](build.gradle.kts#L35-L43), and the [POM](build.gradle.kts#L84-L106) that will be published to yours)
3. Update the dependencies in the [libs.versions.toml](gradle/libs.versions.toml) file
4. [Add a secret](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) to your repository named [REPOSITORY_PUSH_ACCESS](.github/workflows/release.yml#L47) containing a GitHub access token with [push access](https://github.com/semantic-release/semantic-release/blob/master/docs/usage/ci-configuration.md#authentication)
5. Set up the [README.md](README.md) file[^1] (e.g, title, description, license, summary of the patches that are included in the repository)

🎉 You are now ready to start creating patches!

## 🔘 Optional steps

You can also add the following things to the repository:

- [Issue templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository)[^2]
- Contribution guidelines[^3]
- Documentation, if you want to publish your patches as a library[^4]

[^1]: [Example README.md file](https://github.com/ReVanced/revanced-patches/blob/main/README.md)
[^2]: [Example issue templates](https://github.com/ReVanced/revanced-patches/tree/main/.github/ISSUE_TEMPLATE)
[^3]: [Example contribution guidelines](https://github.com/ReVanced/revanced-patches/blob/main/CONTRIBUTING.md)
[^4]: [Example documentation](https://github.com/ReVanced/revanced-patches/tree/docs/docs)

## 🧑‍💻 Usage

In order to develop and release ReVanced Patches using this template, some things need to be considered:

- Development originates in feature branches. Once a feature branch is ready, it is squshed and merged into the `dev` branch
- The `dev` branch is merged into the `main` branch once it is ready for release
- Semantic versioning is used for versioning ReVanced Patches. ReVanced Patches have a public API for other patches to use
- Semantic commit messages are used for commits
- Commits on the `dev` branch and `main` branch are automatically released via the [release.yml](.github/workflows/release.yml) workflow, which is also responsible for generating the changelog and updating the version of ReVanced Patches. It is triggered by pushing to the `dev` or `main` branch. The workflow uses the `publish` task to publish the release of ReVanced Patches
- In order to build ReVanced Patches, that can be used on Android, the [`generateBundle`](build.gradle.kts#L48-L70) task needs to be run. The [`publish` task depends on the `generateBundle`](build.gradle.kts#L74-L76) task, so it will be run automatically when publishing a release.
