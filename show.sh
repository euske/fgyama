#!/bin/sh
python tools/graph2gv.py "$@" | dot -Tsvg 
