import cv2
import numpy as np
import logging

# ---------------------------
# Configure Logging
# ---------------------------
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# ---------------------------
# Preprocessing Functions
# ---------------------------
def automatic_brightness_and_contrast(image, clip_hist_percent=1):
    """
    Automatically adjust brightness and contrast using histogram clipping.
    """
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    
    # cv2.imshow("Gray Image", gray)

    # Calculate grayscale histogram
    hist = cv2.calcHist([gray], [0], None, [256], [0, 256])
    cum_hist = hist.cumsum()
    


    # Calculate thresholds
    total = cum_hist[-1]
    clip_hist_percent *= total / 100.0
    clip_hist_percent /= 2.0

    # Find minimum
    minimum_gray = 0
    while minimum_gray < 256 and cum_hist[minimum_gray] < clip_hist_percent:
        minimum_gray += 1

    # Find maximum
    maximum_gray = 255
    while maximum_gray >= 0 and cum_hist[maximum_gray] >= (total - clip_hist_percent):
        maximum_gray -= 1

    # Avoid division by zero
    if maximum_gray - minimum_gray == 0:
        alpha = 1.0
    else:
        alpha = 255.0 / (maximum_gray - minimum_gray)
    beta = -minimum_gray * alpha

    auto_result = cv2.convertScaleAbs(image, alpha=alpha, beta=beta)
    # cv2.imshow("Auto Brightness/Contrast", auto_result)

    return auto_result

def preprocess_image(image, max_dim=1600, debug=False): #1600 unit: pixel
    """
    Preprocess the provided image data with:
      - Adaptive resizing (max dimension = max_dim)
      - Automatic brightness/contrast
      - CLAHE on V-channel
      - Gaussian blur

    Args:
        image: numpy array of image data (BGR format)
        max_dim: maximum dimension for resizing
        debug: whether to show debug windows

    Returns: (blurred BGR image, HSV image)
    """
    if image is None:
        raise ValueError("Invalid image data provided")
    
    # cv2.imshow("Original Image", image)

    # Resize if larger than max_dim
    height, width = image.shape[:2]
    if max(height, width) > max_dim:
        scale = max_dim / float(max(height, width))
        image = cv2.resize(image, (int(width * scale), int(height * scale)), interpolation=cv2.INTER_AREA)
        logging.info(f"Resized image by factor {scale:.2f}")

    # Auto brightness/contrast
    adjusted = automatic_brightness_and_contrast(image)

    # Convert to HSV
    hsv = cv2.cvtColor(adjusted, cv2.COLOR_BGR2HSV)
    
    # CLAHE on V channel
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    hsv[:,:,2] = clahe.apply(hsv[:,:,2])

    # Convert back to BGR
    enhanced = cv2.cvtColor(hsv, cv2.COLOR_HSV2BGR)
    
    # Blur
    if debug:
        cv2.imshow("Enhanced Image", enhanced)
    blurred = cv2.GaussianBlur(enhanced, (5, 5), 0)
    if debug:
        cv2.imshow("Blurred Image", blurred)
    hsv_blurred = cv2.cvtColor(blurred, cv2.COLOR_BGR2HSV)

    if debug:
        cv2.imshow("Preprocessed Image", blurred)

    return blurred, hsv_blurred

# ---------------------------
# Marble Detection Parameters (Predefined)
# ---------------------------
# Predefined HSV ranges for green marbles
GREEN_LOWER = np.array([36, 40, 40])
GREEN_UPPER = np.array([86, 255, 255])


# Predefined HSV ranges for red marbles (two ranges to cover hue wrap-around)
RED_LOWER1 = np.array([0, 100, 100])
RED_UPPER1 = np.array([10, 255, 255])
RED_LOWER2 = np.array([160, 100, 100])
RED_UPPER2 = np.array([180, 255, 255])

# Predefined Hough Circle Detection Parameters for Cells
HOUGH_DP = 1.1        # Inverse ratio of accumulator resolution
HOUGH_MIN_DIST = 30
HOUGH_PARAM1=500      # Canny high threshold, decrease will detect more edges
HOUGH_PARAM2 = 10   # Accumulator threshold, decrease will detect more circles
HOUGH_MIN_RADIUS = 15
HOUGH_MAX_RADIUS = 25

# Predefined Hough Circle Detection Parameters for Marbles
MARBLE_HOUGH_DP = 1.1
MARBLE_HOUGH_MIN_DIST = 30
MARBLE_HOUGH_PARAM1 = 50   # Canny high threshold
MARBLE_HOUGH_PARAM2 = 15   # Accumulator threshold
MARBLE_HOUGH_MIN_RADIUS = 15
MARBLE_HOUGH_MAX_RADIUS = 30

# Base threshold for marble to cell assignment
BASE_THRESHOLD = 45

