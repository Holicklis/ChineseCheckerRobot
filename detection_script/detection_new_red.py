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
    return auto_result

def preprocess_image(image_path, max_dim=1600, rotate_clockwise=False, debug=False):
    """
    Load and preprocess the image with:
      - Adaptive resizing (max dimension = max_dim)
      - Consistent rotation (clockwise or counterclockwise)
      - Automatic brightness/contrast
      - CLAHE on V-channel
      - Gaussian blur
    Returns: (blurred BGR image, HSV image)
    """
    

    image = cv2.imread(image_path)
    
    
    if image is None:
        raise FileNotFoundError(f"Image '{image_path}' not found.")

    # # Rotate consistently
    # if rotate_clockwise:
    #     image = cv2.rotate(image, cv2.ROTATE_90_CLOCKWISE)
    # else:
    #     image = cv2.rotate(image, cv2.ROTATE_90_COUNTERCLOCKWISE)
        
    #increase brightness
    # image = cv2.convertScaleAbs(image, alpha=1.5, beta=0)

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
    blurred = cv2.GaussianBlur(enhanced, (5, 5), 0)
    hsv_blurred = cv2.cvtColor(blurred, cv2.COLOR_BGR2HSV)
    

    if debug:
        cv2.imshow("Preprocessed Image", blurred)

    return blurred, hsv_blurred

