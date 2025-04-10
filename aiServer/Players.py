from Board import Board
from Tile import Tile

CHARACTERS = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ"

def ask_person_for_piece(board: Board, is_player1: bool) -> Tile:
    tiles_with_movable_pieces: list[Tile] = list(filter(
        lambda t: any(board.get_all_valid_moves(t)), board.get_player1_tiles() if is_player1 else board.get_player2_tiles()
    ))
    board.print_board(tiles_with_movable_pieces, "".join(CHARACTERS))

    while True:
        n = input(f"Select a piece ({CHARACTERS[0]} - {CHARACTERS[len(tiles_with_movable_pieces)-1]})").strip().upper()
        if len(n) != 1:
            continue
        if n in CHARACTERS[ : len(tiles_with_movable_pieces)]:
            selected_tile = tiles_with_movable_pieces[CHARACTERS.index(n)]
            return selected_tile

def ask_person_for_tile_destination(board: Board, tile_origin: Tile) -> Tile:
    available_tile_destinations: list[Tile] = [tile for tile in board.get_all_valid_moves(tile_origin)]

    board.print_board(available_tile_destinations, CHARACTERS)

    while True:
        n = input(f"Select a tile to move to ({CHARACTERS[0]} - {CHARACTERS[len(available_tile_destinations)-1]})").strip().upper()
        if len(n) != 1:
            continue
        if n in CHARACTERS[ : len(available_tile_destinations)]:
            destination_tile = available_tile_destinations[ CHARACTERS.index(n) ]
            return destination_tile
def minimax_pruning(board: Board, depth: int, is_player1_turn: bool,
                    heuristic, use_eval_func_1: bool,
                    maximizing: bool = True,
                    alpha: int = -1_000_000_000,
                    beta: int = 1_000_000_000) -> tuple[int, list[Tile]]:
    # Base case
    if depth == 0 or board.has_game_ended():
        return board.get_score(is_player1_turn, use_eval_func_1) + depth, []

    if maximizing:
        max_points = float('-inf')
        best_path = []
        tiles_for_player = board.get_player1_tiles() if is_player1_turn else board.get_player2_tiles()
        found_any_move = False

        for tile_origin in tiles_for_player:
            # First collect all valid paths from this origin
            # This includes both single-step moves and complete jump paths
            possible_paths = []
            
            # 1. Get single-step moves
            try:
                for dest in board.get_all_valid_moves(tile_origin):
                    if heuristic(tile_origin, dest):
                        possible_paths.append([tile_origin, dest])
            except Exception as e:
                print(f"Error getting valid moves: {e}")
                continue  # Skip this tile if there's an error

            # 2. Get multi-step jump paths
            # This will find all valid multi-step jump sequences
            try:
                jump_paths = board.get_all_jump_paths(tile_origin)
                # Ensure jump_paths is always a list (empty if no jumps)
                if jump_paths is None:
                    jump_paths = []
                    
                for path in jump_paths:
                    # Only include paths with at least one jump
                    if len(path) > 1:
                        # Filter paths by heuristic
                        if heuristic(path[0], path[-1]):
                            possible_paths.append(path)
            except Exception as e:
                print(f"Error getting jump paths: {e}")
                # Continue with any single-step moves we found

            # 3. Evaluate each possible path
            for path in possible_paths:
                # Apply the complete path (might be single-step or multi-jump)
                try:
                    board.apply_path(path)
                except Exception as e:
                    print(f"Error applying path: {e}")
                    continue  # Skip this path if we can't apply it
                
                # Evaluate resulting position
                points, _ = minimax_pruning(board, depth - 1, is_player1_turn,
                                          heuristic, use_eval_func_1,
                                          maximizing=False, alpha=alpha, beta=beta)
                
                # Undo the path to restore board state
                try:
                    board.undo_path(path)
                except Exception as e:
                    print(f"Error undoing path: {e}")
                    # We need to somehow recover from this, maybe by manually 
                    # resetting the board to its original state
                
                found_any_move = True
                if points > max_points:
                    max_points = points
                    best_path = path
                
                alpha = max(alpha, points)
                if beta <= alpha:
                    break
            
            if beta <= alpha:
                break

        if not found_any_move:
            max_points = board.get_score(is_player1_turn, use_eval_func_1)
            best_path = []
        
        return max_points, best_path

    else:
        # Similar error handling for the minimizing branch
        min_points = float('inf')
        best_path = []
        tiles_for_opponent = board.get_player2_tiles() if is_player1_turn else board.get_player1_tiles()
        found_any_move = False

        for tile_origin in tiles_for_opponent:
            # Same approach as the maximizing branch with error handling
            possible_paths = []
            
            # 1. Get single-step moves with error handling
            try:
                for dest in board.get_all_valid_moves(tile_origin):
                    if heuristic(tile_origin, dest):
                        possible_paths.append([tile_origin, dest])
            except Exception as e:
                print(f"Error getting valid moves: {e}")
                continue
            
            # 2. Get multi-step jump paths with error handling
            try:
                jump_paths = board.get_all_jump_paths(tile_origin)
                # Ensure jump_paths is always a list
                if jump_paths is None:
                    jump_paths = []
                    
                for path in jump_paths:
                    if len(path) > 1:
                        if heuristic(path[0], path[-1]):
                            possible_paths.append(path)
            except Exception as e:
                print(f"Error getting jump paths: {e}")

            # 3. Evaluate each possible path with error handling
            for path in possible_paths:
                try:
                    board.apply_path(path)
                except Exception as e:
                    print(f"Error applying path: {e}")
                    continue
                
                points, _ = minimax_pruning(board, depth - 1, is_player1_turn,
                                          heuristic, use_eval_func_1,
                                          maximizing=True, alpha=alpha, beta=beta)
                
                try:
                    board.undo_path(path)
                except Exception as e:
                    print(f"Error undoing path: {e}")
                
                found_any_move = True
                if points < min_points:
                    min_points = points
                    best_path = path
                
                beta = min(beta, points)
                if beta <= alpha:
                    break
            
            if beta <= alpha:
                break

        if not found_any_move:
            min_points = board.get_score(is_player1_turn, use_eval_func_1)
            best_path = []
        
        return min_points, best_path

    
    
