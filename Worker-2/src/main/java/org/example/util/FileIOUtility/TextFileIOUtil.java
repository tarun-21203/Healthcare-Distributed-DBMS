package org.example.util.FileIOUtility;

import org.example.exception.FileIOException;

import java.io.*;
import java.util.List;

public class TextFileIOUtil implements IFileIOUtil {

    @Override
    public boolean checkFileExists(String filepath){
        File file = new File(filepath);
        return file.exists();
    }

    @Override
    public void createFile(String filPath) throws FileIOException {
        try {
            File file = new File(filPath);

            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new FileIOException(e.getMessage());
        }
    }

    @Override
    public void writeToFile(String filPath, List<String> data) throws FileIOException {
        File file = new File(filPath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            for (String line : data) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            throw new FileIOException(e.getMessage());
        }
    }

    @Override
    public void appendToFile(String filPath, String data) throws FileIOException {
        File file = new File(filPath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(data);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new FileIOException(e.getMessage());
        }
    }

    @Override
    public List<String> readFromFile(String filPath) throws FileIOException {
        File file = new File(filPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<String> lines = reader.lines().toList();
            return lines;
        } catch (IOException e) {
            throw new FileIOException(e.getMessage());
        }
    }

    //    @Override
    public String readFileAsString(String filPath) throws FileIOException {
        File file = new File(filPath);
        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line);
            }
            return fileContent.toString();
        } catch (IOException e) {
            throw new FileIOException(e.getMessage());
        }
    }

    public void writeFileAsString(String filPath, String data) throws FileIOException {
        File file = new File(filPath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(data);
            writer.flush();
        } catch (IOException e) {
            throw new FileIOException(e.getMessage());
        }
    }
}
