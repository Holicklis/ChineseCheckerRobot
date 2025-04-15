import requests
import base64
import json
import os
import logging

logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def encode_image(image_path):
    """Encode image file to base64 with error handling"""
    try:
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image file not found: {image_path}")
            
        with open(image_path, 'rb') as f:
            image_bytes = f.read()
            if not image_bytes:
                raise ValueError(f"Image file is empty: {image_path}")
                
            base64_image = base64.b64encode(image_bytes).decode('utf-8')
            logger.debug(f"Successfully encoded image: {image_path}")
            return base64_image
    except Exception as e:
        logger.error(f"Error encoding image {image_path}: {str(e)}")
        raise

def test_server(base_url='https://chinesecheckerrobot-detection-f78q.onrender.com'):
    try:
        # Test empty board upload
        logger.info("Testing empty board upload...")
        empty_board_path = 'received_empty_board.jpg'
        
        try:
            base64_image = encode_image(empty_board_path)
        except Exception as e:
            logger.error(f"Failed to encode empty board image: {str(e)}")
            return
            
        response = requests.post(
            f'{base_url}/upload_empty_board',
            json={'image': base64_image},
            timeout=30  # Add timeout
        )
        
        logger.info(f"Empty board upload - Status Code: {response.status_code}")
        logger.info(f"Empty board upload - Response: {response.json()}")
        
        if response.status_code != 200:
            logger.error("Empty board upload failed. Skipping current board detection.")
            return
            
        # Test current board detection
        logger.info("\nTesting current board detection...")
        current_board_path = 'received_current_board.jpg'
        
        try:
            base64_image = encode_image(current_board_path)
        except Exception as e:
            logger.error(f"Failed to encode current board image: {str(e)}")
            return
            
        response = requests.post(
            f'{base_url}/detect_current_state',
            json={'image': base64_image},
            timeout=30  # Add timeout
        )
        
        logger.info(f"Current board detection - Status Code: {response.status_code}")
        logger.info(f"Current board detection - Response: {response.json()}")
        
    except requests.exceptions.RequestException as e:
        logger.error(f"Network error: {str(e)}")
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}")

if __name__ == '__main__':
    # Make sure we're using the same port as the server
    test_server('https://chinesecheckerrobot-detection-f78q.onrender.com')