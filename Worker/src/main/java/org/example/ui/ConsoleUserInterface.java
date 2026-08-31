package org.example.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.example.POJOs.AuthResponse;
import org.example.exception.FileIOException;
import org.example.exception.InputReaderException;
import org.example.exception.TokenException;
import org.example.interfaces.IQueryManager;
import org.example.interfaces.IUserAuthentication;
import org.example.model.User;
import org.example.service.AuditLoggerService;
import org.example.service.PostgresqlQueryService;
import org.example.service.UserAuthService;
import org.example.util.InputReaderUtility.ConsoleInputReaderUtil;
import org.example.util.InputReaderUtility.IInputReaderUtil;

import static org.example.constant.AppConstant.ConsoleApplication;
import static org.example.constant.AppConstant.ConsoleApplication.ADMIN_PANEL;
import static org.example.constant.AppConstant.ConsoleApplication.AuthConstants;

public class ConsoleUserInterface {

    private final IInputReaderUtil inputReader;
    private final IUserAuthentication authService;
    private boolean running;
    private final IQueryManager postgresqlQueryService;
    private final AuditLoggerService auditLoggerService;
    private User currentUser;

    public ConsoleUserInterface() throws FileIOException, JsonProcessingException {
        this.inputReader = new ConsoleInputReaderUtil();
        this.authService = new UserAuthService();
        this.running = false;
        this.postgresqlQueryService = new PostgresqlQueryService();
        this.auditLoggerService = new AuditLoggerService();
        this.currentUser = null;
    }

//    @Override
    public void startScreen() {
        running = true;
        displayMessage(ConsoleApplication.WELCOME_HEADER);
        handleQuerying();
//        displayMessage(ConsoleApplication.AUTH_PROMPT);
//        displayMenu();
    }

//    @Override
//    public void displayMenu() {
//        try {
//            while (running) {
//                ConsoleApplication.getMenuLines().forEach(this::displayMessage);
//                handleAuthentication(inputReader.readInt(ConsoleApplication.MENU_PROMPT));
//            }
//        } catch (Exception e) {
//            displayError(e.getMessage());
//        }
//    }

//    @Override
    public void displayMessage(String message) {
        System.out.println(message);
    }

//    @Override
    public void displayError(String errorMessage) {
        System.out.println("[ERROR]: " + errorMessage);
    }

//    @Override
    public void exitScreen() {
        displayMessage(ConsoleApplication.EXIT_MESSAGE);
    }

//    private void handleAuthentication(int selectedOption) {
//        try {
//            switch (selectedOption) {
//                case 1:
//                    handleLogin();
//                    break;
//                case 2:
//                    handleSignup();
//                    break;
//                case 3:
//                    handlePasswordRecovery();
//                    break;
//                case 0:
//                    exitScreen();
//                    running = false;
//                    break;
//                default:
//                    displayError("Invalid choice. Please try again.");
//            }
//        } catch (Exception e) {
//            displayError(e.getMessage());
//        }
//    }
//
//    private void handleLogin() {
//        try {
//            String username = inputReader.readLine(AuthConstants.USERNAME_PROMPT);
//            String password = inputReader.readLine(AuthConstants.PASSWORD_PROMPT);
//            int captchaAnswer = inputReader.readInt(String.format(AuthConstants.CAPTCHA_PROMPT + " %s ",
//                    authService.generateCaptcha()));
//            AuthResponse response = authService.authenticate(username, password, captchaAnswer);
//            currentUser = response.getUser();
//            displayMessage(response.getMessage());
//            if (currentUser.isAdmin()) {
//                displayMessage(ADMIN_PANEL);
//            }
//            displayMessage(ConsoleApplication.EXIT_PROMPT);
//            handleQuerying();
//        } catch (Exception e) {
//            displayError(e.getMessage());
//            displayMenu();
//        }
//    }
//
//    private void handleSignup() {
//        try {
//            String username = inputReader.readLine(AuthConstants.USERNAME_PROMPT);
//            String password = inputReader.readLine(AuthConstants.PASSWORD_PROMPT);
//            String confirmPassword = inputReader.readLine(AuthConstants.CONFIRM_PASSWORD_PROMPT);
//            if (!password.equals(confirmPassword)) {
//                throw new Exception("Passwords do not match");
//            }
//            ConsoleApplication.SecurityQuestion securityQuestion = getSecurityQuestion();
//            String securityAnswer = inputReader.readLine(AuthConstants.SECURITY_ANSWER_PROMPT);
//            AuthResponse response = authService.signup(username, password, securityQuestion.getText(), securityAnswer);
//            displayMessage(response.getMessage());
//        } catch (Exception e) {
//            displayError(e.getMessage());
//            displayMenu();
//        }
//    }
//
//    private void handlePasswordRecovery() {
//        try {
//            String username = inputReader.readLine(AuthConstants.USERNAME_PROMPT);
//            displayMessage(authService.getSecurityQuestion(username));
//            String securityAnswer = inputReader.readLine(AuthConstants.SECURITY_ANSWER_PROMPT);
//            String token = authService.validateSecurityAnswer(username, securityAnswer);
//            displayMessage("Token: " + token);
//            handlePasswordChange(username, token);
//        } catch (Exception e) {
//            displayError(e.getMessage());
//            displayMenu();
//        }
//    }
//
//    private void handlePasswordChange(String username, String token) {
//        try {
//            String newPassword = inputReader.readLine(AuthConstants.NEW_PASSWORD_PROMPT);
//            String confirmPassword = inputReader.readLine(AuthConstants.CONFIRM_PASSWORD_PROMPT);
//            AuthResponse response = authService.recoverPassword(username, newPassword, confirmPassword, token);
//            displayMessage(response.getMessage());
//        } catch (TokenException e) {
//            displayMessage(e.getMessage());
//            displayMenu();
//        } catch (Exception e) {
//            displayError(e.getMessage());
//            handlePasswordChange(username, token);
//        }
//    }

