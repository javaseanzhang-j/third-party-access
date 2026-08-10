#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: TPIP_MYSQL_PASSWORD=... $0 <seed|cleanup|verify-clean> <manifest.properties>" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
java_home="${TPIP_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
mysql_driver="${TPIP_MYSQL_DRIVER:-$HOME/.m2/repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar}"
build_dir="$script_dir/.build"

if [[ ! -f "$mysql_driver" ]]; then
  echo "MySQL JDBC driver not found: $mysql_driver" >&2
  exit 3
fi

mkdir -p "$build_dir"
"$java_home/bin/javac" -d "$build_dir" "$script_dir/UiGovernanceFixture.java"
"$java_home/bin/java" -cp "$build_dir:$mysql_driver" UiGovernanceFixture "$1" "$2"
