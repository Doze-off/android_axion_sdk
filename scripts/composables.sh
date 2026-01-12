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
    echo "Usage: $0 -v <version>"
    exit 1
fi

URL="https://repo1.maven.org/maven2/com/composables/$ARTIFACT/$VERSION/"

echo "Downloading all files for $ARTIFACT - version $VERSION"
echo "Source: $URL"
echo "-----------------------------------------------------"

wget -r -np -nd -l1 -R "index.html*" -e robots=off "$URL"