    private void handleQuerying() {
        try {
            while (true) {
                String sqlQuery = inputReader.readLine(AuthConstants.SQL_QUERY_PROMPT);
                if (sqlQuery.toLowerCase().equals("exit")) {
                    return;
                } else {
                    postgresqlQueryService.queryParser(sqlQuery);
//
//                    if (currentUser.isAdmin()) {
//                        auditLoggerService.getLogData(sqlQuery);
//                    } else {
//                        postgresqlQueryService.queryParser(sqlQuery);
//                    }
                }
            }
        } catch (Exception e) {
            displayError(e.getMessage());
            handleQuerying();
        }
    }

//    private ConsoleApplication.SecurityQuestion getSecurityQuestion() throws InputReaderException {
//        displayMessage(AuthConstants.SECURITY_QUESTION_PROMPT);
//        displayMessage(ConsoleApplication.SecurityQuestion.PET_NAME.getText());
//        displayMessage(ConsoleApplication.SecurityQuestion.MOTHER_MAIDEN_NAME.getText());
//        displayMessage(ConsoleApplication.SecurityQuestion.FAVORITE_TEACHER.getText());
//        displayMessage(ConsoleApplication.SecurityQuestion.BIRTH_CITY.getText());
//        displayMessage(ConsoleApplication.SecurityQuestion.FAVORITE_FOOD.getText());
//        displayMessage(ConsoleApplication.SecurityQuestion.SELECTION_CANCEL_OPTION.getText());
//        int option = inputReader.readInt(ConsoleApplication.SECURITY_QUESTION_SELECTION_PROMPT);
//        if (option == 0) {
//            throw new InputReaderException("Signup cancelled by user.");
//        }
//        return ConsoleApplication.SecurityQuestion.fromOption(option);
//    }
}
