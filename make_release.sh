#!/bin/bash

set -eu

#
# This script creates release branch and rolls version in trunk.
#

PROGRAM_NAME=$0

usage() {
    printf "usage: %s --new-dev-version=[version]\n" "$PROGRAM_NAME"
    printf "new-dev-version: Next version which will be updated to poms in trunk after release branch is created\n"
    exit 1
}

check_parameters() {
  if [ -z ${NEW_VERSION+x} ]; then usage; fi
}

for i in "$@"
do
case $i in
    --new-dev-version=*)
    NEW_VERSION="${i#*=}"
    shift
    ;;
    *)
    ;;
esac
done

check_parameters

git fetch --all --tags --prune
git checkout trunk
git pull --rebase

CURRENT_VERSION=$(grep -oPm1 "(?<=<version>)[^<]+" pom.xml)

git checkout -b release/$CURRENT_VERSION

while true; do
    read -rp "Do you want to push new release branch(release/$CURRENT_VERSION) to remote?" yn
    case $yn in
        [Yy]* ) git push --set-upstream origin "release/$CURRENT_VERSION"; break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done

git checkout trunk

find . -name "pom.xml" -exec sed -i "s|<version>$CURRENT_VERSION</version>|<version>$NEW_VERSION</version>|" {} \;

git commit -a -m "Rolled version for new development version"

while true; do
    read -p "Do you want to push new version($NEW_VERSION) to remote?" yn
    case $yn in
        [Yy]* ) git push; break;;
        [Nn]* ) break;;
        * ) echo "Please answer yes or no.";;
    esac
done
