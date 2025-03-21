#!/bin/bash

export GITHUB_SHA=$(git rev-parse HEAD)

cd ~/IdeaProjects/prototype-game
rm -rf resources/public/js/compiled
npm run release
rm resources/public/js/compiled/app.js.map

# Wait until app.js exists
while [ ! -f resources/public/js/compiled/app.js ]; do
  sleep 1  # Wait for 1 second before checking again
done

echo 'Done!'
