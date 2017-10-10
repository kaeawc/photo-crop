#!/usr/bin/env bash

mkdir -p ${ANDROID_HOME}/licenses || true
rm ${ANDROID_HOME}/licenses/* || true
cp ./licenses/* ${ANDROID_HOME}/licenses/
