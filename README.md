# Chinese Checkers Robot

This project implements an autonomous Chinese Checkers playing robot system that combines computer vision, AI, and robotic control to create an interactive gaming experience.

## Project Overview

The system consists of three main components:

1. **Detection Module**: Uses OpenCV to detect the Chinese Checkers board and piece positions
2. **AI Engine**: Implements Minimax algorithm with Alpha-Beta pruning for strategic gameplay
3. **Android App**: Controls the robotic arm and integrates all components

## Repository Structure

- **app/**: Android application source code (Android Studio project)
- **aiServer/**: Python server for AI game logic and move generation (contains requirements.txt)
- **detection_script/**: OpenCV-based board detection scripts (contains requirements.txt)
- **gradle/**: Gradle build system files
- **.idea/**: IntelliJ/Android Studio project settings

## Setup and Installation

### Prerequisites

- Android Studio (latest version)
- Python 3.8+ (for AI server and detection script)
- OpenCV 4.5+ (for detection script)
- Flask (for AI server)
- Required Python packages: numpy, opencv-python, flask

### AI Server Setup

The AI Server can be run locally or deployed to the cloud. For local setup:

1. Navigate to the `aiServer/` directory
2. Install dependencies using the provided requirements.txt:
   ```bash
   pip install -r requirements.txt
   ```
3. Start the server:
   ```bash
   python aiserver.py
   ```

By default, the server is also deployed to: `https://chinesecheckerrobot-zu9g.onrender.com/get_ai_move`

### Detection Server Setup

1. Navigate to the `detection_script/` directory
2. Install dependencies using the provided requirements.txt:
   ```bash
   pip install -r requirements.txt
   ```
3. Start the detection server:
   ```bash
   bash start_server.sh
   ```

The detection server is also deployed to: `https://chinesecheckerrobot-detection-f78q.onrender.com`

### Android App Setup

1. Open the project in Android Studio
2. Configure the project with your SDK location
3. Build and run the app on your device

Note: The Android app is configured to connect to the cloud-hosted servers by default. To use local servers, update the server IP addresses in the app's settings.

## Usage

1. Launch the app on your Android device
2. Configure IP addresses for servers (if using local servers)
3. Capture an empty board image for calibration
4. Detect the current board state
5. Get AI move recommendations
6. Execute moves with the robotic arm

### Auto Play Mode

The app includes an "Auto Play" mode that automatically:
1. Detects the current board state
2. Gets an AI move recommendation
3. Executes the move with the robotic arm

## System Architecture

- The Android app captures images of the Chinese Checkers board via the device camera
- Images are sent to the detection server for board state analysis
- The current board state is sent to the AI server to calculate the next move
- The app controls the robotic arm to execute the moves
- BoardCoordinatesAdapter maps between logical board positions and physical robot coordinates

## License

This project is for educational purposes as part of a Final Year Project.

## Contributors

- LEUNG Ho Ning
- SHIU Chun Nam Alex
