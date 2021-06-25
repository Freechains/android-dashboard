#!/bin/sh

SRC=../freechains.kt/src/main/kotlin/org/freechains/
DST=app/src/main/java/org/freechains/

cp $SRC/common/Util.kt $DST/common/
cp $SRC/cli/*.kt       $DST/cli/
cp $SRC/host/*.kt      $DST/host/
