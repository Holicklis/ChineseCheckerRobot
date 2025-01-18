#!/bin/bash

# Install required packages if not already installed
pip install flask requests opencv-python numpy

# Export Flask development mode
export FLASK_ENV=development
export FLASK_DEBUG=1

# Start the server
python detection_server.py