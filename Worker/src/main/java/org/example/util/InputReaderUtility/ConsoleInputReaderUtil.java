package org.example.util.InputReaderUtility;

import org.example.exception.InputReaderException;

import java.util.Scanner;

public class ConsoleInputReaderUtil implements IInputReaderUtil {

    private final Scanner scanner;
    private boolean isOpen;

    public ConsoleInputReaderUtil() {
        scanner = new Scanner(System.in);
        open();
    }

    @Override
    public String readLine() throws InputReaderException {
        if (isOpen) {
            return scanner.nextLine();
        }
        throw new InputReaderException("InputReader is not opened");
    }

    @Override
    public String readLine(String message) throws InputReaderException {
        if (isOpen) {
            System.out.printf(message);
//            System.out.flush();
            return scanner.nextLine();
        }
        throw new InputReaderException("InputReader is not opened");
    }

    @Override
    public int readInt() throws InputReaderException {
        if (isOpen) {
            return Integer.parseInt(scanner.nextLine());
        }
        throw new InputReaderException("InputReader is not opened");
    }

    @Override
    public int readInt(String message) throws InputReaderException {
        if (isOpen) {
            System.out.printf(message);
            return Integer.parseInt(scanner.nextLine());
        }
        throw new InputReaderException("InputReader is not opened");
    }

    @Override
    public double readDouble() throws InputReaderException {
        if (isOpen) {
            return Double.parseDouble(scanner.nextLine());
        }
        throw new InputReaderException("InputReader is not opened");
    }

    @Override
    public double readDouble(String message) throws InputReaderException {
        if (isOpen) {
            System.out.printf(message);
            return Double.parseDouble(scanner.nextLine());
        }
        throw new InputReaderException("InputReader is not opened");
    }

    @Override
    public void open() {
        isOpen = true;
    }

    @Override
    public void close() throws InputReaderException {
        if (isOpen) {
            isOpen = false;
            scanner.close();
        } else {
            throw new InputReaderException("InputReader is not opened");
        }

    }
}
