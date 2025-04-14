#!/usr/bin/env python3
"""
Test script for Chinese Checkers jump paths.
Sends a specific board state to test if the AI correctly finds a complete jump path.
"""

import requests
import json
import sys
import time

# Configuration
SERVER_URL = "https://chinesecheckerrobot.onrender.com"  # Change this to your server's URL
# SERVER_URL = "http://localhost:5002"  # Change this to your server's URL
AI_ENDPOINT = "/get_ai_move"

# The board state you provided with X at (0,16) that should jump to (4,10)
TEST_BOARD_STATE = [
    "            . ",
    "           . . ",
    "          . . . ",
    "         . O . O ",
    ". . . . . O O O . . . . . ",
    " . . . . O . . O . . . . ",
    "  . . . . O O O . . . . ",
    "   . . . . . . . . . . ",
    "    . . . . . . . . . ",
    "   . . . . . . . . . . ",
    "  . . . . . . . . . . . ",
    " . . . . X . . . . . . . ",
    ". . . . . X X X . . . . . ",
    "         X . . X ",
    "          . X X ",
    "           X . ",
    "            X "
]
# TEST_BOARD_STATE = [
#     "            . ",
#     "           . . ",
#     "          . . . ",
#     "         . . . . ",
#     ". . . . . . . . . . . . . ",
#     " . . . . . . . . . . . . ",
#     "  . . . . . . . . . . . ",
#     "   . . . . X X . . . . ",
#     "    . . . X X X . . . ",
#     "   . . . . X X . . . . ",
#     "  . . . . X . X . . . . ",
#     " . . . . . . X . . . . . ",
#     ". . . . . O O . . . . . . ",
#     "         O O O . ",
#     "          O O O ",
#     "           O . ",
#     "            O "
# ]

def test_ai_jump_path():
    """Test the AI's ability to find and return complete jump paths"""
    print("Testing AI jump path finding...")
    
    payload = {
        "board_state": TEST_BOARD_STATE,
        "is_player1": False,  # AI plays as player 2 (X)
        "depth": 3,
        "eval_func": 1,
        "use_heuristic": True
    }
    
    try:
        response = requests.post(f"{SERVER_URL}{AI_ENDPOINT}", json=payload)
        
        if response.status_code == 200:
            result = response.json()
            print(f"Response status: {result.get('status')}")
            
            if result.get('status') == 'success':
                move_sequence = result.get('move_sequence', [])
                print(f"Move sequence ({len(move_sequence)} steps):")
                
                # Print the move sequence
                for i, step in enumerate(move_sequence):
                    x, y = step.get('x'), step.get('y')
                    step_name = "Start" if i == 0 else "End" if i == len(move_sequence) - 1 else f"Step {i}"
                    print(f"  {i+1}. {step_name}: ({x}, {y})")
                
                # Check if this is actually a multi-step path (more than just start and end)
                if len(move_sequence) > 2:
                    print("\nSuccess! The AI returned a complete multi-step jump path.")
                    print("This means the fix is working correctly.")
                else:
                    print("\nWarning: The AI only returned start and end points, not a complete path.")
                    print("This suggests the fix may not be properly implemented.")
                
                # Debug file
                debug_file = result.get('debug_file')
                if debug_file:
                    print(f"\nDebug file: {debug_file}")
            
            else:
                print(f"Error message: {result.get('message')}")
                
                if result.get('status') == 'invalid_move':
                    print("\nThe AI is still generating invalid moves.")
                    print("This suggests that either:")
                    print("1. The complete jump path finder isn't working correctly")
                    print("2. There actually isn't a valid jump path from (0,16) to (4,10)")
                    print("3. The validation logic isn't correctly handling jump paths")
                    
                    # Show the attempted path
                    move_sequence = result.get('move_sequence', [])
                    if move_sequence:
                        print("\nAttempted path:")
                        for i, step in enumerate(move_sequence):
                            x, y = step.get('x'), step.get('y')
                            print(f"  {i+1}. ({x}, {y})")
        else:
            print(f"HTTP error: {response.status_code}")
            print(response.text)
    
    except Exception as e:
        print(f"Error: {e}")

def test_manual_jump_path():
    """Test if a specific jump path from (0,16) to (4,10) is valid"""
    print("\nTesting manual jump path...")
    
    # The path you suggested: (0,16) -> (0,14) -> (4,12) -> (4,10)
    manual_path = [
        {"x": 0, "y": 16},
        {"x": 0, "y": 14},
        {"x": 4, "y": 12},
        {"x": 4, "y": 10}
    ]
    
    # Use the analyze_path endpoint to validate this path
    payload = {
        "board_state": TEST_BOARD_STATE,
        "path": [(step["x"], step["y"]) for step in manual_path]
    }
    
    try:
        response = requests.post(f"{SERVER_URL}/analyze_path", json=payload)
        
        if response.status_code == 200:
            result = response.json()
            
            if result.get('status') == 'success':
                all_valid = result.get('all_valid', False)
                analysis = result.get('analysis', [])
                
                print(f"Manual path validation: {'ALL VALID' if all_valid else 'INVALID'}")
                
                # Print analysis of each step
                for i, step in enumerate(analysis):
                    valid = step.get('valid', False)
                    reason = step.get('reason', 'Unknown')
                    from_coords = step.get('from', {})
                    to_coords = step.get('to', {})
                    
                    print(f"  Step {i+1}: ({from_coords.get('x')},{from_coords.get('y')}) -> "
                          f"({to_coords.get('x')},{to_coords.get('y')})")
                    print(f"    Valid: {valid}")
                    print(f"    Reason: {reason}")
                
                if all_valid:
                    print("\nThe manual path is valid!")
                    print("This confirms that (0,16) -> (0,14) -> (4,12) -> (4,10) is a valid jump path.")
                    print("Make sure your AI's path finding can find this path.")
                else:
                    print("\nThe manual path is NOT valid.")
                    print("This suggests there might be issues with your coordinate system or validation logic.")
            else:
                print(f"Validation failed: {result.get('message', 'Unknown error')}")
        else:
            print(f"HTTP error: {response.status_code}")
            print("Note: The /analyze_path endpoint may not exist on your server.")
    
    except Exception as e:
        print(f"Error in manual test: {e}")
        print("Note: Make sure you've added the analyze_path endpoint to your server.")

if __name__ == "__main__":
    print("Chinese Checkers Jump Path Tester")
    print("=================================")
    
    # Check if the server is reachable
    try:
        health_check = requests.get(f"{SERVER_URL}/health")
        if health_check.status_code == 200:
            print("Server is reachable and healthy.")
        else:
            print(f"Server health check failed with status code: {health_check.status_code}")
            sys.exit(1)
    except Exception as e:
        print(f"Cannot reach the server at {SERVER_URL}: {e}")
        print("Please check the server URL and make sure the server is running.")
        sys.exit(1)
    
    # Test the AI's jump path finding
    test_ai_jump_path()
    
    # Test the manual jump path
    test_manual_jump_path()