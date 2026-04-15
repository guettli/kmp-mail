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
            # Java host for Kotlin compiler and JVM tests
            jdk21

            # Used once: `task init` runs `gradle wrapper` to generate ./gradlew
            # After that, ./gradlew is the primary build tool.
            gradle

            # Taskfile runner  (https://taskfile.dev)
            go-task
          ];

          shellHook = ''
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
