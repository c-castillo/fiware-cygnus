#!/usr/bin/env bash

###############################################################################
#
#  Get all dependencies
#
#  If this is the first time you are running this script,
#  This will take some minutes, so please, go, get a coffee
#
###############################################################################


#/tmp/maven/bin/mvn dependency:copy-dependencies


###############################################################################
#
#  The compilation itself
#  (May require to download more packages)
#
###############################################################################


# /tmp/maven/bin/mvn clean compile exec:exec assembly:single
# cp target/cygnus-0.8.2-jar-with-dependencies.jar /tmp/flume/plugins.d/cygnus/lib


###############################################################################
#
#  Move into the test directory
#
###############################################################################


# cd /cygnusapp/test/acceptance
# mkdir -p /cygnusapp/test/acceptance/settings
# rm /cygnusapp/test/acceptance/settings/*
# cp properties.json.base settings/epgproperties.json


###############################################################################
#
#  Run the tests
#
###############################################################################


# lettuce_tools -ft mysql_row.feature


###############################################################################
#
#  I just want to get inside the machine
#  Uncomment the line below, comment everything else
#
###############################################################################


bash