# ---------------------------
# Board Detection (Hexagon) - OPTIONAL
# ---------------------------
def detect_board(resized_image, debug=False):
    """
    Attempt to detect the hexagonal outline of the board using contour detection.
    """
    # 1. Convert to HSV (to filter out yellow spacer)
    hsv = cv2.cvtColor(resized_image, cv2.COLOR_BGR2HSV)

    # 2. Define a rough "yellow" range (you may need to tweak these)
    #    You can adjust the hue [20..35], saturation, and value range 
    #    depending on your actual spacer color and lighting.
    yellow_lower = np.array([20, 100, 100])
    yellow_upper = np.array([35, 255, 255])

    # 3. Create a mask for yellow
    yellow_mask = cv2.inRange(hsv, yellow_lower, yellow_upper)

    # 4. Black out the yellow regions in the original image
    #    (where mask is > 0, set those pixels to black)
    masked_image = resized_image.copy()
    masked_image[yellow_mask > 0] = (0, 0, 0)
    
    cv2.imwrite("debug_images/yellow_removed.jpg", masked_image)

    if debug:
        cv2.imwrite("debug_images/yellow_removed.jpg", masked_image)
        cv2.imshow("Yellow Removed", masked_image)

    # 5. Now continue with your usual grayscale, blur, threshold, etc.
    gray = cv2.cvtColor(masked_image, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    
    # For example:
    thresholded = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
        cv2.THRESH_BINARY, 11, 2
    )
    cv2.imwrite('debug_images/thresholded.jpg', thresholded)
    if debug:
        cv2.imshow("Thresholded", thresholded)
    
    # Canny Edge Detection
    edges = cv2.Canny(blurred, 50, 150)
    cv2.imwrite('debug_images/edges.jpg', edges)
    if debug:
        cv2.imshow("Edges", edges)
    
    # Morphological Closing to close gaps in edges
    kernel = np.ones((5, 5), np.uint8)
    closed_edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel)
    cv2.imwrite('debug_images/closed.jpg', closed_edges)
    if debug:
        cv2.imshow("Closed Edges", closed_edges)
    
    # Combined edges - bitwise AND of thresholded and edges (optional)
    combined_edges = cv2.bitwise_and(thresholded, edges)
    cv2.imwrite('debug_images/combined_edges.jpg', combined_edges)
    if debug:
        cv2.imshow("Combined Edges", combined_edges)
    
    # Find Contours on the closed edge image
    contours, _ = cv2.findContours(
        closed_edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
    )
    
    # Draw all found contours for debugging
    debug_contours = resized_image.copy()
    cv2.drawContours(debug_contours, contours, -1, (0, 255, 0), 2)
    cv2.imwrite('debug_images/all_contours.jpg', debug_contours)
    if debug:
        cv2.imshow("All Contours", debug_contours)
    
    board_contour = None
    max_area = 0
    debug_polygons = resized_image.copy()
    
    # Iterate over contours to approximate polygons and find the board
    for contour in contours:
        area = cv2.contourArea(contour)
        min_contour_area = resized_image.shape[0] * resized_image.shape[1] * 0.05
        if area < min_contour_area:
            continue
        
        peri = cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, 0.015 * peri, True)
        
        # Draw approximated polygon for debug purposes
        cv2.drawContours(debug_polygons, [approx], -1, (255, 0, 0), 2)
        
        # Check for a shape that might correspond to a hex board
        if 6 <= len(approx) <= 18 and area > max_area:
            board_contour = contour
            max_area = area

    cv2.imwrite('debug_images/approx_polygons.jpg', debug_polygons)
    if debug:
        cv2.imshow("Approximated Polygons", debug_polygons)
    
    # If a valid board contour is found, draw it on the image
    if board_contour is not None:
        logging.info(f"Board contour area: {max_area}")
        detected_board = resized_image.copy()
        cv2.drawContours(detected_board, [board_contour], -1, (0, 255, 0), 2)
        cv2.imwrite('debug_images/detected_board.jpg', detected_board)
        if debug:
            cv2.imshow("Detected Board", detected_board)
        logging.info("Hexagonal board contour found.")
        with open('debug_info_board.txt', 'a') as f:
            f.write(f"Board contour area: {max_area}\n")
    else:
        logging.warning("Hexagonal board not detected.")
        print("No board detected")
        with open('debug_info_board.txt', 'a') as f:
            f.write("No board detected\n")
    
    return board_contour

# ---------------------------
# Detect Board Cells (Holes) on Empty Board
# ---------------------------
def detect_board_cells(empty_board_image, board_contour, debug=False):
    """
    Detect all the cell (hole) positions on an empty board,
    but preserve only those inside the detected board contour.
    
    Returns:
        List of (x, y) circle centers within the board.
    """
    if debug:
        cv2.imshow("Empty Board", empty_board_image)
        
    
    gray = cv2.cvtColor(empty_board_image, cv2.COLOR_BGR2GRAY)
    
    gray = cv2.medianBlur(gray, 5)
    if debug:
        cv2.imshow("Gray before extra bright", gray)
    gray = cv2.convertScaleAbs(gray, alpha=1.2, beta=0)
    if debug:
        cv2.imshow("Gray for Hough", gray)
    # Perform Hough Circle detection for cells
    circles = cv2.HoughCircles(
        gray,
        cv2.HOUGH_GRADIENT,
        dp=HOUGH_DP,
        minDist=HOUGH_MIN_DIST,
        param1=HOUGH_PARAM1,
        param2=HOUGH_PARAM2,
        minRadius=HOUGH_MIN_RADIUS,
        maxRadius=HOUGH_MAX_RADIUS
    )

    cells = []
    if circles is not None:
        circles = np.int16(np.around(circles[0]))
        for (x, y, r) in circles:
            # Check if circle center is inside the board
            if board_contour is not None:
                inside = cv2.pointPolygonTest(board_contour, (x, y), False)
                if inside < 0:  # -1 means outside the contour
                    continue

            cells.append((int(x), int(y)))
            if debug:
                # Draw the valid circle
                cv2.circle(empty_board_image, (x, y), r, (0, 255, 255), 2)
                cv2.circle(empty_board_image, (x, y), 2, (0, 0, 255), 3)

    if debug:
        cv2.imshow("Detected Board Cells (Filtered)", empty_board_image)
        logging.info(f"[DEBUG] Detected {len(cells)} cells within the board.")

    return cells

# ---------------------------
# Chinese Checker Board Layout
# ---------------------------
board_layout = [
    [None],               # row 0 has space for 1 cell
    [None, None],         # row 1 has space for 2 cells
    [None, None, None],   # row 2 has space for 3 cells
    [None, None, None, None],    # row 3 has space for 4 cells
    [None, None, None, None, None, None, None, None, None, None, None, None, None],  # row 4 has space for 13 cells
    [None, None, None, None, None, None, None, None, None, None, None, None],  # row 5 has space for 12 cells
    [None, None, None, None, None, None, None, None, None, None, None],          # row 6 has space for 11 cells
    [None, None, None, None, None, None, None, None, None, None],                # row 7 has space for 10 cells
    [None, None, None, None, None, None, None, None, None],                      # row 8 has space for 9 cells
    [None, None, None, None, None, None, None, None, None, None],                # row 9 has space for 10 cells
    [None, None, None, None, None, None, None, None, None, None, None],          # row 10 has space for 11 cells
    [None, None, None, None, None, None, None, None, None, None, None, None],    # row 11 has space for 12 cells
    [None, None, None, None, None, None, None, None, None, None, None, None, None],  # row 12 has space for 13 cells
    [None, None, None, None],   # row 13 has space for 4 cells
    [None, None, None],         # row 14 has space for 3 cells
    [None, None],               # row 15 has space for 2 cells
    [None],                     # row 16 has space for 1 cell
]

