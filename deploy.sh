#!/bin/sh
# Mavens distribution management is cool but I want to have a custom pom.xml which greatly complicates things
rm -rf repository/com/typesafe/path-hole/1.0/
mkdir -p repository/com/typesafe/path-hole/1.0/
cp core/target/path-hole-1.0.jar repository/com/typesafe/path-hole/1.0/path-hole-1.0.jar
openssl sha1 repository/com/typesafe/path-hole/1.0/path-hole-1.0.jar | awk '{print $2}' > repository/com/typesafe/path-hole/1.0/path-hole-1.0.jar.sha1
cp deploy.pom repository/com/typesafe/path-hole/1.0/path-hole-1.0.pom
openssl sha1 repository/com/typesafe/path-hole/1.0/path-hole-1.0.jar | awk '{print $2}' >  repository/com/typesafe/path-hole/1.0/path-hole-1.0.pom.sha1
