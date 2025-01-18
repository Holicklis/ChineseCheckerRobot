#!/bin/bash

# Make sure we're in a Python virtual environment
if [[ -z "${VIRTUAL_ENV}" ]]; then
    echo "No virtual environment detected. Creating one..."
    python3 -m venv env
    source env/bin/activate
fi

# Install required packages
echo "Installing/upgrading required packages..."
pip install --upgrade pip
pip install flask requests opencv-python numpy

# Create debug directory if it doesn't exist
mkdir -p debug_images

# Clear any previous debug files
rm -f debug_images/* debug_*.txt board_state.txt

# Export Flask settings
export FLASK_ENV=development
export FLASK_DEBUG=1
export FLASK_RUN_HOST=0.0.0.0
export FLASK_RUN_PORT=9876

# Start the server
echo "Starting detection server on port 9876..."
python detection_server.py