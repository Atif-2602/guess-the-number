package guessnumber;

/**
 * Entry point of the "Guess the Number" mini project.
 *
 * <p>The computer thinks of a number between 1 and 100 and the player tries to
 * find it. After every valid guess the program says whether the guess was too
 * small or too large. When the number is found the program shows how many
 * attempts were needed and offers another round.</p>
 *
 * <p>Run with:
 * <pre>java -cp out guessnumber.Main</pre></p>
 */
public class Main {

    private static final String LINE = "================================================================";
    private static final String THIN = "----------------------------------------------------------------";

    public static void main(String[] args) {
        InputHandler input = new InputHandler();

        try {
            printWelcome();

            int gamesPlayed = 0;
            int bestScore = Integer.MAX_VALUE;

            boolean playAgain = true;
            while (playAgain) {
                Game game = new Game();
                gamesPlayed++;

                int attempts = playRound(game, input);

                if (attempts < bestScore) {
                    bestScore = attempts;
                    System.out.println("  * New best score for this session! *");
                }

                playAgain = input.readYesNo("Play again? (y/n): ");
                System.out.println();
            }

            printSummary(gamesPlayed, bestScore);
        } catch (InputHandler.GameExitException e) {
            // Ctrl+Z (Windows) or Ctrl+D ends the input stream, so leave quietly.
            System.out.println();
            System.out.println("Input closed. Goodbye!");
        } finally {
            input.close();
        }
    }

    /**
     * Plays one complete round and returns the number of valid attempts.
     */
    private static int playRound(Game game, InputHandler input) {
        System.out.println(LINE);
        System.out.println("NEW ROUND");
        System.out.println("I have picked a secret number between "
                + Game.MIN_NUMBER + " and " + Game.MAX_NUMBER + ".");
        System.out.println("Type your guesses and I will tell you if they are too small or too large.");
        System.out.println(THIN);

        while (true) {
            int guess = input.readIntInRange(
                    "Your guess: ", Game.MIN_NUMBER, Game.MAX_NUMBER);

            GuessResult result = game.checkGuess(guess);

            if (result == GuessResult.CORRECT) {
                int attempts = game.getAttempts();
                System.out.println();
                System.out.println(result.getHint());
                System.out.println("You found the number " + game.getSecretNumber()
                        + " in " + attempts + " attempt" + (attempts == 1 ? "" : "s") + ".");
                System.out.println("Rating: " + rate(attempts));
                System.out.println();
                return attempts;
            }

            System.out.println("  " + result.getHint()
                    + "  (attempt " + game.getAttempts() + ")");
        }
    }

    /**
     * Gives friendly feedback based on how quickly the player solved the round.
     */
    private static String rate(int attempts) {
        if (attempts <= 5) {
            return "Outstanding - you read my mind!";
        }
        if (attempts <= 8) {
            return "Excellent - very sharp guessing.";
        }
        if (attempts <= 12) {
            return "Good - solid use of the hints.";
        }
        return "Keep practising - you will get faster.";
    }

    private static void printWelcome() {
        System.out.println();
        System.out.println(LINE);
        System.out.println("          GUESS THE NUMBER - Java Mini Project");
        System.out.println(LINE);
        System.out.println("How to play:");
        System.out.println("  1. I pick a secret number between "
                + Game.MIN_NUMBER + " and " + Game.MAX_NUMBER + ".");
        System.out.println("  2. You type a guess.");
        System.out.println("  3. I tell you if it is too small or too large.");
        System.out.println("  4. Guess the number in as few attempts as possible.");
        System.out.println();
        System.out.println("Only valid guesses are counted as attempts.");
        System.out.println("Enter 'n' when asked, to quit.");
        System.out.println(LINE);
        System.out.println();
    }

    private static void printSummary(int gamesPlayed, int bestScore) {
        System.out.println(LINE);
        System.out.println("SESSION SUMMARY");
        System.out.println("  Rounds played : " + gamesPlayed);
        if (gamesPlayed > 0 && bestScore != Integer.MAX_VALUE) {
            System.out.println("  Best score    : " + bestScore
                    + " attempt" + (bestScore == 1 ? "" : "s"));
        }
        System.out.println("Thanks for playing!");
        System.out.println(LINE);
    }
}
