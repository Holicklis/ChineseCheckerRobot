from flask import Flask, request, jsonify
import logging
import json
import sys
import numpy as np
from typing import List, Tuple, Dict, Optional
import time
import os
import copy

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
                        
    
    
    def get_ai_move_sequence(self, is_player1: bool, depth: int, eval_func: int, use_heuristic: bool):
        """
        Get the best complete path of Tiles from the AI.
        The AI already handles multi-step paths including jumps.
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
        
        # Get the best path - this will already include all jumps
        return ai_player.get_move(self.board)

    def validate_move_sequence(self, path_coords):
        """
        Validate that the move sequence is valid according to Chinese Checkers rules.
        The sequence should already be complete with all intermediate jumps.
        """
        if len(path_coords) < 2:
            return False, "Path too short"

        # Save the original board state
        original_state = {tile: tile.get_piece() for tile in self.board.board_tiles}

        try:
            # Get tiles for the path
            path_tiles = []
            for x, y in path_coords:
                tile = self.get_tile_at_coord(x, y)
                if tile is None:
                    return False, f"Invalid coordinate ({x},{y})"
                path_tiles.append(tile)
            
            # First tile should have a piece
            if path_tiles[0].is_empty():
                return False, f"No piece at the starting position ({path_coords[0][0]},{path_coords[0][1]})"
            
            # Validate the path using the Board's validation logic
            for i in range(len(path_tiles) - 1):
                # Check if the move from i to i+1 is valid
                from_tile = path_tiles[i]
                to_tile = path_tiles[i+1]
                
                # Destination must be empty
                if not to_tile.is_empty():
                    return False, f"Destination ({path_coords[i+1][0]},{path_coords[i+1][1]}) is not empty"
                
                # Either adjacent move or jump
                if to_tile in from_tile.get_neighbours().values():
                    # Adjacent move - valid
                    pass
                else:
                    # Must be a jump - validate it
                    if not self.board.validate_jump(from_tile, to_tile):
                        return False, f"Invalid jump from ({path_coords[i][0]},{path_coords[i][1]}) to ({path_coords[i+1][0]},{path_coords[i+1][1]})"
                
                # Apply the move for the next step validation
                piece = from_tile.get_piece()
                to_tile.set_piece(piece)
                from_tile.set_empty()
            
            return True, "Valid move sequence"
        finally:
            # Restore original board state
            for tile, piece in original_state.items():
                tile.set_piece(piece)

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
    Returns the best move sequence for the current board state.
    The AI will handle path finding internally, including all intermediate jumps.
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
        
        # Normalize and update board state
        board_matrix = normalize_board_state(board_state)
        board_mapper.update_board_from_matrix(board_matrix)
        
        # Log the current board
        logger.info("\n" + board_mapper.board.to_string())
        
        # Save debug board state
        timestamp = int(time.time())
        debug_filename = f"board_before_move_{timestamp}.txt"
        board_mapper.save_debug_board_file(debug_filename, board_state)
        
        # Get AI move with complete path (including jumps)
        # The path is already complete as the AI handles jump paths internally
        path_tiles = board_mapper.get_ai_move_sequence(is_player1, depth, eval_func, use_heuristic)
        
        # If no valid move found
        if not path_tiles or len(path_tiles) < 2:
            logger.warning("No valid moves found")
            return jsonify({
                "status": "no_move_possible", 
                "message": "No valid moves found for the current board state"
            }), 200
        
        # Convert tile path to coordinates
        path_coords = [board_mapper.get_coord_of_tile(tile) for tile in path_tiles]
        logger.info(f"AI generated path: {path_coords}")
        
        # Validate the move sequence
        is_valid, reason = board_mapper.validate_move_sequence(path_coords)
        
        # Save debug board with the move
        debug_filename = f"board_with_move_{timestamp}.txt"
        board_mapper.save_debug_board_file(debug_filename, board_state, path_coords)
        
        if not is_valid:
            logger.warning(f"AI generated invalid move: {reason}")
            return jsonify({
                "status": "invalid_move",
                "message": f"AI generated an invalid move: {reason}",
                "move_sequence": [{"x": x, "y": y} for x, y in path_coords],
                "debug_file": debug_filename
            }), 200
        
        # Convert to JSON-friendly format
        move_sequence = [{"x": x, "y": y} for x, y in path_coords]
        
        response = {
            "status": "success",
            "move_sequence": move_sequence,
            "debug_file": debug_filename
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


@app.route('/test_move_path', methods=['POST'])
def test_move_path():
    """
    Test endpoint for debugging path finding and move validation.
    Allows testing specific paths and seeing all validation details.
    """
    try:
        data = request.json
        board_state = data.get('board_state', [])
        from_x = data.get('from_x')
        from_y = data.get('from_y')
        to_x = data.get('to_x')
        to_y = data.get('to_y')
        
        if None in (from_x, from_y, to_x, to_y):
            return jsonify({"status": "error", "message": "Missing coordinates"}), 400
        
        # Update board state
        board_matrix = normalize_board_state(board_state)
        board_mapper.update_board_from_matrix(board_matrix)
        
        # Log the board state
        logger.info("\nTesting move path with board state:")
        logger.info("\n" + board_mapper.board.to_string())
        
        # Get the tiles
        from_tile = board_mapper.get_tile_at_coord(from_x, from_y)
        to_tile = board_mapper.get_tile_at_coord(to_x, to_y)
        
        if from_tile is None or to_tile is None:
            return jsonify({"status": "error", "message": "Invalid coordinates"}), 400
        
        # Basic checks
        from_state = "empty" if from_tile.is_empty() else from_tile.get_piece().get_color()
        to_state = "empty" if to_tile.is_empty() else to_tile.get_piece().get_color()
        
        logger.info(f"From ({from_x},{from_y}): {from_state}")
        logger.info(f"To ({to_x},{to_y}): {to_state}")
        
        # Try to find a valid path
        result = {
            "from": {"x": from_x, "y": from_y, "state": from_state},
            "to": {"x": to_x, "y": to_y, "state": to_state},
            "direct_move": False,
            "jump_path": None,
            "validation": None
        }
        
        # Check if direct move is possible
        if from_tile.is_empty():
            result["validation"] = {"valid": False, "reason": "No piece at origin"}
        elif not to_tile.is_empty():
            result["validation"] = {"valid": False, "reason": "Destination is not empty"}
        elif to_tile in from_tile.get_neighbours().values():
            # Direct neighbor move
            result["direct_move"] = True
            result["validation"] = {"valid": True, "reason": "Valid adjacent move"}
        else:
            # Try to find a jump path
            jump_paths = board_mapper.board.get_all_jump_paths(from_tile)
            valid_path = None
            
            # Look for a path that ends at to_tile
            for path in jump_paths:
                if path[-1] == to_tile:
                    valid_path = path
                    break
            
            if valid_path:
                # Convert path to coordinates
                path_coords = [board_mapper.get_coord_of_tile(tile) for tile in valid_path]
                
                # Validate the path
                is_valid, reason = board_mapper.validate_move_sequence(path_coords)
                
                result["jump_path"] = {
                    "path": path_coords,
                    "valid": is_valid,
                    "reason": reason
                }
                
                result["validation"] = {"valid": is_valid, "reason": reason}
            else:
                result["validation"] = {"valid": False, "reason": "No valid jump path found"}
        
        # Save debug board
        timestamp = int(time.time())
        debug_filename = f"test_move_{timestamp}.txt"
        
        if result.get("jump_path") and result["jump_path"].get("path"):
            # Save with the jump path
            board_mapper.save_debug_board_file(debug_filename, board_state, result["jump_path"]["path"])
        else:
            # Save with just the start and end points
            board_mapper.save_debug_board_file(debug_filename, board_state, [(from_x, from_y), (to_x, to_y)])
        
        result["debug_file"] = debug_filename
        
        return jsonify({"status": "success", "result": result})
    
    except Exception as e:
        logger.error(f"Error in test_move_path: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500
    
def find_jump_path(board_mapper, start_coords, end_coords):
    """
    Finds a valid path of jumps from start to end coordinates.
    Returns the complete path including all intermediate jumps.
    """
    start_x, start_y = start_coords
    end_x, end_y = end_coords
    
    # Get tiles for start and end positions
    start_tile = board_mapper.get_tile_at_coord(start_x, start_y)
    end_tile = board_mapper.get_tile_at_coord(end_x, end_y)
    
    if start_tile is None or end_tile is None:
        return None
    
    # Check if this is an adjacent move (not a jump)
    for direction, neighbor in start_tile.get_neighbours().items():
        if neighbor == end_tile:
            # This is a direct adjacent move
            return [start_coords, end_coords]
    
    # Try to find a jump path using BFS
    queue = [(start_tile, [start_coords])]
    visited = {start_tile}
    
    while queue:
        current_tile, current_path = queue.pop(0)
        
        # Get all possible jump destinations from current_tile
        jump_destinations = []
        for direction, neighbor in current_tile.get_neighbours().items():
            if neighbor is not None and not neighbor.is_empty():
                # There's a piece we can potentially jump over
                landing = neighbor.get_neighbours().get(direction)
                if landing is not None and landing.is_empty() and landing not in visited:
                    jump_destinations.append(landing)
        
        # Process each jump destination
        for dest in jump_destinations:
            # Get coordinates of this destination
            dest_coords = board_mapper.get_coord_of_tile(dest)
            if dest_coords is None:
                continue
                
            # Create the new path with this jump
            new_path = current_path + [dest_coords]
            
            # If we've reached the target, return the path
            if dest == end_tile:
                return new_path
            
            # Otherwise, continue searching from this position
            visited.add(dest)
            queue.append((dest, new_path))
    
    # No path found
    return None

def trace_jump_paths(board_mapper, tile):
    """Debug utility to trace all possible jump paths from a given tile"""
    logger.info(f"Tracing all jump paths from tile: {tile}")
    
    paths = board_mapper.board.get_all_jump_paths(tile)
    logger.info(f"Found {len(paths)} jump paths")
    
    for i, path in enumerate(paths):
        path_coords = [board_mapper.get_coord_of_tile(t) for t in path]
        logger.info(f"Path {i+1}: {path_coords}")
    
    return paths
@app.route('/test_position', methods=['POST'])
def test_position():
    """
    Test endpoint that returns what piece is at a specific position.
    Useful for debugging coordinate mapping issues.
    """
    try:
        data = request.json
        board_state = data.get('board_state', [])
        x = data.get('position_x')
        y = data.get('position_y')
        
        if x is None or y is None:
            return jsonify({"status": "error", "message": "Missing coordinates"}), 400
        
        # Log the raw board state
        logger.info(f"Raw board state:")
        for row in board_state:
            logger.info(row)
        
        # Normalize and update board state
        board_matrix = normalize_board_state(board_state)
        board_mapper.update_board_from_matrix(board_matrix)
        
        # Log the board for reference
        logger.info("\nBoard after normalization:")
        logger.info("\n" + board_mapper.board.to_string())
        
        # Check the requested position
        tile = board_mapper.get_tile_at_coord(x, y)
        
        if tile is None:
            return jsonify({
                "status": "error", 
                "message": f"No tile at ({x},{y})",
                "raw_position": get_raw_position(board_state, x, y)
            })
        
        piece_info = "empty" if tile.is_empty() else tile.get_piece().get_color()
        
        # Also check the positions for jump path
        neighbors_info = {}
        for direction, neighbor in tile.get_neighbours().items():
            if neighbor is None:
                neighbors_info[direction] = None
            else:
                neighbor_piece = "empty" if neighbor.is_empty() else neighbor.get_piece().get_color()
                neighbor_coords = board_mapper.get_coord_of_tile(neighbor)
                neighbors_info[direction] = {
                    "coords": neighbor_coords,
                    "piece": neighbor_piece
                }
        
        return jsonify({
            "status": "success",
            "position": {"x": x, "y": y},
            "piece": piece_info,
            "is_empty": tile.is_empty(),
            "neighbors": neighbors_info,
            "raw_position": get_raw_position(board_state, x, y)
        })
    
    except Exception as e:
        logger.error(f"Error in test_position: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500

def get_raw_position(board_state, x, y):
    """Helper to get the raw value at a position in the board state"""
    try:
        if 0 <= y < len(board_state):
            row = board_state[y].strip().split()
            if 0 <= x < len(row):
                return row[x]
    except Exception as e:
        logger.error(f"Error accessing raw position: {e}")
    return None

@app.route('/analyze_path', methods=['POST'])
def analyze_path():
    """
    Analyze a specific path for jumping, checking if jumps are valid.
    """
    try:
        data = request.json
        board_state = data.get('board_state', [])
        path_coords = data.get('path', [])
        
        if not path_coords or len(path_coords) < 2:
            return jsonify({"status": "error", "message": "Invalid path provided"}), 400
        
        # Normalize and update board state
        board_matrix = normalize_board_state(board_state)
        board_mapper.update_board_from_matrix(board_matrix)
        
        # Log the board for reference
        logger.info("\nBoard for path analysis:")
        logger.info("\n" + board_mapper.board.to_string())
        
        # Analyze each step in the path
        path_analysis = []
        
        for i in range(len(path_coords) - 1):
            start_x, start_y = path_coords[i]
            end_x, end_y = path_coords[i + 1]
            
            start_tile = board_mapper.get_tile_at_coord(start_x, start_y)
            end_tile = board_mapper.get_tile_at_coord(end_x, end_y)
            
            step_info = {
                "from": {"x": start_x, "y": start_y},
                "to": {"x": end_x, "y": end_y},
                "valid": False,
                "reason": "Unknown"
            }
            
            # Basic validation
            if start_tile is None:
                step_info["reason"] = f"No tile at starting position ({start_x},{start_y})"
            elif end_tile is None:
                step_info["reason"] = f"No tile at ending position ({end_x},{end_y})"
            elif start_tile.is_empty():
                step_info["reason"] = f"No piece at starting position ({start_x},{start_y})"
            elif not end_tile.is_empty():
                step_info["reason"] = f"Destination is not empty at ({end_x},{end_y})"
            else:
                # Check if this is an adjacent move
                is_adjacent = False
                for _, neighbor in start_tile.get_neighbours().items():
                    if neighbor == end_tile:
                        is_adjacent = True
                        break
                
                if is_adjacent:
                    step_info["valid"] = True
                    step_info["reason"] = "Valid adjacent move"
                    step_info["move_type"] = "adjacent"
                else:
                    # Must be a jump - check if it's valid
                    is_valid_jump = False
                    jump_over = None
                    
                    for direction, neighbor in start_tile.get_neighbours().items():
                        if neighbor is not None and not neighbor.is_empty():
                            # There's a piece we might jump over
                            jump_landing = neighbor.get_neighbours().get(direction)
                            if jump_landing == end_tile:
                                is_valid_jump = True
                                neighbor_coords = board_mapper.get_coord_of_tile(neighbor)
                                jump_over = {"coords": neighbor_coords, "piece": neighbor.get_piece().get_color()}
                                break
                    
                    if is_valid_jump:
                        step_info["valid"] = True
                        step_info["reason"] = "Valid jump"
                        step_info["move_type"] = "jump"
                        step_info["jump_over"] = jump_over
                    else:
                        step_info["reason"] = f"Invalid jump from ({start_x},{start_y}) to ({end_x},{end_y})"
            
            path_analysis.append(step_info)
            
            # If this step is valid, simulate the move for the next step
            if step_info["valid"] and not start_tile.is_empty():
                piece = start_tile.get_piece()
                end_tile.set_piece(piece)
                start_tile.set_empty()
        
        # Save a debug file with the path
        timestamp = int(time.time())
        debug_filename = f"path_analysis_{timestamp}.txt"
        board_mapper.save_debug_board_file(debug_filename, board_state, path_coords)
        
        return jsonify({
            "status": "success",
            "path": path_coords,
            "analysis": path_analysis,
            "all_valid": all(step["valid"] for step in path_analysis),
            "debug_file": debug_filename
        })
    
    except Exception as e:
        logger.error(f"Error in analyze_path: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == '__main__':
    port = 5002  # Different from your OpenCV server port (5001)
    logger.info(f"Starting Chinese Checkers AI server on port {port}")
    app.run(host='0.0.0.0', port=port, debug=True)