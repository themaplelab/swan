#!/usr/bin/python

import sys

f = open("/tmp/SWAN_arguments.txt", "w+");
f.write(" ".join(sys.argv[1:]));
f.close();