def group_cells_by_row(cells, row_threshold=15):
    """
    Group cells (x, y) into rows if their y-coordinates 
    are within 'row_threshold' of each other.

    1. Sort by ascending y (then x as a tiebreaker).
    2. Start a new row when the y-difference is bigger than 'row_threshold'.
    3. Sort each row by x ascending.
    4. Return the grouped cells (flattened back into a single list, 
       but now row-by-row).
    """
    if not cells:
        return []

    # 1) Sort by y (then x as a tiebreaker)
    sorted_cells = sorted(cells, key=lambda c: (c[1], c[0]))

    rows = []       # list of lists
    current_row = [sorted_cells[0]]
    prev_y = sorted_cells[0][1]

    # 2) Walk through sorted cells
    for i in range(1, len(sorted_cells)):
        (x, y) = sorted_cells[i]
        # If this cell's y is close enough to the previous cell's y,
        # treat them as the same row
        if abs(y - prev_y) <= row_threshold:
            current_row.append((x, y))
        else:
            # We start a new "row"
            rows.append(current_row)
            current_row = [(x, y)]
        prev_y = y

    # Add the final row
    if current_row:
        rows.append(current_row)

    # 3) Sort each row by x
    for row in rows:
        row.sort(key=lambda c: c[0])

    # 4) Flatten back into a single list
    grouped_sorted_cells = []
    for row in rows:
        grouped_sorted_cells.extend(row)

    return grouped_sorted_cells

def assign_cells_to_layout(empty_cells, board_layout):
    # First do row-grouping on empty_cells
    grouped_cells = group_cells_by_row(empty_cells, row_threshold=10)

    # Now 'grouped_cells' is sorted row-by-row
    # The rest is the same as before:
    total_spots = sum(len(row) for row in board_layout)
    if len(grouped_cells) < total_spots:
        logging.warning(
            f"We only have {len(grouped_cells)} circles, but layout needs {total_spots}."
        )

    # Fill row by row
    populated_layout = []
    index = 0
    for row_idx, row in enumerate(board_layout):
        row_size = len(row)
        row_cells = []
        for col_idx in range(row_size):
            if index < len(grouped_cells):
                row_cells.append(grouped_cells[index])
                index += 1
            else:
                row_cells.append(None)
        populated_layout.append(row_cells)
    with open('populated_layout.txt', 'w') as f:
        for row in populated_layout:
            f.write(f"{row}\n")
    return populated_layout

def is_cell_occupied_by_color(hsv_img, cx, cy, radius,
                              lower_hsv, upper_hsv,
                              coverage_thresh=0.15):
    """
    Return True if at least `coverage_thresh` fraction of the
    pixels inside the circle (cx, cy, radius) fall within [lower_hsv, upper_hsv].
    """
    # 1. Build the color mask
    mask = cv2.inRange(hsv_img, lower_hsv, upper_hsv)

    # 2. Create a boolean circle‐mask
    h, w = mask.shape
    Y, X = np.ogrid[:h, :w]
    circle = (X - cx)**2 + (Y - cy)**2 <= radius**2

    # 3. Compute coverage
    pixels = mask[circle]
    if pixels.size == 0:
        return False
    coverage = np.count_nonzero(pixels) / float(pixels.size)
    return coverage >= coverage_thresh



# ---------------------------
# Marble Assignment and Visualization
# ---------------------------
def assign_marbles_to_cells(cells, marble_positions, base_threshold=45, debug=False):
    """
    Assign each marble to the nearest available cell within the threshold.
    Ensures that each cell is assigned to at most one marble.
    Returns: dict { (cx, cy): "green"/"red"/None }
    """
    cell_occupancy = {c: None for c in cells}  # Initialize all cells as empty
    occupied_cells = set()  # Track occupied cells

    for (mx, my, mradius, color) in marble_positions:
        # Compute distances from the marble center to each cell center
        distances = [np.hypot(mx - cx, my - cy) for (cx, cy) in cells]
        
        # Get sorted indices based on distance
        sorted_indices = np.argsort(distances)
        
        # Iterate through cells in order of proximity
        for idx in sorted_indices:
            chosen_cell = cells[idx]
            min_dist = distances[idx]
            
            if chosen_cell in occupied_cells:
                continue  # Skip if already occupied
            
            if min_dist < (base_threshold + mradius):
                cell_occupancy[chosen_cell] = color
                occupied_cells.add(chosen_cell)
                
                if debug:
                    logging.info(
                        f"Marble at ({mx}, {my}) with radius {mradius} "
                        f"-> Cell {chosen_cell}, Distance={min_dist:.1f}, Color={color} -> Assigned"
                    )
                break  # Move to the next marble after assignment
            else:
                continue  # Check next closest cell
        else:
            # If no suitable cell found
            logging.info(
                f"Marble at ({mx}, {my}) with radius {mradius} "
                f"-> No nearby cell found (min_dist={min_dist:.1f}) Color={color} -> Skipped"
            )

    return cell_occupancy

