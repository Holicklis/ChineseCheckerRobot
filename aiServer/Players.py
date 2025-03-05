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
    """
    Returns (score, path) where path is a list of Tiles representing the move sequence 
    (e.g. [origin, midJump1, midJump2, finalDest]).
    If no moves exist, returns (score, []) or similar.
    """
    # Base case
    if depth == 0 or board.has_game_ended():
        # We add `depth` as a small factor so the AI might prefer faster wins
        return board.get_score(is_player1_turn, use_eval_func_1) + depth, []

    if maximizing:
        max_points = float('-inf')
        best_path = []
        # Get the tiles for the current player
        tiles_for_player = board.get_player1_tiles() if is_player1_turn else board.get_player2_tiles()
        found_any_move = False
        
        for tile_origin in tiles_for_player:
            possible_paths = []
            # Include single-step moves:
            for dest in board.get_all_valid_moves(tile_origin):
                if heuristic(tile_origin, dest):
                    # Create a move path with just origin and destination
                    possible_paths.append([tile_origin, dest])
            # Include jump moves:
            jump_paths = board.get_all_jump_paths(tile_origin)
            for path in jump_paths:
                if len(path) > 1 and all(heuristic(path[i], path[i+1]) for i in range(len(path)-1)):
                    possible_paths.append(path)
            
            for path in possible_paths:
                board.apply_path(path)
                points, _ = minimax_pruning(board, depth - 1, is_player1_turn, heuristic, use_eval_func_1,
                                            maximizing=False, alpha=alpha, beta=beta)
                board.undo_path(path)
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
        # Minimizing branch: similar changes for opponent moves
        min_points = float('inf')
        best_path = []
        tiles_for_opponent = board.get_player2_tiles() if is_player1_turn else board.get_player1_tiles()
        found_any_move = False
        
        for tile_origin in tiles_for_opponent:
            possible_paths = []
            for dest in board.get_all_valid_moves(tile_origin):
                if heuristic(tile_origin, dest):
                    possible_paths.append([tile_origin, dest])
            jump_paths = board.get_all_jump_paths(tile_origin)
            for path in jump_paths:
                if len(path) > 1 and all(heuristic(path[i], path[i+1]) for i in range(len(path)-1)):
                    possible_paths.append(path)
            
            for path in possible_paths:
                board.apply_path(path)
                points, _ = minimax_pruning(board, depth - 1, is_player1_turn, heuristic, use_eval_func_1,
                                            maximizing=True, alpha=alpha, beta=beta)
                board.undo_path(path)
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
