package guessnumber;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * All console input for the game goes through this class.
 *
 * <p>It keeps the validation logic in one place so the main program stays
 * readable, and it never crashes on bad input: non-numeric text, empty
 * lines and out-of-range numbers are all handled.</p>
 */
public class InputHandler {

    private final Scanner scanner;

    public InputHandler() {
        this.scanner = new Scanner(System.in);
    }

    /**
     * Reads a whole number from the console and validates it against a range.
     * The player is asked again until a valid value is entered.
     *
     * @param prompt message shown before reading
     * @param min    smallest accepted value (inclusive)
     * @param max    largest accepted value (inclusive)
     * @return a validated number inside {@code min..max}
     * @throws GameExitException if the input stream ends (Ctrl+Z / EOF)
     */
    public int readIntInRange(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String line = readLine();

            int value;
            try {
                value = Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                System.out.println("  Invalid input. Please enter a whole number.");
                continue;
            }

            if (value < min || value > max) {
                System.out.println("  Out of range. Enter a number between " + min + " and " + max + ".");
                continue;
            }

            return value;
        }
    }

    /**
     * Asks a yes/no question and keeps asking until the answer is clear.
     *
     * @param prompt question text, for example {@code "Play again? (y/n): "}
     * @return {@code true} for yes, {@code false} for no
     * @throws GameExitException if the input stream ends
     */
    public boolean readYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String answer = readLine().trim().toLowerCase();

            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            System.out.println("  Please answer with 'y' or 'n'.");
        }
    }

    /**
     * Reads one line of text and stops the game cleanly when the input ends.
     */
    private String readLine() {
        try {
            return scanner.nextLine();
        } catch (NoSuchElementException | IllegalStateException e) {
            throw new GameExitException();
        }
    }

    /** Releases the underlying input stream. */
    public void close() {
        scanner.close();
    }

    /**
     * Signals that the player closed the input stream, so the program can
     * shut down gracefully instead of printing a stack trace.
     */
    public static class GameExitException extends RuntimeException {

        public GameExitException() {
            super("Input stream closed");
        }
    }
}
