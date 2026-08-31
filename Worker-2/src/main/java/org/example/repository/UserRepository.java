package org.example.repository;

import org.example.constant.AppConstant;
import org.example.exception.FileIOException;
import org.example.model.User;
import org.example.util.FileIOUtility.IFileIOUtil;
import org.example.util.FileIOUtility.TextFileIOUtil;
import org.example.util.HashingUtility.IHashingUtil;
import org.example.util.HashingUtility.MD5HashingUtil;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final IFileIOUtil fileIOUtil;

    public UserRepository() throws FileIOException {
        this.fileIOUtil = new TextFileIOUtil();
        if (!fileIOUtil.checkFileExists(AppConstant.FilePaths.USERS_FILE_PATH)) {
            fileIOUtil.createFile(AppConstant.FilePaths.USERS_FILE_PATH);
            saveAdminUser();
        }
    }

    /**
     * create an admin user when new file is created
     */
    private void saveAdminUser() {
        IHashingUtil hashingUtil = new MD5HashingUtil();
        User user = User.builder()
                .userId("admin")
                .hashedPassword(hashingUtil.hashText("admin"))
                .securityQuestion(null)
                .hashedSecurityAnswer(null)
                .isActive(true)
                .isAdmin(true)
                .build();
        saveUser(user);
    }

    /**
     * Save user to file
     *
     * @param user
     * @return true if saved else false
     */

    public boolean saveUser(User user) {
        String userData = user.getUserId() + AppConstant.Delimiters.USER_DELIMITER +
                user.getHashedPassword() + AppConstant.Delimiters.USER_DELIMITER +
                user.getSecurityQuestion() + AppConstant.Delimiters.USER_DELIMITER +
                user.getHashedSecurityAnswer() + AppConstant.Delimiters.USER_DELIMITER +
                user.isActive() + AppConstant.Delimiters.USER_DELIMITER +
                user.isAdmin();
        try {
            fileIOUtil.appendToFile(AppConstant.FilePaths.USERS_FILE_PATH, userData);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Get user by userId
     *
     * @param userId
     * @return User Object if found else null
     * @throws FileIOException
     */
    public User getByUserId(String userId) throws FileIOException {

        for (String line : fileIOUtil.readFromFile(AppConstant.FilePaths.USERS_FILE_PATH)) {
            String[] userData = line.split("\\" + AppConstant.Delimiters.USER_DELIMITER);
            if (userData[0].equals(userId)) {
                User user = new User();
                user.setUserId(userData[0]);
                user.setHashedPassword(userData[1]);
                user.setSecurityQuestion(userData[2]);
                user.setHashedSecurityAnswer(userData[3]);
                user.setActive(Boolean.parseBoolean(userData[4]));
                user.setAdmin(Boolean.parseBoolean(userData[5]));
                return user;
            }
        }
        return null;
    }

    /**
     * updated user password in a file
     *
     * @param userId
     * @param newHashedPassword
     * @return
     */
    public boolean updatePassword(String userId, String newHashedPassword) {
        try {
            List<String> users = fileIOUtil.readFromFile(AppConstant.FilePaths.USERS_FILE_PATH);
            List<String> updatedData = new ArrayList<>();
            for (String line : users) {
                String[] userData = line.split("\\" + AppConstant.Delimiters.USER_DELIMITER);
                if (userData[0].equals(userId)) {
                    String newUserData = userData[0] + AppConstant.Delimiters.USER_DELIMITER +
                            newHashedPassword + AppConstant.Delimiters.USER_DELIMITER +
                            userData[2] + AppConstant.Delimiters.USER_DELIMITER +
                            userData[3] + AppConstant.Delimiters.USER_DELIMITER +
                            userData[4] + AppConstant.Delimiters.USER_DELIMITER +
                            userData[5];
                    updatedData.add(newUserData);
                } else {
                    updatedData.add(line);
                }
            }
            fileIOUtil.writeToFile(AppConstant.FilePaths.USERS_FILE_PATH, updatedData);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