def detect_and_draw_circles(mask, image, color_name, board_contour):
    """
    Detect circular contours from mask, draw them on 'image', return center coords.
    Only marbles inside the board contour are considered.
    Return: [(x, y, color_name), ...]
    """
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    logging.info(f"Number of contours found in {color_name} mask: {len(contours)}")

    detected_marbles = []
    for contour in contours:
        (x_float, y_float), radius = cv2.minEnclosingCircle(contour)
        if MARBLE_HOUGH_MIN_RADIUS < radius < MARBLE_HOUGH_MAX_RADIUS:
            perimeter = cv2.arcLength(contour, True)
            area = cv2.contourArea(contour)
            if perimeter == 0:
                continue
            circularity = 4 * np.pi * (area / (perimeter * perimeter))
            if circularity > 0.6:
                # Round down the coordinates to integers
                x_int, y_int = int(np.floor(x_float)), int(np.floor(y_float))
                radius_int = int(np.floor(radius))
                
                # Check if marble is inside the board
                if board_contour is not None:
                    inside = cv2.pointPolygonTest(board_contour, (x_int, y_int), False)
                    if inside < 0:
                        logging.debug(f"{color_name.capitalize()} marble at ({x_int}, {y_int}) is outside the board. Skipping.")
                        continue  # Skip marbles outside the board

                # Draw the marble
                if color_name == "green":
                    draw_color = (0, 255, 0)  # Green
                elif color_name == "red":
                    draw_color = (0, 0, 255)  # Red
                else:
                    draw_color = (255, 255, 255)  # White for unknown

                cv2.circle(image, (x_int, y_int), radius_int, draw_color, 2)
                cv2.circle(image, (x_int, y_int), 2, (0, 0, 255), 3)
                
                detected_marbles.append((x_int, y_int, radius_int, color_name))
                
                logging.info(f"Detected {color_name} marble at ({x_int}, {y_int}) with radius {radius_int}")
                
                # Append to debug_info_marbles.txt
                with open('debug_info_marbles.txt', 'a') as f:  # Changed 'w' to 'a' to append
                    f.write(f"Colour: {color_name}\n")
                    f.write(f"Center: ({x_int}, {y_int})\n")
    return detected_marbles

def occupancy_by_colour_ratio(cells, hsv_img):
    occ = {}          # (cx,cy) -> None|'green'|'red'
    cell_r = 22       # radius ≈ HOUGH_MAX_RADIUS – tune once
    
    green_mask = cv2.inRange(hsv_img, GREEN_LOWER, GREEN_UPPER)
    red_mask1  = cv2.inRange(hsv_img, RED_LOWER1, RED_UPPER1)
    red_mask2  = cv2.inRange(hsv_img, RED_LOWER2, RED_UPPER2)
    red_mask   = cv2.bitwise_or(red_mask1, red_mask2)

    for (cx,cy) in cells:
        # Grab a circular ROI (boolean mask)
        yy, xx = np.ogrid[:hsv_img.shape[0], :hsv_img.shape[1]]
        circle = (xx - cx) ** 2 + (yy - cy) ** 2 <= cell_r ** 2

        green_ratio = green_mask[circle].mean() / 255.0
        red_ratio   = red_mask[circle].mean()   / 255.0

        if   green_ratio > 0.15: occ[(cx,cy)] = 'green'
        elif red_ratio   > 0.15: occ[(cx,cy)] = 'red'
        else:                    occ[(cx,cy)] = None
    return occ


# def detect_marbles(hsv_image, draw_image, board_contour, debug = False):
#     """
#     Using predefined HSV thresholds for green/red,
#     detect marbles and return a combined list of them.
#     Only marbles inside the board are considered.
#     """
#     # Create masks for green and red
#     green_mask = cv2.inRange(hsv_image, GREEN_LOWER, GREEN_UPPER)
#     red_mask1 = cv2.inRange(hsv_image, RED_LOWER1, RED_UPPER1)
#     red_mask2 = cv2.inRange(hsv_image, RED_LOWER2, RED_UPPER2)
#     red_mask = cv2.bitwise_or(red_mask1, red_mask2)

#     # Morphological cleaning
#     kernel = np.ones((5, 5), np.uint8)
#     green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_OPEN, kernel, iterations=2)
#     green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
#     kernel2 = np.ones((3,3), np.uint8)
#     green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel2, iterations=1)
#     red_mask   = cv2.morphologyEx(red_mask,   cv2.MORPH_CLOSE, kernel2, iterations=1)

    
#     #save greenmask to directory debug_images
#     cv2.imwrite('debug_images/green_mask.jpg', green_mask)
#     cv2.imwrite('debug_images/red_mask.jpg', red_mask)
    
#     red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_OPEN, kernel, iterations=2)
#     red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    

    
#     # Detect circles
#     green_marbles = detect_and_draw_circles(green_mask, draw_image, "green", board_contour)
#     red_marbles = detect_and_draw_circles(red_mask, draw_image, "red", board_contour)

#     return green_marbles + red_marbles

