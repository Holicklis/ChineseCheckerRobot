from flask import Flask, request, jsonify, send_file
import glob
import cv2
import numpy as np
import base64
import logging
import os
import traceback
import detection_no_trackbars as detector  # Import with alias for clarity

app = Flask(__name__)

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - [%(filename)s:%(lineno)d] - %(message)s',
    handlers=[
        logging.StreamHandler()  # Log to stderr/stdout instead of files
    ]
)
logger = logging.getLogger(__name__)

# Global variables to store board state
empty_board_image = None
board_contour = None
empty_cells = None

def ensure_debug_dir():
    """Ensure debug directory exists"""
    debug_dir = 'debug_images'
    if not os.path.exists(debug_dir):
        os.makedirs(debug_dir)
    return debug_dir

def save_debug_image(image, filename):
    """Save image for debugging purposes with error handling"""
    try:
        debug_dir = ensure_debug_dir()
        filepath = os.path.join(debug_dir, filename)
        success = cv2.imwrite(filepath, image)
        if not success:
            logger.error(f"Failed to save image to {filepath}")
        else:
            logger.info(f"Saved debug image to {filepath}")
    except Exception as e:
        logger.error(f"Error saving debug image: {str(e)}")
        logger.debug(traceback.format_exc())

def decode_image(image_data):
    """Decode base64 image data to OpenCV format with enhanced error handling"""
    try:
        # Remove potential data URL prefix
        if "base64," in image_data:
            image_data = image_data.split("base64,")[1]
            
        # Decode base64 image
        image_bytes = base64.b64decode(image_data)
        nparr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if image is None:
            raise ValueError("Failed to decode image data - resulting image is None")
        
        if image.size == 0:
            raise ValueError("Decoded image has zero size")
            
        logger.debug(f"Successfully decoded image with shape: {image.shape}")
        return image
    except Exception as e:
        logger.error(f"Error decoding image: {str(e)}")
        logger.debug(traceback.format_exc())
        raise

@app.route('/upload_empty_board', methods=['POST'])
def upload_empty_board():
    global empty_board_image, board_contour, empty_cells
    
    try:
        # Get image data from request
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({'error': 'No image data received in request'}), 400
            
        image_data = data['image']
        logger.info("Received empty board image data")
        
        # Decode image
        try:
            image = decode_image(image_data)
        except Exception as e:
            return jsonify({'error': f'Failed to decode image: {str(e)}'}), 400
        
        # Save original image for debugging
        save_debug_image(image, 'received_empty_board.jpg')
        
        # Process empty board
        empty_board_image = image.copy()
        try:
            # Using imported detector module
            blurred, hsv = detector.preprocess_image(image)
            save_debug_image(blurred, 'preprocessed_empty_board.jpg')
            
            board_contour = detector.detect_board(blurred.copy())
            if board_contour is None:
                logger.warning("No board contour detected, proceeding anyway...")
                
            # Detect cells
            empty_cells = detector.detect_board_cells(blurred, board_contour)
            if not empty_cells:
                return jsonify({'error': 'No cells detected on the board'}), 400
            
            logger.info(f"Successfully detected {len(empty_cells)} cells")
            
            # Save visualization for debugging
            debug_image = blurred.copy()
            for (x, y) in empty_cells:
                cv2.circle(debug_image, (x, y), 5, (0, 255, 0), -1)
            save_debug_image(debug_image, 'detected_cells.jpg')
            
            return jsonify({
                'message': 'Empty board processed successfully',
                'cells_detected': len(empty_cells)
            })
            
        except Exception as e:
            logger.error(f"Error in image processing: {str(e)}")
            logger.debug(traceback.format_exc())
            return jsonify({'error': f'Image processing failed: {str(e)}'}), 500
            
    except Exception as e:
        logger.error(f"Error processing empty board: {str(e)}")
        logger.debug(traceback.format_exc())
        return jsonify({'error': str(e)}), 500

# @app.route('/detect_current_state', methods=['POST'])
# def detect_current_state():
#     global empty_board_image, board_contour, empty_cells
    
#     try:
#         # Check if we have processed empty board
#         if empty_board_image is None or empty_cells is None:
#             return jsonify({'error': 'Empty board not processed yet'}), 400
            
#         # Get current board image
#         data = request.get_json()
#         if not data or 'image' not in data:
#             return jsonify({'error': 'No image data received in request'}), 400
            
