#!/bin/bash -xe

# we need to add jackson as a runtime dependency of the
# flink-dist project so the bash-utils.jar that is run
# to start flink stuff will be able to do json logging
# when it runs. Without having these runtime dependencies,
# we can't run flink and have json logging.

readonly jars="jackson-core jackson-databind jackson-annotations"
readonly version="2.12.1"
# add all of our jars as includes in the assembly file, which puts together
# the dist package using the maven assembly plugin. This will put our
# jackson jars in the dist lib directory so it's available at runtime
# for the bash-java-utils.jar
# This step only builds the xml block we will add to the file
build_assembly_edits() {
    local edit_block=$(cat<<EOF
        <dependencySet>
        <outputDirectory>lib</outputDirectory>
        <unpack>false</unpack>
        <includes>
EOF
)
    for jar in $jars; do
        edit_block="${edit_block}\n<include>com.fasterxml.jackson.core:$jar:jar:$version</include>"
    done
    edit_block="${edit_block}\n</includes>\n</dependencySet>"
    edit_block=${edit_block//$'\n'/\\$'\n'} # escape \n with \\n for sed to work
    echo $edit_block
}

# add dependencies to flink-dist pom.xml so that our jackson
# jars will be downloaded and available during build time
# Without this edit, the assembly-plugin will not add jackson
# jars to the dist package, but the build will succeed with a
# warning.
# This step only builds the xml block we'll need
build_pom_edits() {
    local edit_block=""
    for jar in $jars; do
      new_block=$(cat<<EOF
        <dependency>\n
        <groupId>com.fasterxml.jackson.core</groupId>\n
        <artifactId>$jar</artifactId>\n
        <version>$version</version>\n
        <scope>runtime</scope>\n
        </dependency>
EOF
)
      edit_block="${edit_block}\n${new_block}"
    done
  edit_block=${edit_block//$'\n'/\\$'\n'} # escape \n with \\n for sed to work
  echo $edit_block 
}

# add the built xml blocks to the correct file
# args
# $1 - The xml block to add, generated in one of the build_x functions above
# $2 - the file to add the xml block to
# $3 - the notable string in the file at which to add the edit. We will add the edits in the fashion of $marker\n$edit
patch_file() {
    local edit_block=$1
    local -r file=$2
    local -r edit_marker=$3

    # only replace the first occurance in the file (-z)
    sed -i -z "s|${edit_marker}|${edit_marker}\n${edit_block}|" $file
}

main() {
    assembly_edits=$(build_assembly_edits)
    pom_edits=$(build_pom_edits)

    patch_file "${assembly_edits}" "flink-dist/src/main/assemblies/bin.xml" "<dependencySets>"
    patch_file "${pom_edits}" "flink-dist/pom.xml" "<dependencies>"
}

main