def detect_marbles(hsv_image, draw_image, board_contour, debug=False):
    """
    Using color ratio analysis instead of circularity detection,
    identify marbles based on their color signatures.
    Returns the same format as the original function for compatibility.
    """
    # Create masks for green and red
    green_mask = cv2.inRange(hsv_image, GREEN_LOWER, GREEN_UPPER)
    red_mask1 = cv2.inRange(hsv_image, RED_LOWER1, RED_UPPER1)
    red_mask2 = cv2.inRange(hsv_image, RED_LOWER2, RED_UPPER2)
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)

    # Morphological cleaning
    kernel = np.ones((5, 5), np.uint8)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_OPEN, kernel, iterations=1)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_OPEN, kernel, iterations=1)
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    # Save masks for debugging
    if debug:
        cv2.imwrite('debug_images/green_mask.jpg', green_mask)
        cv2.imwrite('debug_images/red_mask.jpg', red_mask)
    
    # Image dimensions
    h, w = hsv_image.shape[:2]
    
    # Instead of looking for circles, we'll analyze the area around detected marbles
    # The sampling radius should be approximately the size of a marble
    sampling_radius = 20
    ratio_threshold = 0.15  # Minimum ratio of colored pixels to consider a marble
    
    # Store detected marbles
    detected_marbles = []
    
    # For compatibility with your existing system, we still need to retain the
    # format of [(x, y, radius, color), ...] for detected marbles
    
    # Use blob detection or other methods to find potential marble centers
    # Here we'll use connected components on the color masks
    
    # Process green marbles
    green_components = cv2.connectedComponentsWithStats(green_mask, connectivity=8)
    for i in range(1, green_components[0]):  # Skip the background (id=0)
        # Get component stats
        stats = green_components[2][i]
        area = stats[cv2.CC_STAT_AREA]
        
        # Filter small or very large components
        if area < 50 or area > 2000:
            continue
            
        # Get centroid
        centroids = green_components[3]
        cx, cy = centroids[i]
        cx, cy = int(cx), int(cy)
        
        # Check if it's inside the board contour
        if board_contour is not None:
            inside = cv2.pointPolygonTest(board_contour, (cx, cy), False)
            if inside < 0:  # -1 means outside the contour
                continue
                
        # Calculate color ratio in a circle around this point
        Y, X = np.ogrid[:h, :w]
        dist_from_center = (X - cx)**2 + (Y - cy)**2
        circle_mask = dist_from_center <= sampling_radius**2
        
        # Ensure the circle mask is within image bounds
        if np.sum(circle_mask) == 0:
            continue
            
        # Calculate green ratio within circle
        green_pixels = np.count_nonzero(green_mask[circle_mask])
        green_ratio = green_pixels / float(np.sum(circle_mask))
        
        # If strong enough green presence, consider it a green marble
        if green_ratio >= ratio_threshold:
            # Add to detected marbles with the format your existing code expects
            detected_marbles.append((cx, cy, sampling_radius, "green"))
            
            # Draw visualization
            cv2.circle(draw_image, (cx, cy), sampling_radius, (0, 255, 0), 2)
            cv2.circle(draw_image, (cx, cy), 2, (0, 0, 255), 3)
            
            if debug:
                logging.info(f"Detected green marble at ({cx}, {cy}) with green_ratio={green_ratio:.3f}")
    
    # Process red marbles (same approach)
    red_components = cv2.connectedComponentsWithStats(red_mask, connectivity=8)
    for i in range(1, red_components[0]):
        stats = red_components[2][i]
        area = stats[cv2.CC_STAT_AREA]
        
        if area < 50 or area > 2000:
            continue
            
        centroids = red_components[3]
        cx, cy = centroids[i]
        cx, cy = int(cx), int(cy)
        
        if board_contour is not None:
            inside = cv2.pointPolygonTest(board_contour, (cx, cy), False)
            if inside < 0:
                continue
                
        Y, X = np.ogrid[:h, :w]
        dist_from_center = (X - cx)**2 + (Y - cy)**2
        circle_mask = dist_from_center <= sampling_radius**2
        
        if np.sum(circle_mask) == 0:
            continue
            
        red_pixels = np.count_nonzero(red_mask[circle_mask])
        red_ratio = red_pixels / float(np.sum(circle_mask))
        
        if red_ratio >= ratio_threshold:
            detected_marbles.append((cx, cy, sampling_radius, "red"))
            
            cv2.circle(draw_image, (cx, cy), sampling_radius, (0, 0, 255), 2)
            cv2.circle(draw_image, (cx, cy), 2, (0, 0, 255), 3)
            
            if debug:
                logging.info(f"Detected red marble at ({cx}, {cy}) with red_ratio={red_ratio:.3f}")
    
    # Optional fallback: If no marbles were found with blob detection,
    # analyze each empty cell location directly
    if not detected_marbles:
        # This requires access to empty_cells, which may need to be passed
        # as an additional parameter or made global if this approach is needed
        logging.warning("No marbles detected with blob detection, consider direct cell analysis")
    
    if debug:
        logging.info(f"Total marbles detected: {len(detected_marbles)} (Green: {sum(1 for m in detected_marbles if m[3]=='green')}, Red: {sum(1 for m in detected_marbles if m[3]=='red')})")
    
    return detected_marbles

def detect_cell_occupancy_directly(empty_cells, hsv_image, debug=False):
    """
    Alternative method to directly detect occupancy at each cell location
    without the intermediate step of detecting marbles.
    
    This can be used as a fallback or validation approach.
    """
    # Create color masks
    green_mask = cv2.inRange(hsv_image, GREEN_LOWER, GREEN_UPPER)
    red_mask1 = cv2.inRange(hsv_image, RED_LOWER1, RED_UPPER1)
    red_mask2 = cv2.inRange(hsv_image, RED_LOWER2, RED_UPPER2)
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)
    
    # Apply morphological operations
    kernel = np.ones((5, 5), np.uint8)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_OPEN, kernel, iterations=1)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_OPEN, kernel, iterations=1)
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    # Image dimensions
    h, w = hsv_image.shape[:2]
    
    # Sampling parameters
    sampling_radius = 20
    ratio_threshold = 0.15
    
    # Initialize cell occupancy dictionary
    cell_occupancy = {}
    
    # Analyze each cell location
    for cx, cy in empty_cells:
        # Create a circular mask centered at the cell position
        Y, X = np.ogrid[:h, :w]
        circle_mask = (X - cx)**2 + (Y - cy)**2 <= sampling_radius**2
        
        # Calculate color ratios
        pixels_in_circle = np.sum(circle_mask)
        if pixels_in_circle == 0:
            cell_occupancy[(cx, cy)] = None
            continue
        
        green_pixels = np.count_nonzero(green_mask[circle_mask])
        red_pixels = np.count_nonzero(red_mask[circle_mask])
        
        green_ratio = green_pixels / float(pixels_in_circle)
        red_ratio = red_pixels / float(pixels_in_circle)
        
        # Determine occupancy based on dominant color
        if green_ratio >= ratio_threshold and green_ratio >= red_ratio:
            cell_occupancy[(cx, cy)] = "green"
            if debug:
                logging.info(f"Cell ({cx}, {cy}) -> green (ratio={green_ratio:.3f})")
        elif red_ratio >= ratio_threshold:
            cell_occupancy[(cx, cy)] = "red"
            if debug:
                logging.info(f"Cell ({cx}, {cy}) -> red (ratio={red_ratio:.3f})")
        else:
            cell_occupancy[(cx, cy)] = None
            if debug:
                logging.info(f"Cell ({cx}, {cy}) -> empty (green={green_ratio:.3f}, red={red_ratio:.3f})")
    
    return cell_occupancy

