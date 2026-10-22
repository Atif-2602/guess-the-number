package guessnumber;

import java.util.Random;

/**
 * Holds the state of a single round of "Guess the Number".
 *
 * <p>The computer picks one secret number between {@link #MIN_NUMBER} and
 * {@link #MAX_NUMBER}. The player keeps guessing and the class returns a
 * {@link GuessResult} for every valid attempt.</p>
 */
public class Game {

    /** Smallest number the computer can pick. */
    public static final int MIN_NUMBER = 1;

    /** Largest number the computer can pick. */
    public static final int MAX_NUMBER = 100;

    private final Random random;
    private final int secretNumber;
    private int attempts;

    /**
     * Starts a new round with a randomly chosen secret number.
     */
    public Game() {
        this(new Random());
    }

    /**
     * Starts a new round using the supplied {@link Random} source.
     * Useful for testing because the sequence of numbers can be fixed.
     *
     * @param random source of randomness (must not be {@code null})
     */
    public Game(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.random = random;
        // nextInt(n) returns 0..n-1, so shift it into MIN_NUMBER..MAX_NUMBER.
        int span = MAX_NUMBER - MIN_NUMBER + 1;
        this.secretNumber = random.nextInt(span) + MIN_NUMBER;
        this.attempts = 0;
    }

    /**
     * Compares a guess with the secret number and records one attempt.
     *
     * <p>This method must only be called with an already validated guess, so
     * the attempt counter always reflects valid attempts, as required by the
     * project specification.</p>
     *
     * @param guess a number inside the valid range
     * @return {@link GuessResult#TOO_LOW}, {@link GuessResult#TOO_HIGH} or
     *         {@link GuessResult#CORRECT}
     * @throws IllegalArgumentException if the guess is outside the valid range
     */
    public GuessResult checkGuess(int guess) {
        if (!isInRange(guess)) {
            throw new IllegalArgumentException(
                    "Guess " + guess + " is outside " + MIN_NUMBER + "-" + MAX_NUMBER);
        }

        attempts++;

        if (guess < secretNumber) {
            return GuessResult.TOO_LOW;
        }
        if (guess > secretNumber) {
            return GuessResult.TOO_HIGH;
        }
        return GuessResult.CORRECT;
    }

    /** @return number of valid attempts made so far in this round */
    public int getAttempts() {
        return attempts;
    }

    /** @return the secret number (only useful once the round is over) */
    public int getSecretNumber() {
        return secretNumber;
    }

    /**
     * Checks whether a number lies inside the playable range.
     *
     * @param number value to test
     * @return {@code true} if the number can be guessed
     */
    public static boolean isInRange(int number) {
        return number >= MIN_NUMBER && number <= MAX_NUMBER;
    }

    /** @return the randomness source used by this round */
    public Random getRandom() {
        return random;
    }
}
