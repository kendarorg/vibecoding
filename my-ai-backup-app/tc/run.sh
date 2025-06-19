#!/bin/sh

chmod +x /test/*
ls -lrt /test
cd /test
mkdir -p /test/dates-backup
mkdir -p /test/sync-backup
mkdir -p /test/mirror-backup
python -m SimpleHTTPServer  8180 &
java -jar /test/sync-server.jar