# ---------------------------
# Trackbars Creation
# ---------------------------
def create_green_trackbars():
    """
    Create a separate window with trackbars for green marble HSV range adjustments.
    """
    cv2.namedWindow("Green Controls", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Green Controls", 400, 300)
    
    # Adjusted wider initial values for green marble range
    cv2.createTrackbar("Lower H", "Green Controls", 36, 180, lambda x: None)  # Lower H widened
    cv2.createTrackbar("Lower S", "Green Controls", 0, 255, lambda x: None)  # Lower S widened
    cv2.createTrackbar("Lower V", "Green Controls", 0, 255, lambda x: None)  # Lower V widened
    cv2.createTrackbar("Upper H", "Green Controls", 86, 180, lambda x: None)  # Upper H widened
    cv2.createTrackbar("Upper S", "Green Controls", 255, 255, lambda x: None)
    cv2.createTrackbar("Upper V", "Green Controls", 255, 255, lambda x: None)

def create_red_trackbars():
    """
    Create a separate window with trackbars for red marble HSV range adjustments.
    """
    cv2.namedWindow("Red Controls", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Red Controls", 400, 300)
    
    # Initial values for red marble range
    # Note: Red in HSV can wrap around, so we might need two ranges or adjust accordingly
    cv2.createTrackbar("Lower H1", "Red Controls", 0, 10, lambda x: None)   # Lower H range 1
    cv2.createTrackbar("Lower S1", "Red Controls", 100, 255, lambda x: None)
    cv2.createTrackbar("Lower V1", "Red Controls", 100, 255, lambda x: None)
    cv2.createTrackbar("Upper H1", "Red Controls", 10, 180, lambda x: None)  # Upper H range 1
    cv2.createTrackbar("Upper S1", "Red Controls", 255, 255, lambda x: None)
    cv2.createTrackbar("Upper V1", "Red Controls", 255, 255, lambda x: None)
    
    cv2.createTrackbar("Lower H2", "Red Controls", 160, 180, lambda x: None)  # Lower H range 2
    cv2.createTrackbar("Lower S2", "Red Controls", 100, 255, lambda x: None)
    cv2.createTrackbar("Lower V2", "Red Controls", 100, 255, lambda x: None)
    cv2.createTrackbar("Upper H2", "Red Controls", 180, 180, lambda x: None)  # Upper H range 2
    cv2.createTrackbar("Upper S2", "Red Controls", 255, 255, lambda x: None)
    cv2.createTrackbar("Upper V2", "Red Controls", 255, 255, lambda x: None)

def get_green_color_ranges():
    """
    Retrieve the current positions of the green HSV trackbars.
    Returns: (lower, upper) as numpy arrays
    """
    lh = cv2.getTrackbarPos("Lower H", "Green Controls")
    ls = cv2.getTrackbarPos("Lower S", "Green Controls")
    lv = cv2.getTrackbarPos("Lower V", "Green Controls")
    uh = cv2.getTrackbarPos("Upper H", "Green Controls")
    us = cv2.getTrackbarPos("Upper S", "Green Controls")
    uv = cv2.getTrackbarPos("Upper V", "Green Controls")

    green_lower = np.array([lh, ls, lv])
    green_upper = np.array([uh, us, uv])
    return green_lower, green_upper

def get_red_color_ranges():
    """
    Retrieve the current positions of the red HSV trackbars.
    Returns: (lower1, upper1, lower2, upper2) as numpy arrays
    """
    # Range 1
    lh1 = cv2.getTrackbarPos("Lower H1", "Red Controls")
    ls1 = cv2.getTrackbarPos("Lower S1", "Red Controls")
    lv1 = cv2.getTrackbarPos("Lower V1", "Red Controls")
    uh1 = cv2.getTrackbarPos("Upper H1", "Red Controls")
    us1 = cv2.getTrackbarPos("Upper S1", "Red Controls")
    uv1 = cv2.getTrackbarPos("Upper V1", "Red Controls")
    
    # Range 2
    lh2 = cv2.getTrackbarPos("Lower H2", "Red Controls")
    ls2 = cv2.getTrackbarPos("Lower S2", "Red Controls")
    lv2 = cv2.getTrackbarPos("Lower V2", "Red Controls")
    uh2 = cv2.getTrackbarPos("Upper H2", "Red Controls")
    us2 = cv2.getTrackbarPos("Upper S2", "Red Controls")
    uv2 = cv2.getTrackbarPos("Upper V2", "Red Controls")

    red_lower1 = np.array([lh1, ls1, lv1])
    red_upper1 = np.array([uh1, us1, uv1])
    red_lower2 = np.array([lh2, ls2, lv2])
    red_upper2 = np.array([uh2, us2, uv2])
    return red_lower1, red_upper1, red_lower2, red_upper2

def create_canny_trackbars():
    """
    Create trackbars for tuning Canny edge detection parameters.
    """
    cv2.namedWindow("Canny Controls", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Canny Controls", 400, 300)
    cv2.createTrackbar("Low Threshold", "Canny Controls", 50, 500, lambda x: None)
    cv2.createTrackbar("High Threshold", "Canny Controls", 150, 500, lambda x: None)
    cv2.createTrackbar("Blur Kernel", "Canny Controls", 1, 15, lambda x: None)  # For Gaussian blur

# ---------------------------
# Board Detection (Hexagon) - OPTIONAL
# ---------------------------
def detect_board(resized_image):
    """
    Attempt to detect the hexagonal outline of the board using contour detection.
    """
    gray = cv2.cvtColor(resized_image, cv2.COLOR_BGR2GRAY)
    
    # Dynamically read trackbar values
    low_threshold = cv2.getTrackbarPos("Low Threshold", "Canny Controls")
    high_threshold = cv2.getTrackbarPos("High Threshold", "Canny Controls")
    blur_kernel = cv2.getTrackbarPos("Blur Kernel", "Canny Controls") * 2 + 1  # Ensure odd kernel size
    # Apply Gaussian blur
    blurred = cv2.GaussianBlur(gray, (blur_kernel, blur_kernel), 0)
    
    # Adaptive Thresholding
    thresholded = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2)

    # Canny Edge Detection
    edges = cv2.Canny(blurred, low_threshold, high_threshold)
     
    # Display intermediate results for debugging
    cv2.imshow("Thresholded", thresholded)
    cv2.imshow("Canny Edges", edges)
    
    

    # Morphological Closing to close gaps
    kernel = np.ones((7, 7), np.uint8)  #  first parameter is the kernel size, second is the shape of the kernel
    closed_edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel)
    cv2.imshow("Closed Edges", closed_edges)
    

    combined_edges = cv2.bitwise_and(thresholded, edges)
    cv2.imshow("Combined Edges", combined_edges)

    # Find Contours
    contours, _ = cv2.findContours(closed_edges, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)

    if not contours:
        with open('debug_info_board.txt', 'a') as f:
            f.write("No contours found\n")
        return None

    board_contour = None
    max_area = 0
    for contour in contours:
        area = cv2.contourArea(contour)
        min_contour_area = resized_image.shape[0] * resized_image.shape[1] * 0.01
        if area < min_contour_area:
            continue

        peri = cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, 0.015 * peri, True)

        # Check for ~6 sides (hexagon), but allow some flexibility
        if 6 <= len(approx) <= 18 and area > max_area:
            board_contour = contour
            max_area = area

    if board_contour is not None:
        logging.info(f"Board contour area: {max_area}")
        cv2.drawContours(resized_image, [board_contour], -1, (0, 255, 0), 2)
        cv2.imshow("Detected Board", resized_image)
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
# Marble Detection
# ---------------------------
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
        if 10 < radius < 60:
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

