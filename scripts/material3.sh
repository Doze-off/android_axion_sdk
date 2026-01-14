#!/bin/bash

ARTIFACT=""
VERSION=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -a|--artifact)
            ARTIFACT="$2"
            shift 2
            ;;
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [[ -z "$VERSION" ]]; then
    echo "Usage: $0 -a <artifact> -v <version>"
    exit 1
fi

URL="https://dl.google.com/dl/android/maven2/androidx/compose/material3/$ARTIFACT/$VERSION"

exts=( ".aar" ".pom" ".module" "-sources.jar" "-samples-sources.jar" )
sums=( "" ".md5" ".sha1" ".sha256" ".sha512" )

echo "Downloading Material 3 $ARTIFACT - version $VERSION"

for ext in "${exts[@]}"; do
    for sum in "${sums[@]}"; do
        filename="$ARTIFACT-$VERSION$ext$sum"
        link="$URL/$filename"

        printf "%-55s " "$filename"

        if wget -q --show-progress "$link"; then
            echo ""
        else
            echo "Not found"
        fi
    done
done