class Player():
    def __init__(self, name: str) -> None:
        self.name: str = name
    
    def get_name(self) -> str:
        return self.name
    
    def is_player1(self) -> bool:
        return "1" in self.get_name()


class Player_Computer(Player):
    DEFAULT_HEURISTIC = lambda x, y: True
    def __init__(self, name: str, eval_func_int: int, depth: int) -> None:
        super().__init__(name)
        self.eval_func: int = eval_func_int
        self.heuristic = Player_Computer.DEFAULT_HEURISTIC
        self.depth = depth
    
    def set_heuristic(self, f):
        self.heuristic = f

    def get_move(self, board: Board):
        """
        Instead of returning (tileOrigin, tileDestination),
        return the entire path (a list of Tiles).
        """
        score, best_path = minimax_pruning(
            board, 
            depth=self.depth, 
            is_player1_turn=self.is_player1(), 
            heuristic=self.get_heuristic(), 
            use_eval_func_1=self.uses_eval_func_1(),
            maximizing=True
        )
        
        # Log debugging information
        print(f"AI Move Generation:")
        print(f"Player: {self.get_name()}")
        print(f"Score: {score}")
        #print path
        if best_path:
            print(f"Best Path: {best_path.__str__()}")
        else:
            print("No valid path found.")
        print(f"Path Length: {len(best_path)}")
        if best_path:
            print("Path Details:")
            for tile in best_path:
                print(f" - Tile: {tile}, Is Empty: {tile.is_empty()}")

        return best_path

    def get_heuristic(self):
        return self.heuristic
    
    def get_eval_func(self) -> int:
        return self.eval_func
    
    def uses_eval_func_1(self) -> bool:
        return self.eval_func == 1

class Player_Person(Player):
    def __init__(self, name: str) -> None:
        super().__init__(name)

    def get_move(self, board: Board) -> tuple[Tile, Tile]:
        # Select piece from all it pieces available
        tile_origin: Tile = ask_person_for_piece(board, "1" in self.get_name())

        # Select the destination tile for the selected piece
        tile_destination: Tile = ask_person_for_tile_destination(board, tile_origin)

        return (tile_origin, tile_destination)
