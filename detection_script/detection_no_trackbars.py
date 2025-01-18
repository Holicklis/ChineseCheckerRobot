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

def preprocess_image(image, max_dim=1600, debug=False):
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

    # if debug:
    #     cv2.imshow("Preprocessed Image", blurred)

    return blurred, hsv_blurred

# ---------------------------
# Marble Detection Parameters (Predefined)
# ---------------------------
# Predefined HSV ranges for green marbles
GREEN_LOWER = np.array([36, 0, 0])      # [H, S, V]
GREEN_UPPER = np.array([86, 255, 255])

# Predefined HSV ranges for red marbles (two ranges to cover hue wrap-around)
RED_LOWER1 = np.array([0, 100, 100])
RED_UPPER1 = np.array([10, 255, 255])
RED_LOWER2 = np.array([160, 100, 100])
RED_UPPER2 = np.array([180, 255, 255])

# Predefined Hough Circle Detection Parameters for Cells
HOUGH_DP = 1.1
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
MARBLE_HOUGH_MIN_RADIUS = 10
MARBLE_HOUGH_MAX_RADIUS = 60

# Base threshold for marble to cell assignment
BASE_THRESHOLD = 45

# ---------------------------
# Board Detection (Hexagon) - OPTIONAL
# ---------------------------
def detect_board(resized_image):
    """
    Attempt to detect the hexagonal outline of the board using contour detection.
    """
    gray = cv2.cvtColor(resized_image, cv2.COLOR_BGR2GRAY)
    
    # Apply Gaussian blur
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    
    # Adaptive Thresholding
    thresholded = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 11, 2)

    # Canny Edge Detection
    edges = cv2.Canny(blurred, 50, 150)
    
    # cv2.imshow("Thresholded", thresholded)
    # cv2.imshow("Edges", edges)

    # Morphological Closing to close gaps
    kernel = np.ones((7, 7), np.uint8)
    closed_edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, kernel)
    # cv2.imshow("Closed Edges", closed_edges)

    combined_edges = cv2.bitwise_and(thresholded, edges)
    # cv2.imshow("Combined Edges", combined_edges)

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
        # cv2.imshow("Detected Board", resized_image)
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
    gray = cv2.cvtColor(empty_board_image, cv2.COLOR_BGR2GRAY)
    gray = cv2.medianBlur(gray, 5)
    
    gray = cv2.convertScaleAbs(gray, alpha=1.5, beta=0)

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
        # cv2.imshow("Detected Board Cells (Filtered)", empty_board_image)
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

def detect_marbles(hsv_image, draw_image, board_contour):
    """
    Using predefined HSV thresholds for green/red,
    detect marbles and return a combined list of them.
    Only marbles inside the board are considered.
    """
    # Create masks for green and red
    green_mask = cv2.inRange(hsv_image, GREEN_LOWER, GREEN_UPPER)
    red_mask1 = cv2.inRange(hsv_image, RED_LOWER1, RED_UPPER1)
    red_mask2 = cv2.inRange(hsv_image, RED_LOWER2, RED_UPPER2)
    red_mask = cv2.bitwise_or(red_mask1, red_mask2)

    # Morphological cleaning
    kernel = np.ones((5, 5), np.uint8)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_OPEN, kernel, iterations=2)
    green_mask = cv2.morphologyEx(green_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    #save greenmask to directory debug_images
    cv2.imwrite('debug_images/green_mask.jpg', green_mask)
    
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_OPEN, kernel, iterations=2)
    red_mask = cv2.morphologyEx(red_mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    
    # FOR DEBUG: Show these masks
    # cv2.imshow("Green Mask", green_mask)
    # cv2.imshow("Red Mask", red_mask)    
    
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

        # Preprocess the current board image
        current_image = cv2.imread("board_current_red_test1.jpeg")
        current_blurred, current_hsv = preprocess_image(current_image, debug=False)

        while True:
            temp_display = current_blurred.copy()
            all_marbles = detect_marbles(current_hsv, temp_display, board_contour)
            cell_occupancy = assign_marbles_to_cells(empty_cells, all_marbles, base_threshold=BASE_THRESHOLD, debug=True)

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
            # cv2.imshow("Final Board State", temp_display)

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