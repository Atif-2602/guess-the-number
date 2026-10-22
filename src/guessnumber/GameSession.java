package guessnumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One playable round of the game, identified by a unique id.
 *
 * <p>The web server creates a session for every browser that starts a game,
 * so two players never share a secret number. The secret number stays on the
 * server; the browser only ever receives hints.</p>
 */
public class GameSession {

    private final String id;
    private final Game game;
    private final List<Integer> guesses;
    private boolean finished;

    public GameSession() {
        this.id = UUID.randomUUID().toString();
        this.game = new Game();
        this.guesses = new ArrayList<>();
        this.finished = false;
    }

    /**
     * Records a guess for this session.
     *
     * @param guess value already validated against the playable range
     * @return the hint for the player
     * @throws IllegalStateException if the round is already finished
     */
    public GuessResult submitGuess(int guess) {
        if (finished) {
            throw new IllegalStateException("This round is already finished");
        }

        GuessResult result = game.checkGuess(guess);
        guesses.add(guess);

        if (result == GuessResult.CORRECT) {
            finished = true;
        }
        return result;
    }

    public String getId() {
        return id;
    }

    public int getAttempts() {
        return game.getAttempts();
    }

    public boolean isFinished() {
        return finished;
    }

    public int getSecretNumber() {
        return game.getSecretNumber();
    }

    /** @return an unmodifiable view of the guesses made so far */
    public List<Integer> getGuesses() {
        return Collections.unmodifiableList(guesses);
    }
}
