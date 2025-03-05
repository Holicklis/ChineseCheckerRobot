def normalize_board_state(board_strings):
    """
    Converts a list of strings representing the board state into a 2D matrix.
    Handles the triangular format from the camera detection where rows have varying lengths.
    
    Input formats supported:
    1. Triangular format with G/R/. (camera detection)
    2. Triangular format with O/X/. (already converted)
    3. Rectangular format with O/X/. (already padded)
    
    Returns a properly formatted 2D matrix suitable for the AI's board representation.
    """
    normalized_matrix = []
    
    # Define the expected number of cells per row in the Chinese Checkers board
    expected_cells_per_row = [1, 2, 3, 4, 13, 12, 11, 10, 9, 10, 11, 12, 13, 4, 3, 2, 1]
    
    # If the input roughly matches the expected format, process it directly
    if len(board_strings) == len(expected_cells_per_row):
        for row_idx, row_string in enumerate(board_strings):
            # Split by spaces and process each element as a piece
            pieces = row_string.strip().split()
            row = []
            
            for piece in pieces:
                if piece in ['O', 'X', '.', 'G', 'R']:
                    # Convert G to O and R to X if needed
                    if piece == 'G':
                        row.append('O')
                    elif piece == 'R':
                        row.append('X')
                    else:
                        row.append(piece)
            
            # Check if we have the expected number of cells
            if len(row) != expected_cells_per_row[row_idx]:
                # If not, pad the row to match expected width
                padded_row = ['.'] * expected_cells_per_row[row_idx]
                for i in range(min(len(row), len(padded_row))):
                    padded_row[i] = row[i]
                row = padded_row
            
            normalized_matrix.append(row)
    else:
        # For formats that are already in matrix form or other formats
        # We'll try to create a valid board representation based on the existing data
        
        # First, find non-empty rows
        valid_rows = [row for row in board_strings if any(char in ['O', 'X', '.', 'G', 'R'] for char in row)]
        
        # Process each valid row
        for i, row_string in enumerate(valid_rows):
            # Split by spaces and process each element as a piece
            pieces = row_string.strip().split()
            row = []
            
            for piece in pieces:
                if piece in ['O', 'X', '.', 'G', 'R']:
                    if piece == 'G':
                        row.append('O')
                    elif piece == 'R':
                        row.append('X')
                    else:
                        row.append(piece)
            
            # Determine row index in the standard board
            row_idx = min(i, len(expected_cells_per_row) - 1)
            
            # Pad or trim to match expected width
            if len(row) < expected_cells_per_row[row_idx]:
                row.extend(['.'] * (expected_cells_per_row[row_idx] - len(row)))
            elif len(row) > expected_cells_per_row[row_idx]:
                row = row[:expected_cells_per_row[row_idx]]
                
            normalized_matrix.append(row)
        
        # If we don't have enough rows, add empty rows
        while len(normalized_matrix) < len(expected_cells_per_row):
            row_idx = len(normalized_matrix)
            normalized_matrix.append(['.'] * expected_cells_per_row[row_idx])
    
    return normalized_matrix