#         image_data = data['image']
        
#         # Decode image
#         try:
#             current_image = decode_image(image_data)
#         except Exception as e:
#             return jsonify({'error': f'Failed to decode image: {str(e)}'}), 400
            
#         save_debug_image(current_image, 'received_current_board.jpg')
        
#         # Process current board state
#         try:
#             # Using imported detector module
#             blurred, hsv = detector.preprocess_image(current_image)
            
#             # APPROACH 1: Use the updated detect_marbles function
#             marbles = detector.detect_marbles(hsv, blurred.copy(), board_contour, debug=True)
            
#             # If no marbles were detected, try direct cell analysis
#             if not marbles:
#                 logger.warning("No marbles detected with standard approach, trying direct cell analysis")
#                 cell_occupancy = detector.detect_cell_occupancy_directly(empty_cells, hsv, debug=True)
#                 if not any(value is not None for value in cell_occupancy.values()):
#                     return jsonify({'error': 'No marbles detected on the board'}), 400
#             else:
#                 # Map marbles to cells 
#                 cell_occupancy = detector.occupancy_by_colour_ratio_nearest_marble(
#                     cells=empty_cells,
#                     marbles=marbles,
#                     hsv_img=hsv,
#                     max_association_distance=40,
#                     ratio_threshold=0.15,
#                     debug=True
#                 )
            
#             # Map cells to board layout
#             populated_layout = detector.assign_cells_to_layout(empty_cells, detector.board_layout)
            
#             # Generate board state visualization
#             visualization = blurred.copy()
#             for (cx, cy), marble_color in cell_occupancy.items():
#                 color = (0, 255, 0) if marble_color == "green" else \
#                        (0, 0, 255) if marble_color == "red" else \
#                        (255, 0, 0)
#                 cv2.circle(visualization, (int(cx), int(cy)), 5, color, -1)
                
#                 # Also draw a larger circle to show the sampling area
#                 # This is useful for debugging the color ratio approach
#                 cv2.circle(visualization, (int(cx), int(cy)), 20, color, 1)
                
#             save_debug_image(visualization, 'board_state.jpg')
            
#             # Generate text representation
#             board_state = []
#             for row in populated_layout:
#                 row_state = []
#                 for cell in row:
#                     if cell is None:
#                         row_state.append('X')  # No cell detected here
#                     else:
#                         occupant = cell_occupancy.get(cell, None)
#                         if occupant is None:
#                             row_state.append('.')  # Empty cell
#                         elif occupant == "green":
#                             row_state.append('G')
#                         elif occupant == "red":
#                             row_state.append('R')
#                         else:
#                             row_state.append('?')
#                 board_state.append(' '.join(row_state))
            
#             # Save board state to file
#             with open('board_state.txt', 'w') as f:
#                 for row in board_state:
#                     f.write(f"{row}\n")
            
#             return jsonify({
#                 'board_state': board_state,
#                 'message': 'Board state detected successfully'
#             })
            
#         except Exception as e:
#             logger.error(f"Error in image processing: {str(e)}")
#             logger.debug(traceback.format_exc())
#             return jsonify({'error': f'Image processing failed: {str(e)}'}), 500
            
