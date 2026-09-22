#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?Set DEPS_TOKEN with Contents read access to TF-Minecraft/ServerAssets}"
ref=d19dc36f8cdd66a71fc718df6c1ea881998b7e57
mkdir -p libs
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/0116d714822b/ItemsAdder_3.5.0-r2.jar?ref=$ref" > "libs/ItemsAdder_3.5.0-r2.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/14850d745437/MMOCore-1.13.1.jar?ref=$ref" > "libs/MMOCore-1.13.1.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/c84700df5942/MMOItems-6.10.jar?ref=$ref" > "libs/MMOItems-6.10.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/runtime/plugins/MythicLib.jar?ref=$ref" > "libs/MythicLib-dist-1.7.1-20260819.194904-107.jar"
bash .github/scripts/install-local-dependencies.sh "$@"
