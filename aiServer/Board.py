from Tile import Tile
from Piece import Piece

class Board():
    def __init__(self) -> None:
        # Create the tiles, an arrange them in a list of lists
        self.board_row_tiles: list[list[Tile]] = self.generate_board_rows()

        # Arrange the tiles in a single list
        self.board_tiles: list[Tile] = self.rows_to_board()

        # Generate the pieces for both players
        self.pieces: list[Piece] = self.generate_pieces()

        # Generate then links between all tiles
        self.add_neighbouring_tiles()

        # Place the pieces of both users in the board
        self.place_pieces_in_board()

        # Calculate scores for every tiles later used in evaluation function
        self.calculate_tiles_scores()
    
    """Creates and returns a lists of Tiles that represent each row in the board"""
    def generate_board_rows(self) -> list[list[Tile]]:
        TILES_PER_ROW: list[int] = [1, 2, 3, 4, 13, 12, 11, 10, 9, 10, 11, 12, 13, 4, 3, 2, 1]
        board_rows: list[list[Tile]] = []
        for i in range(len(TILES_PER_ROW)):
            board_rows.append([Tile() for _ in range(TILES_PER_ROW[i])])
        return board_rows
    
    """Receives the list of rows with Tiles and outputs a list of Tiles"""
    def rows_to_board(self) -> list[Tile]:
        board: list[Tile] = []
        for row in self.board_row_tiles:
            board.extend(row)
        return board

    """Creates 10 pieces for the player1 and another 10 pieces for the player2.
    Returns a single list with all 20 pieces"""
    def generate_pieces(self) -> list[Piece]:
        return [Piece(Piece.PLAYER1_COLOR) for _ in range(10)] + [Piece(Piece.PLAYER2_COLOR) for _ in range(10)]
    
    """Adds all the neighbours for each tile"""
    def add_neighbouring_tiles(self) -> None:
        # Add edges within the row
        for row in self.board_row_tiles:
            for i in range(0, len(row)-1):
                row[i].add_neighbour("R", row[i+1])
            for i in range(1, len(row)):
                row[i].add_neighbour("L", row[i-1])

        # Add some diagonal edges (1/4)
        for row_index in [0, 1, 2, 8, 9, 10, 11]:
            for tile_index in range(len(self.board_row_tiles[row_index])):
                self.board_row_tiles[row_index    ][tile_index    ].add_neighbour("DL", self.board_row_tiles[row_index+1][tile_index])
                self.board_row_tiles[row_index    ][tile_index    ].add_neighbour("DR", self.board_row_tiles[row_index+1][tile_index+1])
                self.board_row_tiles[row_index + 1][tile_index    ].add_neighbour("UR", self.board_row_tiles[row_index][tile_index])
                self.board_row_tiles[row_index + 1][tile_index + 1].add_neighbour("UL", self.board_row_tiles[row_index][tile_index])

        # Add more diagonal edges (2/4)
        for row_index in [5, 6, 7, 8, 14, 15, 16]:
            for tile_index in range(len(self.board_row_tiles[row_index])):
                self.board_row_tiles[row_index    ][tile_index    ].add_neighbour("UL", self.board_row_tiles[row_index - 1][tile_index])
                self.board_row_tiles[row_index    ][tile_index    ].add_neighbour("UR", self.board_row_tiles[row_index - 1][tile_index+1])
                self.board_row_tiles[row_index - 1][tile_index    ].add_neighbour("DR", self.board_row_tiles[row_index    ][tile_index])
                self.board_row_tiles[row_index - 1][tile_index + 1].add_neighbour("DL", self.board_row_tiles[row_index    ][tile_index])

        # Add more diagonal edges (3/4)
        for tile_index in range(len(self.board_row_tiles[3])):
            self.board_row_tiles[3][tile_index    ].add_neighbour("DL", self.board_row_tiles[4][tile_index + 4])
            self.board_row_tiles[3][tile_index    ].add_neighbour("DR", self.board_row_tiles[4][tile_index + 5])
            self.board_row_tiles[4][tile_index + 4].add_neighbour("UR", self.board_row_tiles[3][tile_index])
            self.board_row_tiles[4][tile_index + 5].add_neighbour("UL", self.board_row_tiles[3][tile_index])

        # Add last diagonal edges (4/4)
        for tile_index in range(len(self.board_row_tiles[13])):
            self.board_row_tiles[13][tile_index    ].add_neighbour("UL", self.board_row_tiles[12][tile_index + 4])
            self.board_row_tiles[13][tile_index    ].add_neighbour("UR", self.board_row_tiles[12][tile_index + 5])
            self.board_row_tiles[12][tile_index + 4].add_neighbour("DR", self.board_row_tiles[13][tile_index])
            self.board_row_tiles[12][tile_index + 5].add_neighbour("DL", self.board_row_tiles[13][tile_index])
    
    """Places the 20 pieces where they should be at the start of the game"""
    def place_pieces_in_board(self) -> None:
        i = 0
        for piece in self.get_player1_pieces():
            self.board_tiles[i].set_piece(piece)
            i += 1
        
        for piece in self.get_player2_pieces():
            self.board_tiles[-i].set_piece(piece)
            i -= 1

    """Returns all the tiles that contain pieces from the player1"""
    def get_player1_tiles(self):
        return filter(lambda t: not t.is_empty()  and  t.get_piece().is_player1_piece(), self.board_tiles)

    """Returns all the tiles that contain pieces from the player2"""
    def get_player2_tiles(self):
        return filter(lambda t: not t.is_empty()  and  t.get_piece().is_player2_piece(), self.board_tiles)
        
    """Returns a filter that iterates through all Pieces of the player1"""
    def get_player1_pieces(self):
        return filter(lambda p: p.is_player1_piece(), self.pieces)

    """Returns a filter that iterates through all Pieces of the player2"""
    def get_player2_pieces(self):
        return filter(lambda p: p.is_player2_piece(), self.pieces)

    """Check if triangle destination for player1 is filled with player1 Tiles"""
    def has_player1_reached_destination(self) -> bool:
        return all(not tile.is_empty() for tile in self.get_bottom_triangle_tiles())  and  any(tile.get_piece().is_player1_piece() for tile in self.get_bottom_triangle_tiles())

    """Check if triangle destination for player2 is filled with player2 Tiles"""
    def has_player2_reached_destination(self) -> bool:
        return all(not tile.is_empty() for tile in self.get_top_triangle_tiles())  and  any(tile.get_piece().is_player2_piece() for tile in self.get_top_triangle_tiles())

    """Check if player1 can move"""
    def can_player1_move(self) -> bool:
        return any(filter(lambda t: any(self.get_all_valid_moves(t)), self.get_player1_tiles()))

    """Check if player2 can move"""
    def can_player2_move(self) -> bool:
        return any(filter(lambda t: any(self.get_all_valid_moves(t)), self.get_player2_tiles()))

    """Check if the player1 has won"""
    def has_player1_won(self) -> bool:
        # Has won if has reached destination 
        return self.has_player1_reached_destination()

    """Check if the player2 has won"""
    def has_player2_won(self) -> bool:
        # Has won if has reached destination
        return self.has_player2_reached_destination()

    """Return True if (at least) one of the player has reached the end of the board"""
    def has_game_ended(self) -> bool:
        return self.has_player2_won() or self.has_player1_won()

    """Return the score for the current state of the board"""
    def get_score(self, is_player1_turn: bool, use_eval_func_1: bool) -> int:
        if (is_player1_turn  and  self.has_player1_won())  or  (not is_player1_turn  and  self.has_player2_won()):
            return 1_000_000
        if (is_player1_turn  and  self.has_player2_won())  or  (not is_player1_turn  and  self.has_player1_won()):
            return -1_000_000
         
        if use_eval_func_1:
            # Evaluation function 1
            score_player_1 = sum(t.get_score1() for t in self.get_player1_tiles()) * (1 if is_player1_turn else -1)
            score_player_2 = sum(t.get_score1() for t in self.get_player2_tiles()) * (-1 if is_player1_turn else 1)
            return score_player_1 + score_player_2
        else:
            # Evaluation function 2
            score_player_1 = sum(t.get_score2() for t in self.get_player1_tiles()) * (1 if is_player1_turn else -1)
            score_player_2 = sum(t.get_score2() for t in self.get_player2_tiles()) * (-1 if is_player1_turn else 1)
            return score_player_1 + score_player_2
    
    """Generator that outputs all the tiles where you can move to"""
    def get_all_possible_tiles_to_move(self, tile: Tile, only_jumps = False, already_jumped_from = None, already_returned = None):
        # This only happens on the first call
        if already_jumped_from is None:
            already_jumped_from = set()
            already_returned = set()
        
        # We only process the tile if we still haven't jumped from this tile
        if tile not in already_jumped_from:
            # Add the starting tile 
            already_jumped_from.add(tile)

            for (neighbour_direction, neighbour_tile) in tile.get_neighbours().items():
                if neighbour_tile.is_empty():
                    # A neighbouring tile is empty, we can move to that it directly but we cannot jump it
                    if not only_jumps:
                        # Only return the result if it is the first move (if it is a recursive call only_jumps is set to True)
                        yield neighbour_tile
                else:
                    # Neighbour is not empty, maybe we can jump
                    neighbours_neighbour: Tile = neighbour_tile.get_neighbours().get(neighbour_direction, None)
                    if neighbours_neighbour is not None:
                        # The neighbour exists
                        if neighbours_neighbour.is_empty():
                            # It exists and it is empty, we can jump to it
                            if neighbours_neighbour not in already_returned:
                                yield neighbours_neighbour
                                already_returned.add(neighbours_neighbour)
                            for move in self.get_all_possible_tiles_to_move(neighbours_neighbour, True, already_jumped_from, already_returned):
                                # Maybe we can keep on jumping
                                yield move
                                already_returned.add(move)
    
    """Return the tiles that are part of the triangle in the top"""
    def get_top_triangle_tiles(self):
        return self.board_tiles[:10]

    """Return the tiles that are part of the triangle in the bottom"""
    def get_bottom_triangle_tiles(self):
        return self.board_tiles[-10:]
    
    """Generates all the valid moves from the piece in the argument"""
    def get_all_valid_moves(self, tile_origin: Tile):
        for move in self.get_all_possible_tiles_to_move(tile_origin):
            # Moves that are not valid: move a piece that already rests in its target triangle out of that triangle
            if tile_origin.get_piece().is_player2_piece()  and  tile_origin in self.get_top_triangle_tiles()  and   move not in self.get_top_triangle_tiles():
                continue
            elif tile_origin.get_piece().is_player1_piece()  and  tile_origin in self.get_bottom_triangle_tiles()  and  move not in self.get_bottom_triangle_tiles():
                continue
            else:
                yield move
    
    """Generates all valid moves that satisfy the heuristic function"""
    def get_all_valid_logical_moves(self, tile_origin: Tile, heuristic_function):
        for tile_destination in self.get_all_valid_moves(tile_origin):
            if heuristic_function(tile_origin, tile_destination):
                yield tile_destination
    def get_all_valid_logical_paths(self, tile_origin: Tile, heuristic_function=None) -> list[list[Tile]]:
        """
        Returns every possible complete path (list of Tiles) that the piece on tile_origin
        can make in one turn, including:
        - Single-step moves: [origin, neighbor]
        - Multi-jump moves: [origin, jump1, jump2, ..., finalTile]

        If 'heuristic_function' is provided, it is called once at the end to verify
        (tile_origin, final_tile). If it returns False, that path is excluded.

        Also respects the rule that if a piece starts in its finishing triangle,
        it cannot leave that triangle in one turn.
        """
        # If there's no piece on tile_origin, no moves exist
        if tile_origin.is_empty():
            return []

        piece = tile_origin.get_piece()
        all_paths = []

        ########################################################################
        # 1) Single-step moves
        ########################################################################
        for neighbor in tile_origin.get_neighbours().values():
            if neighbor is not None and neighbor.is_empty():
                # Enforce triangle rule:
                if piece.is_player2_piece() and (tile_origin in self.get_top_triangle_tiles()) and (neighbor not in self.get_top_triangle_tiles()):
                    # Player2 piece can't leave top triangle
                    continue
                if piece.is_player1_piece() and (tile_origin in self.get_bottom_triangle_tiles()) and (neighbor not in self.get_bottom_triangle_tiles()):
                    # Player1 piece can't leave bottom triangle
                    continue

                # Enforce heuristic if given (for the final move):
                if heuristic_function is not None and not heuristic_function(tile_origin, neighbor):
                    continue

                # If it's valid, store [origin, neighbor]
                all_paths.append([tile_origin, neighbor])

        ########################################################################
        # 2) Multi-jump moves via DFS
        ########################################################################
        jump_paths = []
        visited = set()
        visited.add(tile_origin)

        def dfs_jumps(current_path: list[Tile]):
            current_tile = current_path[-1]
            found_jump = False

            for direction, occupied_tile in current_tile.get_neighbours().items():
                if occupied_tile is None or occupied_tile.is_empty():
                    continue  # Need an occupied tile to jump over

                # The landing tile in the same direction beyond that occupied tile
                landing_tile = occupied_tile.get_neighbours().get(direction)
                if landing_tile is None or (not landing_tile.is_empty()) or (landing_tile in visited):
                    continue

                # We can jump here
                found_jump = True
                visited.add(landing_tile)
                current_path.append(landing_tile)

                dfs_jumps(current_path)  # Check further jumps from there

                # Backtrack
                current_path.pop()
                visited.remove(landing_tile)

            # If no further jumps found AND the path length > 1, we have a valid jump chain
            if not found_jump and len(current_path) > 1:
                jump_paths.append(list(current_path))

        dfs_jumps([tile_origin])

        # Now filter the jump paths by triangle rules and optional heuristic
        # It is permitted to move a marble into any hole on the board including holes in triangles belonging to other players. 
        # However, once a marble has reached the opposite triangle, it may not be moved out of the triangle - only within the triangle.
        for path in jump_paths:
            final_tile = path[-1]

            # Enforce triangle rule
            if piece.is_player2_piece() and (tile_origin in self.get_top_triangle_tiles()) and (final_tile not in self.get_top_triangle_tiles()):
                continue
            if piece.is_player1_piece() and (tile_origin in self.get_bottom_triangle_tiles()) and (final_tile not in self.get_bottom_triangle_tiles()):
                continue

            # Enforce heuristic if given
            if (heuristic_function is not None) and (not heuristic_function(tile_origin, final_tile)):
                continue

            all_paths.append(path)

        return all_paths

                                    
    """Moves the piece in the arguments to the tile in the paramenters. If the movement is not possible it does nothing returns False"""
    def move_piece_to_tile(self, tile_origin: Tile, destination_tile: Tile) -> bool:
        if not destination_tile.is_empty():
            # We cannot move here, do not do anythin
            return False
        
        destination_tile.set_piece(tile_origin.get_piece())
        tile_origin.set_empty()

        return True

    """Calculates for all tiles in the board the distance from that tile to tiles in the top and bottom edges"""
    def calculate_tiles_scores(self) -> None:
        pending_of_exploring: list[Tile]

        # Evaluation function 1
        # Calculate scores for player1 player
        pending_of_exploring = [self.board_tiles[-1]]
        pending_of_exploring[0].set_score1_for_player1(16)
        while any(pending_of_exploring):
            exploring_tile, pending_of_exploring = pending_of_exploring[0], pending_of_exploring[1:]
            pending_of_exploring.extend( [tile for tile in exploring_tile.get_neighbours().values() if tile.set_score1_for_player1(exploring_tile.get_score1_for_player1() - 1)] )
        for tile in self.get_bottom_triangle_tiles():
            tile.set_score1_for_player1(5 + tile.get_score1_for_player1())
        
        # Calculate scores for player2 player
        pending_of_exploring = [self.board_tiles[0]]
        pending_of_exploring[0].set_score1_for_player2(16)
        while any(pending_of_exploring):
            exploring_tile, pending_of_exploring = pending_of_exploring[0], pending_of_exploring[1:]
            pending_of_exploring.extend( [tile for tile in exploring_tile.get_neighbours().values() if tile.set_score1_for_player2(exploring_tile.get_score1_for_player2() - 1)] )
        for tile in self.get_top_triangle_tiles():
            tile.set_score1_for_player2(5 + tile.get_score1_for_player2())

        # Evaluation function 2
        # Player 1
        for i in range(len(self.board_row_tiles)):
            row = self.board_row_tiles[i]
            for j in range(len(row)):
                if len(row) % 2 == 0:
                    row[j].set_score2_for_player1(1*10 - abs(int((len(row)-1)/2 - j)))
                else:
                    row[j].set_score2_for_player1(i*10 - abs(len(row)//2 - j))
        for tile in self.get_bottom_triangle_tiles():
            tile.set_score2_for_player1(tile.get_score2_for_player1() + 50)
        # Player2
        for i in range(len(self.board_row_tiles)):
            row = self.board_row_tiles[i]
            for j in range(len(row)):
                if len(row) % 2 == 0:
                    row[j].set_score2_for_player2((16-i)*10 - abs(int((len(row)-1)/2 - j)))
                else:
                    row[j].set_score2_for_player2((16-i)*10 - abs(len(row)//2 - j))
        for tile in self.get_top_triangle_tiles():
            tile.set_score2_for_player2(tile.get_score2_for_player2() + 50)

    """Prints the board in the command line"""
    def print_board(self, numbered_tiles=None, characters="123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ") -> None:
        print(self.to_string(numbered_tiles, characters))
        


    def to_string(self, numbered_tiles=None, characters="123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ") -> str:
        res = ""
        numbered_tiles = [] if numbered_tiles is None else list(numbered_tiles)
        
        # Calculate the lenth of the row(s) with the most tiles
        max_length: int = max( (len(row) for row in self.board_row_tiles) )

        res += "CURRENT BOARD:\n"
        for row in self.board_row_tiles:
            # Print the spaces before the row
            res += " "*(max_length-len(row))
            tile: Tile
            for tile in row:
                # Print each tile
                if tile in numbered_tiles:
                    res += f"{characters[numbered_tiles.index(tile)]} "
                else:
                    res += f"{str(tile)} "
            res += "\n"
        res += "\n"

        return res

    
    """Returns if both tiles are in the same row"""
    def get_row_index(self, tile: Tile) -> int:
        for i in range(len(self.board_row_tiles)):
            if tile in self.board_row_tiles[i]:
                return i
    
    """Returns the tile that contain the piece in the argument"""
    def get_tile(self, piece: Piece):
        return next(tile for tile in self.board_tiles if tile.get_piece() is piece)
    
        # --- In Board.py ---
    # def get_all_jump_paths(self, start_tile: Tile):
    #         """
    #         Returns a list of possible multi-jump paths for the piece in start_tile.
    #         Each path is a list of Tiles: [start_tile, ..., final_tile].
    #         If no jumps are possible, it returns an empty list.
    #         """
    #         if start_tile.is_empty():
    #             return []  # No piece here -> no paths

    #         all_paths = []

    #         def backtrack(current_path, visited):
    #             current_tile = current_path[-1]

    #             # We'll track if we found any new jump from this tile
    #             found_jump = False

    #             for direction, neighbor in current_tile.get_neighbours().items():
    #                 # Must have a piece to jump over
    #                 if not neighbor.is_empty():
    #                     # The tile after neighbor in the same direction
    #                     jump_tile = neighbor.get_neighbours().get(direction, None)
    #                     if jump_tile and jump_tile.is_empty() and jump_tile not in visited:
    #                         # Valid jump
    #                         found_jump = True
    #                         current_path.append(jump_tile)
    #                         visited.add(jump_tile)

    #                         backtrack(current_path, visited)

    #                         # Undo
    #                         current_path.pop()
    #                         visited.remove(jump_tile)

    #             # If we never found a jump from current_tile, it means
    #             # this path can't extend further. Save the path as-is.
    #             if not found_jump:
    #                 # We want to store a copy of the current path
    #                 all_paths.append(list(current_path))

    #         # Start backtracking from the start tile
    #         visited = set([start_tile])
    #         backtrack([start_tile], visited)

    #         # Now all_paths might include 1-length “paths” if no jump is possible.
    #         # For multi-jump moves, we only want those with length > 1 to indicate
    #         # an actual jump. But if you also want to allow single-step adjacency (non-jumps)
    #         # in the same turn, handle that separately. Typically, we expect jumps to be length>=2.

    #         return all_paths
    
    def get_all_jump_paths(self, tile: Tile) -> list[list[Tile]]:
        """
        Finds all valid jump paths starting from the given tile.
        Each path is a list of tiles: [start_tile, jump1, jump2, ...]
        Returns an empty list if no jumps are possible.
        """
        # Always return a list (empty if no jumps), never None
        if tile is None or tile.is_empty():
            return []  # Return empty list instead of None
        
        all_paths = []
        visited = set([tile])
        
        def find_jumps(current_tile, current_path):
            """Recursive helper to find all jump paths"""
            # Check all directions for possible jumps
            found_any_jump = False
            
            for direction, neighbor in current_tile.get_neighbours().items():
                if neighbor is None or neighbor.is_empty():
                    continue  # No piece to jump over
                    
                # There's a piece - check if we can jump over it
                landing_tile = neighbor.get_neighbours().get(direction)
                if landing_tile is None or not landing_tile.is_empty() or landing_tile in visited:
                    continue  # Invalid landing spot
                
                # Valid jump found
                found_any_jump = True
                new_path = current_path + [landing_tile]
                visited.add(landing_tile)
                
                # Recursively find more jumps from this position
                find_jumps(landing_tile, new_path)
                
                # Backtrack
                visited.remove(landing_tile)
            
            # If no further jumps were found, and we've moved at least once,
            # this is a valid (possibly terminal) path
            if not found_any_jump and len(current_path) > 1:
                all_paths.append(current_path)
    
        # Start recursion from the initial tile
        find_jumps(tile, [tile])
        return all_paths  # This will be an empty list if no jumps found

    def apply_path(self, path: list[Tile]):
        """
        Move the piece from path[0] -> path[1] -> path[2] ...
        """
        if not path or len(path) < 2:
            return
        moving_piece = path[0].get_piece()
        for i in range(len(path) - 1):
            origin = path[i]
            destination = path[i + 1]
            destination.set_piece(moving_piece)
            origin.set_empty()

    def undo_path(self, path: list[Tile]):
        """
        Undo the path, i.e. move the piece back from the last tile to the first.
        """
        if not path or len(path) < 2:
            return
        moving_piece = path[-1].get_piece()
        for i in range(len(path) - 1, 0, -1):
            cur_tile = path[i]
            prev_tile = path[i - 1]
            prev_tile.set_piece(moving_piece)
            cur_tile.set_empty()
            
            
    def get_jump_destinations(self, tile: Tile) -> list[Tile]:
        """
        Returns a list of landing tiles reachable via a jump from the given tile.
        A jump is allowed if there is an adjacent neighbor (in any valid direction)
        that is occupied and the tile immediately beyond it (in the same direction) is empty.
        """
        destinations = []
        for direction, neighbor in tile.get_neighbours().items():
            if neighbor is not None and not neighbor.is_empty():
                landing_tile = neighbor.get_neighbours().get(direction, None)
                if landing_tile is not None and landing_tile.is_empty():
                    destinations.append(landing_tile)
        return destinations

    def is_valid_jump(self, current_tile: Tile, landing_tile: Tile) -> bool:
        """
        Validates a jump move by ensuring that the landing_tile is reachable from
        current_tile by jumping over an adjacent occupied tile.
        """
        return landing_tile in self.get_jump_destinations(current_tile)
    
    def get_all_jump_paths(self, tile: Tile) -> list[list[Tile]]:
        """
        Finds all valid jump paths starting from the given tile.
        Each path is a list of tiles: [start_tile, jump1, jump2, ...]
        Returns multiple paths if there are branching possibilities.
        """
        if tile.is_empty():
            return []  # No piece here, no paths possible
        
        all_paths = []
        visited = set([tile])
    
    def find_jumps(current_tile, current_path):
        """Recursive helper to find all jump paths"""
        # Check all directions for possible jumps
        found_any_jump = False
        
        for direction, neighbor in current_tile.get_neighbours().items():
            if neighbor is None or neighbor.is_empty():
                continue  # No piece to jump over
                
            # There's a piece - check if we can jump over it
            landing_tile = neighbor.get_neighbours().get(direction)
            if landing_tile is None or not landing_tile.is_empty() or landing_tile in visited:
                continue  # Invalid landing spot
            
            # Valid jump found
            found_any_jump = True
            new_path = current_path + [landing_tile]
            visited.add(landing_tile)
            
            # Recursively find more jumps from this position
            find_jumps(landing_tile, new_path)
            
            # Backtrack
            visited.remove(landing_tile)
        
        # If no further jumps were found, and we've moved at least once,
        # this is a valid (possibly terminal) path
        if not found_any_jump and len(current_path) > 1:
            all_paths.append(current_path)
    
        # Start recursion from the initial tile
        find_jumps(tile, [tile])
        return all_paths

    def apply_path(self, path: list[Tile]):
        """
        Apply a complete path by moving the piece from the first tile to the last,
        through all intermediate tiles.
        """
        if not path or len(path) < 2:
            return False  # Invalid path
        
        # Get the piece from the first tile
        piece = path[0].get_piece()
        if piece is None:
            return False  # No piece to move
        
        # Move the piece through the path
        for i in range(len(path) - 1):
            current_tile = path[i]
            next_tile = path[i + 1]
            
            # Verify next tile is empty
            if not next_tile.is_empty():
                return False  # Can't move to a non-empty tile
            
            # Move the piece one step
            next_tile.set_piece(piece)
            current_tile.set_empty()
        
        return True

    def undo_path(self, path: list[Tile]):
        """
        Undo a path by moving the piece from the last tile back to the first.
        Used for minimax search to restore board state after evaluation.
        """
        if not path or len(path) < 2:
            return False
        
        # Get the piece from the last tile
        piece = path[-1].get_piece()
        if piece is None:
            return False  # Something went wrong, no piece at the end
        
        # Move the piece back through the path in reverse
        for i in range(len(path) - 1, 0, -1):
            current_tile = path[i]
            prev_tile = path[i - 1]
            
            # Move the piece one step back
            prev_tile.set_piece(piece)
            current_tile.set_empty()
        
        return True

    def validate_jump(self, from_tile: Tile, to_tile: Tile) -> bool:
        """
        Validates if a jump from from_tile to to_tile is legal.
        In Chinese Checkers, a jump requires a piece in between.
        """
        # Direct neighbors can just move (no jump needed)
        if to_tile in from_tile.get_neighbours().values():
            return True
            
        # For a jump, we need to find a piece in between
        for direction, neighbor in from_tile.get_neighbours().items():
            if neighbor is not None and not neighbor.is_empty():
                # There's a piece we might jump over
                jump_landing = neighbor.get_neighbours().get(direction)
                if jump_landing == to_tile:
                    return True  # Valid jump
        
        return False  # No valid jump path found


