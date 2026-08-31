package org.example.constant;

import java.util.Arrays;
import java.util.List;

/**
 * Contain Application constant
 */
public class AppConstant {

    public static class ConsoleApplication {

        public static final String WELCOME_HEADER = "=================== Welcome to Database Management System ===================";
        public static final String AUTH_PROMPT = "Please authenticate to continue.";
        public static final String EXIT_MESSAGE = "Thank you for using the Database Management System. Goodbye!";

        public static final String MENU_HEADER = "------------------- Authentication Required ------------------";
        public static final String ADMIN_PANEL = "------------------- Admin Panel ------------------";
        public static final String MENU_OPTION_LOGIN = "1. Login";
        public static final String MENU_OPTION_SIGNUP = "2. Signup";
        public static final String MENU_OPTION_RECOVER = "3. Recover Password";
        public static final String MENU_OPTION_EXIT = "0. Exit";
        public static final String MENU_PROMPT = "Please select an option (0-3):";
        public static final String SECURITY_QUESTION_SELECTION_PROMPT = "Please select a security question (0-5):";
        public static final String EXIT_PROMPT = "Type 'Exit' to close the software";

        public static class AuthConstants {
            public static final String USERNAME_PROMPT = "Enter your user id: ";
            public static final String PASSWORD_PROMPT = "Enter your password: ";
            public static final String CONFIRM_PASSWORD_PROMPT = "Confirm your password: ";
            public static final String SECURITY_QUESTION_PROMPT = "Select a security question: ";
            public static final String SECURITY_ANSWER_PROMPT = "Enter your answer: ";
            public static final String NEW_PASSWORD_PROMPT = "Enter your new password: ";
            public static final String SQL_QUERY_PROMPT = "SQL: ";
            public static final String CAPTCHA_PROMPT = "Solve the following captcha to proceed: ";

        }

        public static List<String> getMenuLines() {
            return Arrays.asList(
                    MENU_HEADER,
                    MENU_OPTION_LOGIN,
                    MENU_OPTION_SIGNUP,
                    MENU_OPTION_RECOVER,
                    MENU_OPTION_EXIT
            );
        }
        static

        public enum SecurityQuestion {
            PET_NAME("1. What is your pet's name?"),
            MOTHER_MAIDEN_NAME("2. What is your mother's maiden name?"),
            FAVORITE_TEACHER("3. Who was your favorite teacher?"),
            BIRTH_CITY("4. In which city were you born?"),
            FAVORITE_FOOD("5. What is your favorite food?"),
            SELECTION_CANCEL_OPTION("0. Exit");
            private final String text;

            SecurityQuestion(String text) {
                this.text = text;
            }

            public static SecurityQuestion fromOption(int option) {
                for (SecurityQuestion question : SecurityQuestion.values()) {
                    if (question.ordinal() + 1 == option) {
                        return question;
                    }
                }
                throw new IllegalArgumentException("Invalid security question option: " + option);
            }

            public String getText() {
                return text;
            }

            @Override
            public String toString() {
                return text;
            }
        }
    }

    public enum AUDIT_LOG_TYPE {
        FAILED_LOGIN,
        SUCCESS_LOGIN,
        FAILED_SIGNUP,
        SUCCESS_SIGNUP,
        FAILED_RECOVER_PASSWORD,
        SUCCESS_RECOVER_PASSWORD
    }

    public static class AuditLogRows{
        public static final String TYPE = "type";
        public static final String USER_ID = "user_id";
        public static final String IP_ADDRESS = "ip_address";
        public static final String MESSAGE = "message";
        public static final String TIMESTAMP = "timestamp";
    }

    public static class FilePaths {
        public static final String USERS_FILE_PATH = "data/users/users.txt";
        public static final String AUDIT_LOG_FILE_PATH = "data/audit/audit_log.csv";
        public static final String META_DATA_FILE_PATH = "data/db/metadata.json";
        public static final String DATA_FILE_PATH = "data/db/";
    }

    public static class FileType{
        public static final String TXT = ".txt";

    }

    public static class Delimiters {
        public static final String USER_DELIMITER = "|";
        public static final String AUDIT_LOG_DELIMITER = ",";
        public static final String ROW_DELIMITER = "@@@@";
        public static final String COLUMN_DELIMITER = "####";

    }
}
