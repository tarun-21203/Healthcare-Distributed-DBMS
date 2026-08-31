package org.example.constant;
/**
 * Contain SQL constant like sql command and keywords
 */
public class SQLCommandConstant {

    public enum SQLCommand {
        USE("use"),
        DESCRIBE("describe"),
        SHOW("show"),
        CREATE("create"),
        DROP("drop"),
        INSERT("insert"),
        SELECT("select"),
        UPDATE("update"),
        DELETE("delete"),
        ALTER("alter"),
        BEGIN("begin"),
        COMMIT("commit"),
        ROLLBACK("rollback");

        private final String command;

        SQLCommand(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }
    }

    public enum SQLKeyword {
        DATABASE("database"),
        TABLE("table"),
        TABLES("tables"),
        VALUES("values"),
        SET("set"),
        WHERE("where"),
        FROM("from"),
        INTO("into"),
        AND("and"),
        OR("or"),
        LIKE("like"),
        DATABASES("databases");
        private final String keyword;

        SQLKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getKeyword() {
            return keyword;
        }
    }
}