# ---------------------------
# Debug Mapping Information
# ---------------------------
def output_debug_info(cells, marbles, cell_occupancy, filename='debug_mapping.txt'):
    """
    Outputs cells coordinates, marbles coordinates, and their mappings to a debug file.

    Parameters:
        cells (list of tuples): List of cell coordinates [(x, y), ...]
        marbles (list of tuples): List of marbles [(x, y, radius, color), ...]
        cell_occupancy (dict): Mapping from cell coordinates to color or None
        filename (str): Name of the debug file to write
    """
    with open(filename, 'w') as f:
        f.write("=== Cells Detected ===\n")
        f.write("Cell_X, Cell_Y\n")
        for (cx, cy) in cells:
            f.write(f"{cx}, {cy}\n")
        
        f.write("\n=== Marbles Detected ===\n")
        f.write("Marble_X, Marble_Y, Radius, Color\n")
        for (mx, my, mradius, color) in marbles:
            f.write(f"{mx}, {my}, {mradius}, {color}\n")
        
        f.write("\n=== Cell to Marble Mapping ===\n")
        f.write("Cell_X, Cell_Y, Occupancy, Marble_Color\n")
        for cell in cells:
            occupancy = cell_occupancy.get(cell)
            if occupancy is not None:
                f.write(f"{cell[0]}, {cell[1]}, Occupied, {occupancy}\n")
            else:
                f.write(f"{cell[0]}, {cell[1]}, Empty, -\n")
    
    logging.info(f"Debug mapping information written to {filename}")
    
def print_text_board(populated_layout, cell_occupancy):
    
    # Print to board_matrix.txt
    with open('board_matrix.txt', 'w') as f:
        for row_idx, row in enumerate(populated_layout):
            row_str = []
            for cell in row:
                if cell is None:
                    # No circle assigned
                    row_str.append("X")
                else:
                    occupant = cell_occupancy.get(cell, None)
                    if occupant is None:
                        row_str.append(".")
                    elif occupant == "green":
                        row_str.append("G")
                    elif occupant == "red":
                        row_str.append("R")
                    else:
                        # Just in case there's another color or something else
                        row_str.append("?")
            # Join the row cells into a string
            row_text = " ".join(row_str)
            print(row_text)
            f.write(row_text + '\n')

def occupancy_by_colour_ratio_nearest_marble(
    cells,                # List of reference cell coordinates (cx, cy)
    marbles,              # List of detected marbles (mx, my, radius, detected_color)
    hsv_img,              # Current HSV image
    max_association_distance=50, # Max distance between cell and marble center
    ratio_threshold=0.15, # Min ratio of color pixels in sample area
    debug=False
):
    """
    Assigns occupancy ('green', 'red', or None) to each cell based on color ratios,
    but samples the area around the NEAREST detected marble within a distance threshold.

    Args:
        cells: List of (cx, cy) tuples for reference cell locations.
        marbles: List of (mx, my, radius, detected_color) tuples for detected marbles.
        hsv_img: The HSV image of the current board state.
        max_association_distance: Maximum distance allowed between a cell center (cx, cy)
                                 and a marble center (mx, my) to consider them associated.
        ratio_threshold: Minimum proportion of pixels within the sampling area that must
                         match the target color.
        debug: Print debug information.

    Returns:
        dict: { (cx, cy): "green" / "red" / None }
    """
    cell_occupancy = {c: None for c in cells} # Initialize all cells as empty
    assigned_marble_indices = set() # Track which marbles have been assigned to a cell

    # Pre-calculate masks for efficiency
    green_mask = cv2.inRange(hsv_img, GREEN_LOWER, GREEN_UPPER)
    red_mask1  = cv2.inRange(hsv_img, RED_LOWER1, RED_UPPER1)
    red_mask2  = cv2.inRange(hsv_img, RED_LOWER2, RED_UPPER2)
    red_mask   = cv2.bitwise_or(red_mask1, red_mask2)
    h, w = hsv_img.shape[:2]

    if not marbles: # Handle case with no detected marbles
         logging.warning("No marbles detected to associate with cells.")
         return cell_occupancy

    # --- Step 1: For each cell, find the closest *unassigned* marble ---
    potential_assignments = [] # Store potential (distance, cell_idx, marble_idx)
    for i, (cx, cy) in enumerate(cells):
        min_dist = float('inf')
        best_marble_idx = -1
        for j, (mx, my, mradius, mcolor) in enumerate(marbles):
             dist = np.hypot(mx - cx, my - cy)
             if dist < min_dist and dist < max_association_distance :
                 min_dist = dist
                 best_marble_idx = j

        if best_marble_idx != -1:
            potential_assignments.append({'dist': min_dist, 'cell_idx': i, 'marble_idx': best_marble_idx})

    # --- Step 2: Sort potential assignments by distance (closest first) ---
    # This helps ensure the best geometric matches are considered first.
    potential_assignments.sort(key=lambda x: x['dist'])

    # --- Step 3: Assign based on ratio check at the *marble's* location ---
    assigned_cells = set() # Keep track of cells that have been filled
    assigned_marbles = set() # Keep track of marbles that have been used

    for assignment in potential_assignments:
        cell_idx = assignment['cell_idx']
        marble_idx = assignment['marble_idx']
        cx, cy = cells[cell_idx]
        mx, my, mradius, detected_color = marbles[marble_idx] # Use marble's info

        # Skip if cell or marble is already assigned
        if cell_idx in assigned_cells or marble_idx in assigned_marbles:
            continue

        # --- Perform Ratio Check CENTERED ON THE MARBLE (mx, my) ---
        # Use the marble's detected radius for the sampling area
        sample_radius = int(mradius) # Or use a fixed value like 20 if preferred
        if sample_radius <= 0: sample_radius = 1 # Avoid zero radius

        # Create a circular mask centered at the marble
        Y, X = np.ogrid[:h, :w]
        circle_mask = (X - mx)**2 + (Y - my)**2 <= sample_radius**2

        # Calculate ratios within that specific circle mask
        pixels_in_circle = np.sum(circle_mask)
        if pixels_in_circle == 0:
             if debug: logging.debug(f"Cell ({cx},{cy}) -> Marble ({mx},{my}) -> Zero pixels in sample radius {sample_radius}. Skipping.")
             continue # Skip if the circle is somehow empty or off-image

        green_pixels_in_circle = np.count_nonzero(green_mask[circle_mask])
        red_pixels_in_circle = np.count_nonzero(red_mask[circle_mask])

        green_ratio = green_pixels_in_circle / float(pixels_in_circle)
        red_ratio = red_pixels_in_circle / float(pixels_in_circle)

        assigned_color = None
        if green_ratio >= ratio_threshold and green_ratio >= red_ratio: # Check green first
             assigned_color = 'green'
        elif red_ratio >= ratio_threshold: # Then check red
             assigned_color = 'red'

        # --- Final Assignment ---
        if assigned_color is not None:
            cell_occupancy[(cx, cy)] = assigned_color
            assigned_cells.add(cell_idx)
            assigned_marbles.add(marble_idx)
            if debug:
                logging.info(
                    f"Cell ({cx},{cy}) -> Associated Marble ({mx},{my}, r={mradius}) "
                    f"-> Ratios(G:{green_ratio:.2f}, R:{red_ratio:.2f}) "
                    f"-> Assigned: {assigned_color} (Dist: {assignment['dist']:.1f})"
                )
        elif debug:
             logging.info(
                    f"Cell ({cx},{cy}) -> Associated Marble ({mx},{my}, r={mradius}) "
                    f"-> Ratios(G:{green_ratio:.2f}, R:{red_ratio:.2f}) "
                    f"-> Not assigned (ratio low) (Dist: {assignment['dist']:.1f})"
                )

    return cell_occupancy

