package org.example.ui;

public interface IUserInterface {

    /**
     * Displays the introduction screen.
     */
    void startScreen();
    /**
     * Displays the main menu where user can choose to login, signup or recover password.
     */
    void displayMenu();
    /**
     * Display any message
     * @param message the message to display
     */
    void displayMessage(String message);
    /**
     * Displays an error message to the user
     * @param errorMessage the error message to display
     */
    void displayError(String errorMessage);
    /**
     * Displays the exit screen when user logout.
     */
    void exitScreen();
}
