#!/usr/bin/python

import sys
import os
import commands

print "<INTERCEPTED ARGS>"
print sys.argv[1:]
print "</INTERCEPTED ARGS>"

print "<DRIVER JOBS>"
driverJobs = "swiftc " + " ".join(sys.argv[1:]) + " -Onone -whole-module-optimization -driver-print-jobs"
jobs = commands.getstatusoutput(driverJobs)[1]
jobs = jobs.split(" ")
del jobs[0]
del jobs[0]
removeArgIndex = jobs.index('-supplementary-output-file-map')
jobs = jobs[0:removeArgIndex] + jobs[removeArgIndex + 2:]
jobs.insert(0, "-emit-silgen")
jobs.insert(0, "")
print jobs
print "</DRIVER JOBS>"

print "<SWAN>"
SWAN_path = os.environ['PATH_TO_SWAN']
SWAN_cmd = "/." + SWAN_path + "/gradlew run -p " + SWAN_path + "/" + " --args=\'" + "iOS " + " ".join(jobs) + "\'"
print "running " + SWAN_cmd
os.system(SWAN_cmd)
print "</SWAN>"