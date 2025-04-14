#!/usr/bin/env python3
"""
Debug Image Downloader

This script connects to the Chinese Checkers detection server and downloads 
all available debug images to a local directory.
"""

import os
import requests
import argparse
import time
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def ensure_download_dir(directory):
    """Ensure download directory exists"""
    if not os.path.exists(directory):
        os.makedirs(directory)
        logger.info(f"Created download directory: {directory}")
    return directory

def download_debug_images(server_url, download_dir):
    """Download all debug images from the server"""
    try:
        # First, get the list of available images
        list_url = f"{server_url}/list_debug_images"
        logger.info(f"Requesting image list from {list_url}")
        
        response = requests.get(list_url, timeout=10)
        if response.status_code != 200:
            logger.error(f"Failed to retrieve image list: {response.text}")
            return False
        
        image_data = response.json()
        if 'error' in image_data:
            logger.error(f"Error from server: {image_data['error']}")
            return False
        
        image_count = image_data.get('count', 0)
        if image_count == 0:
            logger.info("No debug images available on the server")
            return True
        
        logger.info(f"Found {image_count} debug images")
        
        # Download each image
        for i, image_name in enumerate(image_data.get('images', [])):
            download_url = f"{server_url}/download_debug_image/{image_name}"
            save_path = os.path.join(download_dir, image_name)
            
            logger.info(f"Downloading [{i+1}/{image_count}]: {image_name}")
            
            image_response = requests.get(download_url, timeout=30)
            if image_response.status_code != 200:
                logger.warning(f"Failed to download {image_name}: {image_response.text}")
                continue
            
            with open(save_path, 'wb') as f:
                f.write(image_response.content)
                
            logger.info(f"Saved to {save_path}")
            
            # Small delay to avoid overwhelming the server
            time.sleep(0.2)
        
        logger.info(f"Download complete. {image_count} images saved to {download_dir}")
        return True
    
    except requests.exceptions.RequestException as e:
        logger.error(f"Network error: {str(e)}")
        return False
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Download debug images from Chinese Checkers server")
    parser.add_argument('--server', default='https://chinesecheckerrobot-detection.onrender.com',
                        help='Server URL (default: https://chinesecheckerrobot-detection.onrender.com)')
    parser.add_argument('--output', default='./downloaded_debug_images',
                        help='Directory to save downloaded images (default: ./downloaded_debug_images)')
    
    args = parser.parse_args()
    
    # Ensure trailing slash is removed from server URL
    server_url = args.server.rstrip('/')
    
    # Ensure output directory exists
    output_dir = ensure_download_dir(args.output)
    
    logger.info(f"Connecting to server: {server_url}")
    logger.info(f"Downloading images to: {output_dir}")
    
    download_debug_images(server_url, output_dir)

if __name__ == '__main__':
    main()