#     except Exception as e:
#         logger.error(f"Error detecting current state: {str(e)}")
#         logger.debug(traceback.format_exc())
#         return jsonify({'error': str(e)}), 500
@app.route('/detect_current_state', methods=['POST'])
def detect_current_state():
    global empty_board_image, board_contour, empty_cells
    
    try:
        # Check if we have processed empty board
        if empty_board_image is None or empty_cells is None:
            return jsonify({'error': 'Empty board not processed yet'}), 400
            
        # Get current board image
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({'error': 'No image data received in request'}), 400
            
        image_data = data['image']
        
        # Decode image
        try:
            current_image = decode_image(image_data)
        except Exception as e:
            return jsonify({'error': f'Failed to decode image: {str(e)}'}), 400
            
        save_debug_image(current_image, 'received_current_board.jpg')
        
        # Process current board state
        try:
            # Using imported detector module
            blurred, hsv = detector.preprocess_image(current_image)
            save_debug_image(blurred, 'preprocessed_current_board.jpg')
            
            # Use the improved direct cell occupancy detection
            cell_occupancy = detector.improved_cell_occupancy_detection(empty_cells, hsv, debug=True)
            
            # Check if any marbles were detected
            if not any(val is not None for val in cell_occupancy.values()):
                logger.warning("No marbles detected in the board state")
                
            # Map cells to board layout
            populated_layout = detector.assign_cells_to_layout(empty_cells, detector.board_layout)
            
            # Generate board state visualization
            visualization = blurred.copy()
            for (cx, cy), marble_color in cell_occupancy.items():
                # Draw cell position
                cv2.circle(visualization, (int(cx), int(cy)), 3, (255, 255, 255), -1)
                
                # Draw color-coded circle based on detection
                color = (0, 255, 0) if marble_color == "green" else \
                        (0, 0, 255) if marble_color == "red" else \
                        (255, 0, 0)  # Blue for empty
                
                # Draw a filled circle for the detected marble
                if marble_color is not None:
                    cv2.circle(visualization, (int(cx), int(cy)), 10, color, -1)
                
                # Draw the sampling circle used for detection
                cv2.circle(visualization, (int(cx), int(cy)), 20, color, 1)
                
            save_debug_image(visualization, 'board_state.jpg')
            
            # Generate text representation
            board_state = []
            for row in populated_layout:
                row_state = []
                for cell in row:
                    if cell is None:
                        row_state.append('X')  # No cell detected here
                    else:
                        # Fix: Ensure we're using the tuple as the key
                        # This is the key fix - make sure cell is properly used as a key
                        occupant = cell_occupancy.get(tuple(cell), None)
                        if occupant is None:
                            row_state.append('.')  # Empty cell
                        elif occupant == "green":
                            row_state.append('G')
                        elif occupant == "red":
                            row_state.append('R')
                        else:
                            row_state.append('?')
                board_state.append(' '.join(row_state))
            
            # Save board state to file
            with open('board_state.txt', 'w') as f:
                for row in board_state:
                    f.write(f"{row}\n")
            logger.info(jsonify({
                'board_state': board_state,
                'message': 'Board state detected successfully'
            }))
            return jsonify({
                'board_state': board_state,
                'message': 'Board state detected successfully'
            })
            
        except Exception as e:
            logger.error(f"Error in image processing: {str(e)}")
            logger.debug(traceback.format_exc())
            return jsonify({'error': f'Image processing failed: {str(e)}'}), 500
            
    except Exception as e:
        logger.error(f"Error detecting current state: {str(e)}")
        logger.debug(traceback.format_exc())
        return jsonify({'error': str(e)}), 500
    
@app.route('/list_debug_images', methods=['GET'])
def list_debug_images():
    """List all available debug images"""
    try:
        debug_dir = ensure_debug_dir()
        # Get all image files in the debug directory
        image_files = glob.glob(os.path.join(debug_dir, '*.jpg')) + \
                      glob.glob(os.path.join(debug_dir, '*.png'))
        
        # Extract just the filenames without path
        image_names = [os.path.basename(file) for file in image_files]
        
        return jsonify({
            'status': 'success',
            'images': image_names,
            'count': len(image_names)
        })
    except Exception as e:
        logger.error(f"Error listing debug images: {str(e)}")
        logger.debug(traceback.format_exc())
        return jsonify({'error': str(e)}), 500

@app.route('/download_debug_image/<filename>', methods=['GET'])
def download_debug_image(filename):
    """Download a specific debug image"""
    try:
        debug_dir = ensure_debug_dir()
        file_path = os.path.join(debug_dir, filename)
        
        # Validate the file exists and is within the debug directory
        if not os.path.exists(file_path) or not os.path.isfile(file_path):
            return jsonify({'error': 'File not found'}), 404
            
        # Check if it's an image file
        if not (filename.lower().endswith('.jpg') or 
                filename.lower().endswith('.jpeg') or 
                filename.lower().endswith('.png')):
            return jsonify({'error': 'Invalid file type'}), 400
            
        return send_file(file_path, as_attachment=True)
    except Exception as e:
        logger.error(f"Error downloading debug image: {str(e)}")
        logger.debug(traceback.format_exc())
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    # Ensure debug directory exists
    ensure_debug_dir()
    
    # Use port 5001 by default
    port = int(os.environ.get('PORT', 5001))
    
    # Output server IP for easier connection
    logger.info(f"Starting server on 0.0.0.0:{port}")
    
    # Start the server
    app.run(host='0.0.0.0', port=port, debug=False)