def detect_marbles(hsv_image, draw_image, board_contour):
    """
    Using the current trackbar-based HSV thresholds for green/red,
    detect marbles and return a combined list of them.
    Only marbles inside the board are considered.
    """
    # Get dynamic HSV ranges
    green_lower, green_upper = get_green_color_ranges()
    red_lower1, red_upper1, red_lower2, red_upper2 = get_red_color_ranges()

    # Create masks for green and red
    green_mask = cv2.inRange(hsv_image, green_lower, green_upper)
    red_mask1 = cv2.inRange(hsv_image, red_lower1, red_upper1)
    red_mask2 = cv2.inRange(hsv_image, red_lower2, red_upper2)
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)

    # Morphological cleaning
    kernel = np.ones((5, 5), np.uint8)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_OPEN, kernel, iterations=2)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_OPEN, kernel, iterations=2)
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    # FOR DEBUG: Show these masks
    cv2.imshow("Green Mask", green_mask)
    cv2.imshow("Red Mask", red_mask)    
    
    # Detect circles
    green_marbles = detect_and_draw_circles(green_mask, draw_image, "green", board_contour)
    red_marbles = detect_and_draw_circles(red_mask, draw_image, "red", board_contour)

    return green_marbles + red_marbles

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

# ---------------------------
# Detect Board Cells (Holes) on Empty Board
# ---------------------------

def create_hough_trackbars():
    cv2.namedWindow("Cell Hough Controls", cv2.WINDOW_NORMAL)
    cv2.resizeWindow("Cell Hough Controls", 400, 300)
    cv2.createTrackbar("Param1", "Cell Hough Controls", 500, 1000, lambda x: None)  # Canny high threshold, lower detects more edges
    cv2.createTrackbar("Param2", "Cell Hough Controls", 12, 200, lambda x: None)  # Accumulator threshold, lower detects more circles
    cv2.createTrackbar("MinDist", "Cell Hough Controls", 30, 200, lambda x: None)
    cv2.createTrackbar("MinRadius", "Cell Hough Controls", 15, 50, lambda x: None)
    cv2.createTrackbar("MaxRadius", "Cell Hough Controls", 25, 50, lambda x: None)

def get_hough_params():
    p1 = cv2.getTrackbarPos("Param1", "Cell Hough Controls")
    p2 = cv2.getTrackbarPos("Param2", "Cell Hough Controls")
    md = cv2.getTrackbarPos("MinDist", "Cell Hough Controls")
    minr = cv2.getTrackbarPos("MinRadius", "Cell Hough Controls")
    maxr = cv2.getTrackbarPos("MaxRadius", "Cell Hough Controls")
    return p1, p2, md, minr, maxr

def detect_board_cells(empty_board_image, board_contour, debug=False):
    """
    Detect all the cell (hole) positions on an empty board,
    but preserve only those inside the detected board contour.
    
    Returns:
        List of (x, y) circle centers within the board.
    """
    gray = cv2.cvtColor(empty_board_image, cv2.COLOR_BGR2GRAY)
    gray = cv2.medianBlur(gray, 5)

    # Read the dynamic trackbars
    param1, param2, minDist, minRadius, maxRadius = get_hough_params()

    # Optionally adjust brightness for better detection
    gray = cv2.convertScaleAbs(gray, alpha=1.5, beta=0)
    
    

    # Perform Hough Circle detection
    circles = cv2.HoughCircles(
        gray,
        cv2.HOUGH_GRADIENT,
        dp=1.1,
        minDist=minDist,
        param1=param1,
        param2=param2,
        minRadius=minRadius,
        maxRadius=maxRadius
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

            cells.append((x, y))
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

def print_text_board(populated_layout, cell_occupancy):
    """
    Print each row of the 'populated_layout'.
    For each cell:
      - 'X' if None (meaning no circle detected there),
      - '.' if empty (occupancy is None),
      - 'G' if a green marble is present,
      - 'R' if a red marble is present.
    """
    
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

def assign_marbles_to_cells(cells, marble_positions, base_threshold=20, debug=False):
    """
    Assign each marble to the nearest available cell within the threshold.
    Ensures that each cell is assigned to at most one marble.
    Returns: dict { (cx, cy): "green"/"white"/None }
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

# ---------------------------
# Main Function
# ---------------------------
def main():
    try:
        create_hough_trackbars()
        create_canny_trackbars()

        # Preprocess the empty board image
        empty_blurred, empty_hsv = preprocess_image("board_empty_test1.jpeg", debug=True)
        
        cv2.imshow("Empty Board", empty_blurred)

        # Detect the hexagonal board contour
        board_contour = detect_board(empty_blurred)

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

        # Create trackbars for marble detection
        create_green_trackbars()
        create_red_trackbars()
        current_blurred, current_hsv = preprocess_image("board_current_red_test1.jpeg", debug=False)

        while True:
            temp_display = current_blurred.copy()
            all_marbles = detect_marbles(current_hsv, temp_display, board_contour)
            cell_occupancy = assign_marbles_to_cells(empty_cells, all_marbles, base_threshold=45, debug=True)

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

    cv2.destroyAllWindows()

# ---------------------------
# Entry Point
# ---------------------------
if __name__ == "__main__":
    main()