#!/bin/bash

# Define source and destination directories
src="node_modules/@babylonjs/havok/lib/esm/HavokPhysics.wasm"
dst="resources/public/js/compiled/"

# Check if destination directory exists and if not, create it
mkdir -p $dst

# Copy file
cp $src $dst

echo "HavokPhysics.wasm has been successfully copied to the destination directory.\n"
