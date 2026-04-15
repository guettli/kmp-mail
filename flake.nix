{
  description = "KMP Mail libraries — dev environment";

  inputs = {
    nixpkgs.url     = "github:NixOS/nixpkgs/nixos-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            # Eclipse Temurin 21 — unpatched upstream binary, avoids the NixOS-specific
            # SIGSEGV in oop_access_barrier / JNIHandles::destroy_global that affects all
            # NixOS-packaged OpenJDK 21.x builds (triggered by Kotlin/Native FFI cleanup).
            temurin-bin-21

            # zlib — required by Kotlin/Native's libllvmstubs.so at link time
            zlib

            # Used once: `task init` runs `gradle wrapper` to generate ./gradlew
            # After that, ./gradlew is the primary build tool.
            gradle

            # Taskfile runner  (https://taskfile.dev)
            go-task
          ];

          shellHook = ''
            export JAVA_HOME="${pkgs.temurin-bin-21}"
            export LD_LIBRARY_PATH="${pkgs.zlib}/lib:$LD_LIBRARY_PATH"

            echo "kmp-mail dev shell"
            echo "  java    : $(java -version 2>&1 | head -1)"
            echo "  gradle  : $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'available')"
            echo "  task    : $(task --version 2>/dev/null || echo 'available')"
            echo ""
            if [ ! -f gradlew ]; then
              echo "Run 'task init' to generate the Gradle wrapper."
            fi
          '';
        };
      }
    );
}