def detect_marbles_by_color_ratio(hsv_image, draw_image, empty_cells, board_contour, 
                                 sampling_radius=20, ratio_threshold=0.15, debug=False):
    """
    Detect marbles using color ratios in circular regions around each potential cell.
    Returns a list of detected marbles with their positions and colors.
    
    Args:
        hsv_image: HSV image of the current board state
        draw_image: Image to draw detection visualization on
        empty_cells: List of (x, y) coordinates for all detected cells
        board_contour: Detected board contour for filtering
        sampling_radius: Radius of circular area to sample for color detection
        ratio_threshold: Minimum ratio of color pixels to consider it a marble
        debug: Enable debug visualization and logging
        
    Returns:
        List of tuples (x, y, radius, color), representing detected marbles
    """
    # Create color masks
    green_mask = cv2.inRange(hsv_image, GREEN_LOWER, GREEN_UPPER)
    red_mask1 = cv2.inRange(hsv_image, RED_LOWER1, RED_UPPER1)
    red_mask2 = cv2.inRange(hsv_image, RED_LOWER2, RED_UPPER2)
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)
    
    # Optional: Save masks for debugging
    if debug:
        cv2.imwrite('debug_images/green_mask.jpg', green_mask)
        cv2.imwrite('debug_images/red_mask.jpg', red_mask)
    
    # Apply morphological operations to clean masks
    kernel = np.ones((5, 5), np.uint8)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_OPEN, kernel, iterations=1)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_OPEN, kernel, iterations=1)
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    # Store detected marbles
    detected_marbles = []
    
    # Image dimensions for boundary checking
    h, w = hsv_image.shape[:2]
    
    # Analyze each cell location for marble presence
    for cx, cy in empty_cells:
        # Skip if the cell is outside the board contour
        if board_contour is not None:
            inside = cv2.pointPolygonTest(board_contour, (cx, cy), False)
            if inside < 0:  # -1 means outside the contour
                continue
        
        # Create a circular mask centered at the cell position
        Y, X = np.ogrid[:h, :w]
        circle_mask = (X - cx)**2 + (Y - cy)**2 <= sampling_radius**2
        
        # Calculate color ratios within the circle
        pixels_in_circle = np.sum(circle_mask)
        if pixels_in_circle == 0:
            continue  # Skip if the circle is off-image
        
        green_pixels = np.count_nonzero(green_mask[circle_mask])
        red_pixels = np.count_nonzero(red_mask[circle_mask])
        
        green_ratio = green_pixels / float(pixels_in_circle)
        red_ratio = red_pixels / float(pixels_in_circle)
        
        # Determine if a marble is present and what color
        if green_ratio >= ratio_threshold and green_ratio >= red_ratio:
            color = "green"
            draw_color = (0, 255, 0)  # Green (BGR)
            
            # Add to detected marbles list
            detected_marbles.append((cx, cy, sampling_radius, color))
            
            # Draw detected marble for visualization
            cv2.circle(draw_image, (cx, cy), sampling_radius, draw_color, 2)
            cv2.circle(draw_image, (cx, cy), 2, (0, 0, 255), 3)  # Center point
            
            if debug:
                logging.info(f"Detected green marble at ({cx}, {cy}) with green_ratio={green_ratio:.3f}")
                
        elif red_ratio >= ratio_threshold:
            color = "red"
            draw_color = (0, 0, 255)  # Red (BGR)
            
            # Add to detected marbles list
            detected_marbles.append((cx, cy, sampling_radius, color))
            
            # Draw detected marble for visualization
            cv2.circle(draw_image, (cx, cy), sampling_radius, draw_color, 2)
            cv2.circle(draw_image, (cx, cy), 2, (0, 0, 255), 3)  # Center point
            
            if debug:
                logging.info(f"Detected red marble at ({cx}, {cy}) with red_ratio={red_ratio:.3f}")
    
    if debug:
        logging.info(f"Total marbles detected: {len(detected_marbles)} (Green: {sum(1 for m in detected_marbles if m[3]=='green')}, Red: {sum(1 for m in detected_marbles if m[3]=='red')})")
    
    return detected_marbles

