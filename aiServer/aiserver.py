from flask import Flask, request, jsonify
import logging
import json
import sys
import numpy as np
from typing import List, Tuple, Dict, Optional
import time
import os

# Import your existing game classes
from Board import Board
from Tile import Tile
from Piece import Piece
from Players import Player_Computer

# Import the board state parser
from BoardStateParser import normalize_board_state

app = Flask(__name__)

# Configure logging
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
                    handlers=[
                        logging.FileHandler("aiserver.log"),
                        logging.StreamHandler()
                    ])
logger = logging.getLogger(__name__)

# Default AI settings
DEFAULT_DEPTH = 4
DEFAULT_EVAL_FUNC = 1
USE_HEURISTIC = True

# Create a debug directory if it doesn't exist
DEBUG_DIR = "debug_logs"
os.makedirs(DEBUG_DIR, exist_ok=True)

###############################################################################
# Mapping from coordinate-based board representation to internal Board object
###############################################################################
class BoardMapper:
    def __init__(self):
        self.board = Board()
        # Store mappings between coordinates and tiles
        self.coord_to_tile: Dict[Tuple[int, int], Tile] = {}
        self.tile_to_coord: Dict[Tile, Tuple[int, int]] = {}
        self._init_mappings()
        
    def _init_mappings(self):
        """Create mappings between coordinate representation and internal tiles"""
        # row_offsets = [6, 5, 4, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 4, 5, 6]
        row_offsets = [0]*17 
        for i, row in enumerate(self.board.board_row_tiles):
            for j, tile in enumerate(row):
                # Calculate x,y coordinates
                x = j + row_offsets[i]
                y = i
                self.coord_to_tile[(x, y)] = tile
                self.tile_to_coord[tile] = (x, y)
    
    def get_tile_at_coord(self, x: int, y: int) -> Optional[Tile]:
        """Get the tile at the given coordinates"""
        return self.coord_to_tile.get((x, y))
    
    def get_coord_of_tile(self, tile: Tile) -> Tuple[int, int]:
        """Get the coordinates of the given tile"""
        return self.tile_to_coord.get(tile)
    
    def update_board_from_matrix(self, board_matrix: List[List[str]]) -> None:
        """Update the board based on a 2D matrix representation"""
        # First, clear all pieces from the board
        for tile in self.board.board_tiles:
            tile.set_empty()
            
        # Set pieces based on the matrix
        for y in range(len(board_matrix)):
            for x in range(len(board_matrix[y])):
                cell_val = board_matrix[y][x]
                if cell_val in ('O', 'X'):  # Player pieces
                    tile = self.get_tile_at_coord(x, y)
                    if tile is not None:
                        # Create and place the appropriate piece
                        piece = Piece(cell_val)
                        tile.set_piece(piece)
                        
    
    
    def get_ai_move_sequence(self, is_player1: bool, 
                         depth: int = DEFAULT_DEPTH, 
                         eval_func: int = DEFAULT_EVAL_FUNC,
                         use_heuristic: bool = USE_HEURISTIC) -> List[Tuple[int,int]]:
        """
        Get the best multi-step path (sequence of tiles) from the AI,
        then convert it to a list of (x,y) coordinate pairs.
        """
        # Create AI player
        ai_player = Player_Computer("AI1" if is_player1 else "AI2", eval_func, depth)
        
        # Set heuristic if needed
        if use_heuristic:
            def heuristic(tile_origin, tile_destination) -> bool:
                # Enforce directional movement based on player's position
                if is_player1:
                    return self.board.get_row_index(tile_destination) >= self.board.get_row_index(tile_origin)
                else:
                    return self.board.get_row_index(tile_destination) <= self.board.get_row_index(tile_origin)
            ai_player.set_heuristic(heuristic)
        
        # Get the best path of Tiles
        best_path = ai_player.get_move(self.board)

        # If no path is found, return an empty list
        if not best_path:
            return []

        # Convert the path of Tiles -> list of coordinate pairs
        coord_path = []
        for t in best_path:
            xy = self.get_coord_of_tile(t)
            if xy is not None:
                coord_path.append(xy)
        
        return coord_path

    def validate_move_sequence(self, path_coords):
        """Validate that the move sequence is valid"""
        if len(path_coords) < 2:
            return False, "Path too short"
        
        # Check each step in the path
        for i in range(len(path_coords) - 1):
            origin_x, origin_y = path_coords[i]
            dest_x, dest_y = path_coords[i + 1]
            
            # Get tiles
            origin_tile = self.get_tile_at_coord(origin_x, origin_y)
            dest_tile = self.get_tile_at_coord(dest_x, dest_y)
            
            logger.info(f"Checking move from ({origin_x},{origin_y}) to ({dest_x},{dest_y})")
            
            
            
            
            if origin_tile is None or dest_tile is None:
                error_msg = f"Invalid move: Tile not found at ({origin_x},{origin_y}) or ({dest_x},{dest_y})"
                logger.warning(error_msg)
                return False, error_msg
            
            if origin_tile.is_empty():
                error_msg = f"Invalid move: No piece at origin ({origin_x},{origin_y})"
                logger.warning(error_msg)
                return False, error_msg
                
            if not dest_tile.is_empty():
                error_msg = f"Invalid move: Destination is not empty at ({dest_x},{dest_y})"
                logger.warning(error_msg)
                return False, error_msg
            
            # Check if dest_tile is a valid move from origin_tile
            valid_moves = list(self.board.get_all_valid_moves(origin_tile))
            if dest_tile not in valid_moves:
                error_msg = f"Invalid move: Cannot move from ({origin_x},{origin_y}) to ({dest_x},{dest_y})"
                logger.warning(error_msg)
                return False, error_msg
        
        return True, "Valid move sequence"

    def save_debug_board_file(self, filename, board_state=None, move_sequence=None):
        """Save board visualization using Board's built-in to_string method"""
        with open(os.path.join(DEBUG_DIR, filename), 'w') as f:
            # Write current timestamp
            f.write(f"=== Debug Board State: {time.strftime('%Y-%m-%d %H:%M:%S')} ===\n\n")
            
            # Write the original board state if provided
            if board_state:
                f.write("Original board state from app:\n")
                for row in board_state:
                    f.write(row + "\n")
                f.write("\n")
            
            # Write the current board representation using Board's to_string method
            f.write("Current board state:\n")
            f.write(self.board.to_string())
            f.write("\n")
            
            # If a move sequence is provided, highlight it
            if move_sequence and len(move_sequence) >= 2:
                # Collect tiles to highlight
                tiles_to_highlight = []
                for x, y in move_sequence:
                    tile = self.get_tile_at_coord(x, y)
                    if tile:
                        tiles_to_highlight.append(tile)
                
                # Draw the board with numbered tiles for the move sequence
                f.write("Move sequence visualization:\n")
                f.write(self.board.to_string(tiles_to_highlight))
                
                # Add a legend explaining the numbered tiles
                f.write("\nMove sequence legend:\n")
                for i, (x, y) in enumerate(move_sequence):
                    step_name = "Start" if i == 0 else "End" if i == len(move_sequence) - 1 else f"Step {i}"
                    f.write(f"{i+1}: {step_name} at ({x}, {y})\n")
                
                # Validate the move and show results
                is_valid, reason = self.validate_move_sequence(move_sequence)
                f.write(f"\nMove validation: {'Valid' if is_valid else 'Invalid'}\n")
                if not is_valid:
                    f.write(f"Reason: {reason}\n")
                    
                    # If invalid, check what valid moves exist from the start position
                    start_x, start_y = move_sequence[0]
                    start_tile = self.get_tile_at_coord(start_x, start_y)
                    
                    if start_tile and not start_tile.is_empty():
                        f.write(f"\nValid moves from ({start_x}, {start_y}):\n")
                        valid_destinations = []
                        for valid_dest_tile in self.board.get_all_valid_moves(start_tile):
                            valid_dest_coord = self.get_coord_of_tile(valid_dest_tile)
                            if valid_dest_coord:
                                valid_destinations.append(valid_dest_coord)
                        
                        if valid_destinations:
                            for dest_x, dest_y in valid_destinations:
                                f.write(f"- Can move to: ({dest_x}, {dest_y})\n")
                        else:
                            f.write("No valid moves available.\n")
                            
            # Add a coordinate reference guide for debugging the coordinate system
            f.write("\nCoordinate system reference:\n")
            for y in range(17):  # For each row
                row_content = ""
                for x in range(13):  # Max possible x value
                    tile = self.get_tile_at_coord(x, y)
                    if tile:
                        if tile.is_empty():
                            row_content += f"({x},{y}):. "
                        else:
                            row_content += f"({x},{y}):{tile.get_piece().get_color()} "
                if row_content:
                    f.write(f"Row {y}: {row_content}\n")

