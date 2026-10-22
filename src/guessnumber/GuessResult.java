package guessnumber;

/**
 * Result of comparing the player's guess with the secret number.
 * Each constant carries the hint message that is shown to the player.
 */
public enum GuessResult {

    TOO_LOW("Too small!  Try a bigger number."),
    TOO_HIGH("Too large!  Try a smaller number."),
    CORRECT("Correct!  You guessed it.");

    private final String hint;

    GuessResult(String hint) {
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }
}