def improved_cell_occupancy_detection(empty_cells, hsv_image, debug=False):
    """
    Direct method to detect occupancy at each cell location using a simple color threshold.
    
    Args:
        empty_cells: List of (x, y) coordinates for known cell positions
        hsv_image: HSV image of the current board state
        debug: Enable debug output
        
    Returns:
        dict mapping cell coordinates to occupancy: {(x, y): "green"/"red"/None}
    """
    # Create color masks with the predefined thresholds
    green_mask = cv2.inRange(hsv_image, GREEN_LOWER, GREEN_UPPER)
    red_mask1 = cv2.inRange(hsv_image, RED_LOWER1, RED_UPPER1)
    red_mask2 = cv2.inRange(hsv_image, RED_LOWER2, RED_UPPER2)
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)
    
    # Save masks for debugging
    if debug:
        cv2.imwrite('debug_images/green_mask.jpg', green_mask)
        cv2.imwrite('debug_images/red_mask.jpg', red_mask)
    
    # Image dimensions
    h, w = hsv_image.shape[:2]
    
    # Directly sample each known cell location with a fixed radius
    sampling_radius = 20  # Radius to check for color presence
    threshold_ratio = 0.15  # 15% of pixels must be colored to detect a marble
    
    # Initialize result dictionary
    cell_occupancy = {}
    
    # For each known cell location
    for cx, cy in empty_cells:
        # Create a circular mask centered at the cell position
        Y, X = np.ogrid[:h, :w]
        dist_from_center = (X - cx)**2 + (Y - cy)**2
        circle_mask = dist_from_center <= sampling_radius**2
        
        # Ensure the mask is within image bounds
        if np.sum(circle_mask) == 0:
            cell_occupancy[(cx, cy)] = None
            if debug:
                logging.debug(f"Cell ({cx},{cy}) is out of image bounds")
            continue
        
        # Count color pixels within the circle
        green_pixels = np.count_nonzero(green_mask[circle_mask])
        red_pixels = np.count_nonzero(red_mask[circle_mask])
        
        total_pixels = np.sum(circle_mask)
        green_ratio = green_pixels / float(total_pixels)
        red_ratio = red_pixels / float(total_pixels)
        
        # Determine occupancy based on ratio threshold
        if green_ratio >= threshold_ratio and green_ratio > red_ratio:
            cell_occupancy[(cx, cy)] = "green"
            if debug:
                logging.info(f"Cell ({cx},{cy}) -> GREEN (ratio: {green_ratio:.3f})")
        elif red_ratio >= threshold_ratio:
            cell_occupancy[(cx, cy)] = "red"
            if debug:
                logging.info(f"Cell ({cx},{cy}) -> RED (ratio: {red_ratio:.3f})")
        else:
            cell_occupancy[(cx, cy)] = None
            if debug:
                logging.info(f"Cell ({cx},{cy}) -> EMPTY (green: {green_ratio:.3f}, red: {red_ratio:.3f})")
    
    return cell_occupancy
# ---------------------------
# Main Function
# ---------------------------
def main():
    try:
        # Preprocess the empty board image
        empty_image = cv2.imread("board_empty_test1.jpeg")
        empty_blurred, empty_hsv = preprocess_image(empty_image, debug=True)
        
        # cv2.imshow("Empty Board", empty_blurred)

        # Detect the hexagonal board contour
        board_contour = detect_board(empty_blurred, debug=True)

        if board_contour is None:
            logging.error("Board contour not detected. Exiting.")
            # return

        # Detect cells only within the board
        empty_cells = detect_board_cells(empty_blurred, board_contour, debug=True)

        # Sort cells by y-coordinate for alignment
        empty_cells.sort(key=lambda x: x[1])

        # Save cell coordinates to debug file
        with open('debug_info.txt', 'w') as f:
            f.write(f"Number of cells detected: {len(empty_cells)}\n")
            for cell in empty_cells:
                f.write(f"{cell[0]} {cell[1]}\n")

        # Assign cells to layout
        populated_layout = assign_cells_to_layout(empty_cells, board_layout)

        # Preprocess the current board image
        current_image = cv2.imread("board_current_red_test1.jpeg")
        current_blurred, current_hsv = preprocess_image(current_image, debug=False)

        while True:
            temp_display = current_blurred.copy()
            all_marbles = detect_marbles(current_hsv, temp_display, board_contour, debug=True)
            # cell_occupancy = assign_marbles_to_cells(empty_cells, all_marbles, base_threshold=BASE_THRESHOLD, debug=True)
            # cell_occupancy = occupancy_by_colour_ratio(empty_cells, current_hsv)
            cell_occupancy = occupancy_by_colour_ratio_nearest_marble(
                cells=empty_cells,  # Use the reference cell list
                marbles=all_marbles, # Pass the list of detected marbles
                hsv_img=current_hsv,
                max_association_distance=40, # Tune this distance
                ratio_threshold=0.15,        # Tune this ratio
                debug=True                   # Enable debug prints
            )

            # Output debug information
            output_debug_info(empty_cells, all_marbles, cell_occupancy, filename='debug_mapping.txt')

            # Visualize results
            for (cx, cy), marble_color in cell_occupancy.items():
                if marble_color == "green":
                    color_draw = (0, 255, 0)  # Green
                elif marble_color == "red":
                    color_draw = (0, 0, 255)  # Red
                else:
                    color_draw = (255, 0, 0)  # Blue for empty

                cv2.circle(temp_display, (int(cx), int(cy)), 5, color_draw, -1)

            print_text_board(populated_layout, cell_occupancy)
            cv2.imshow("Final Board State", temp_display)

            key = cv2.waitKey(1) & 0xFF
            if key == ord('q'):
                break

    except FileNotFoundError as e:
        logging.error(e)
    except ValueError as e:
        logging.error(e)

    cv2.destroyAllWindows()
    
    






# ---------------------------
# Entry Point
# ---------------------------
if __name__ == "__main__":
    main()