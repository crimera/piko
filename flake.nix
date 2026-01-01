{
  description = "Android development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, android-nixpkgs }:
    let
      allSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];

      forAllSystems = f: nixpkgs.lib.genAttrs allSystems (system: f {
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };
        androidSdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-34-0-0
          platform-tools
          platforms-android-34
          emulator
        ]);
      });
    in
    {
      devShells = forAllSystems ({ pkgs, androidSdk }:
        let
          build = pkgs.writeShellScriptBin "build" ''
            ./gradlew patches:apiDump; ./gradlew patches:build "$@"
          '';
          build-android = pkgs.writeShellScriptBin "build-android" ''
            ./gradlew patches:apiDump; ./gradlew patches:buildAndroid "$@"
          '';
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              jdk17
              androidSdk
              build
              build-android

              frida-tools
              jadx
              apktool
            ];

            ANDROID_HOME = "${androidSdk}/share/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk}/share/android-sdk";
            JAVA_HOME = pkgs.jdk17.home;
          };
        });
    };
}

