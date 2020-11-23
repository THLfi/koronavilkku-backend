#!/bin/sh

#
# This script generates images from plantuml diagram definitions.
#

cd "$(dirname "$0")" || exit

for pu_file in ./*.pu
do
 echo "Generating image from $pu_file"
 java -Djava.awt.headless=true -jar /opt/plantuml.jar -o ../generated_images $pu_file
done
