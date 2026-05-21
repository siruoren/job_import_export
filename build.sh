#!/bin/bash
source ~/.zshrc;cd /Users/zhuguojun/Documents/git_project/job_import_export && mvn clean package -Denforcer.skip=true -DskipTests -Dspotbugs.skip=true