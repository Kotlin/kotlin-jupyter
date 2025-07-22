#!/bin/bash

# Check if the required parameters are passed
if [ "$#" -lt 4 ]; then
    echo "Usage: $0 <username> <password> <artifact_path> <anaconda_user>"
    exit 1
fi

USERNAME="$1"
PASSWORD="$2"
ARTIFACT_PATH="$3"
ANACONDA_USER="$4"

# Log in to Anaconda
echo "Logging in to Anaconda..."
echo "yes" | anaconda login --at anaconda.org --username "$USERNAME" --password "$PASSWORD"
if [ $? -ne 0 ]; then
    echo "Failed to log in to Anaconda"
    exit 2
fi

# Upload the artifact
echo "Uploading artifact to Anaconda..."
anaconda upload -u "$ANACONDA_USER" "$ARTIFACT_PATH"
if [ $? -ne 0 ]; then
    echo "Failed to upload artifact to Anaconda"
    exit 3
fi

echo "Upload complete."
exit 0
