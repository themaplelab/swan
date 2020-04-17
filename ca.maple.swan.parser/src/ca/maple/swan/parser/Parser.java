package ca.maple.swan.parser;

import ca.maple.swan.utils.ExceptionReporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;


// Direct port of https://github.com/tensorflow/swift/blob/master/Sources/SIL/Parser.swift
public class Parser {

    private String path;
    private char[] chars;
    private int cursor = 0;

    public int position() {
        return cursor;
    }

    public Parser(Path path) throws Error {
        this.path = path.toString();
        try {
            byte[] data = Files.readAllBytes(path);
            // Assume UTF-8 encoding.
            String text = new String(data, StandardCharsets.UTF_8);
            this.chars = text.toCharArray();
        } catch (IOException e) {
            ExceptionReporter.report(new Error(this.path, "file not found"));
        }
    }

    public Parser(String s) {
        this.path = "<memory>";
        this.chars = s.toCharArray();
        skipTrivia();
    }

    // MARK: "Token"-level APIs

    /// Check whether chars[cursor..] starts with a given string.
    public boolean peek(String query) {
        assert(!query.isEmpty());
        return Arrays.equals(
                Arrays.copyOfRange(chars, cursor, cursor + query.length()),
                query.toCharArray());
    }

    /// If chars[cursor..] starts with a given string, consume string and skip trivia afterwards.
    public void take(String query) throws Error {
        if (!peek(query)) {
            ExceptionReporter.report(parseError(query + " expected"));
        }
        cursor += query.length();
        skipTrivia();
    }

    /// If chars[cursor..-1] starts with a given string, consume string, skip trivia and return true.
    /// Otherwise, return false.
    public boolean skip(String query) {
        if (!peek(query)) {
            return false;
        }
        cursor += query.length();
        skipTrivia();
        return true;
    }

    /// Consume characters starting from cursor while a given predicate keeps being true and
    /// return the consumed string. Skip trivia afterwards.
    // TODO: func take(while fn: (Character) -> Bool) -> String { }

    /// Consume characters starting from cursor while a given predicate keeps being true and
    /// report whether something was consumed. Skip trivia afterwards.
    // TODO: func skip(while fn: (Character) -> Bool) -> Bool { }

    private void skipTrivia() {
        if (cursor < chars.length) {
            return;
        }
        if (Character.isWhitespace(chars[cursor])) {
            cursor += 1;
            skipTrivia();
        }
    }

    // MARK: Tree-level APIs

    // TODO...

    // MARK: Error reporting APIs

    /// Raise a parser error at a given position.

    public Error parseError(String message) {
        return parseError(message, null);
    }

    public Error parseError(String message, Integer at) {
        int position = at != null ? at : cursor;
        char[] newlines; // TODO
        int line = 0; // TODO
        int column = 0; // TODO
        return new Error(path, line, column, message);
    }

    public class Error extends Exception {
        String path;
        Integer line = null;
        Integer column = null;
        String message;

        public Error(String path, String message) {
            this.path = path;
            this.message = message;
        }

        public Error(String path, int line, int column, String message) {
            this.path = path;
            this.line = line;
            this.column = column;
            this.message = message;
        }

        public String description() {
            if (line == null) {
                return path + ": " + message;
            }
            if (column == null) {
                return path + ":" + line + ": " + message;
            }
            return path + ":" + line + ":" + column + ": " + message;
        }

        @Override
        public String getMessage() {
            return description();
        }
    }
}