###############################################################################
# Create global board mapper
###############################################################################
board_mapper = BoardMapper()

###############################################################################
# Routes
###############################################################################

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({"status": "ok"})

@app.route('/get_ai_move', methods=['POST'])
def get_ai_move():
    """
    Get the best multi-step move sequence for the current board state,
    returning an array of coordinates that includes intermediate jumps 
    (e.g. [ (x0,y0), (x1,y1), (x2,y2), ... ]).
    """
    try:
        # Get request data
        data = request.json
        logger.info(f"Received request: {json.dumps(data)}")
        
        board_state = data.get('board_state', [])
        is_player1 = data.get('is_player1', False)
        depth = data.get('depth', DEFAULT_DEPTH)
        eval_func = data.get('eval_func', DEFAULT_EVAL_FUNC)
        use_heuristic = data.get('use_heuristic', USE_HEURISTIC)
        
        # Use the board state parser to normalize the format
        board_matrix = normalize_board_state(board_state)
        
        # Update the internal board state
        board_mapper.update_board_from_matrix(board_matrix)
        
        board_str = board_mapper.board.to_string()
        logger.info("\n" + board_str)
        
        # Save the board state before AI move
        timestamp = int(time.time())
        board_mapper.save_debug_board_file(
            f"board_before_move_{timestamp}.txt",
            board_state
        )
        
        # Get the AI's multi-step path
        path_coords = board_mapper.get_ai_move_sequence(is_player1, depth, eval_func, use_heuristic)
        
        # Save the board state with the move sequence
        board_mapper.save_debug_board_file(
            f"board_with_move_{timestamp}.txt",
            board_state,
            path_coords
        )
        
        # If no move is possible, return appropriate status
        if not path_coords:
            logger.warning("No valid moves found")
            return jsonify({
                "status": "no_move_possible", 
                "message": "No valid moves found for the current board state"
            }), 200
        
        # Validate the move sequence
        is_valid, reason = board_mapper.validate_move_sequence(path_coords)
        if not is_valid:
            logger.warning(f"AI generated invalid move: {reason}")
            return jsonify({
                "status": "invalid_move",
                "message": f"AI generated an invalid move: {reason}",
                "move_sequence": [{"x": x, "y": y} for x, y in path_coords],
                "debug_file": f"board_with_move_{timestamp}.txt"
            }), 200
        
        # Build JSON-friendly structure
        move_sequence = []
        for (x, y) in path_coords:
            move_sequence.append({"x": x, "y": y})
        
        response = {
            "status": "success",
            "move_sequence": move_sequence,
            "debug_file": f"board_with_move_{timestamp}.txt"
        }
        logger.info(f"Sending response: {response}")
        return jsonify(response)
    
    except Exception as e:
        logger.error(f"Error processing request: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/debug_board_state', methods=['POST'])
def debug_board_state():
    """Debug endpoint to specifically analyze the board state parsing"""
    try:
        data = request.json
        board_state = data.get('board_state', [])
        
        # Use the board state parser to normalize the format
        board_matrix = normalize_board_state(board_state)
        
        # Update the internal board state
        board_mapper.update_board_from_matrix(board_matrix)
        
        # Create a detailed representation of the board with coordinates
        detailed_board = []
        for y in range(len(board_matrix)):
            row_info = []
            for x in range(len(board_matrix[y])):
                cell_val = board_matrix[y][x]
                # Get the actual coordinates this would map to
                tile = board_mapper.get_tile_at_coord(x, y)
                if tile is not None:
                    is_empty = tile.is_empty()
                    piece_color = None if is_empty else tile.get_piece().get_color()
                    row_info.append({
                        "matrix_pos": [x, y],
                        "cell_value": cell_val,
                        "is_empty": is_empty,
                        "piece_color": piece_color
                    })
            detailed_board.append(row_info)
        
        # Examine row 13 specifically
        row_13_info = []
        if len(board_matrix) > 13:
            for x in range(len(board_matrix[13])):
                cell_val = board_matrix[13][x]
                # Get the mapped coordinates
                mapped_x = x + board_mapper._init_mappings.__closure__[0].cell_value[13]  # Access the offset
                tile = board_mapper.get_tile_at_coord(mapped_x, 13)
                if tile is not None:
                    is_empty = tile.is_empty()
                    piece_color = None if is_empty else tile.get_piece().get_color()
                    row_13_info.append({
                        "matrix_index": x,
                        "mapped_coord": [mapped_x, 13],
                        "cell_value": cell_val,
                        "is_empty": is_empty,
                        "piece_color": piece_color
                    })
        
        return jsonify({
            "status": "success",
            "input_board": board_state,
            "normalized_matrix": board_matrix,
            "row_13_detail": row_13_info,
            "offsets": board_mapper._init_mappings.__closure__[0].cell_value
        })
    
    except Exception as e:
        logger.error(f"Error in debug_board_state: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500


@app.route('/test_move', methods=['POST'])
def test_move():
    """Test a specific move to see if it's valid"""
    try:
        data = request.json
        board_state = data.get('board_state', [])
        from_x = data.get('from_x')
        from_y = data.get('from_y')
        to_x = data.get('to_x')
        to_y = data.get('to_y')
        
        if from_x is None or from_y is None or to_x is None or to_y is None:
            return jsonify({"status": "error", "message": "Missing coordinates"}), 400
        
        # Use the board state parser to normalize the format
        board_matrix = normalize_board_state(board_state)
        
        # Update the internal board state
        board_mapper.update_board_from_matrix(board_matrix)
        
        # Get tiles for the move
        from_tile = board_mapper.get_tile_at_coord(from_x, from_y)
        to_tile = board_mapper.get_tile_at_coord(to_x, to_y)
        
        # Validate basic conditions
        if from_tile is None or to_tile is None:
            return jsonify({
                "status": "invalid",
                "message": f"One or both coordinates do not exist on the board: ({from_x},{from_y}) to ({to_x},{to_y})"
            })
        
        if from_tile.is_empty():
            return jsonify({
                "status": "invalid",
                "message": f"No piece at origin: ({from_x},{from_y})"
            })
        
        if not to_tile.is_empty():
            return jsonify({
                "status": "invalid",
                "message": f"Destination is not empty: ({to_x},{to_y})"
            })
        
        # Check if the move is valid according to game rules
        valid_moves = list(board_mapper.board.get_all_valid_moves(from_tile))
        is_valid = to_tile in valid_moves
        
        # Save debug file with the test move
        timestamp = int(time.time())
        debug_filename = f"test_move_{timestamp}.txt"
        board_mapper.save_debug_board_file(
            debug_filename, 
            board_state,
            [(from_x, from_y), (to_x, to_y)]
        )
        
        if is_valid:
            return jsonify({
                "status": "valid",
                "message": f"Move from ({from_x},{from_y}) to ({to_x},{to_y}) is valid",
                "debug_file": debug_filename
            })
        else:
            # Get valid moves to help debugging
            valid_destinations = []
            for valid_dest_tile in valid_moves:
                valid_dest_coord = board_mapper.get_coord_of_tile(valid_dest_tile)
                if valid_dest_coord:
                    valid_destinations.append(valid_dest_coord)
            
            return jsonify({
                "status": "invalid",
                "message": f"Move from ({from_x},{from_y}) to ({to_x},{to_y}) is not valid",
                "valid_destinations": valid_destinations,
                "debug_file": debug_filename
            })
    
    except Exception as e:
        logger.error(f"Error in test_move: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500


if __name__ == '__main__':
    port = 5002  # Different from your OpenCV server port (5001)
    logger.info(f"Starting Chinese Checkers AI server on port {port}")
    app.run(host='0.0.0.0', port=port, debug=True)