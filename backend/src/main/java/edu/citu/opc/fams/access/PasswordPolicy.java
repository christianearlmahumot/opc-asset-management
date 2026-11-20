package edu.citu.opc.fams.access;

import edu.citu.opc.fams.common.ApiException;

/** One place for the password rules, used by account creation, reset and change. */
final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 72;   // BCrypt ignores bytes beyond 72

    private PasswordPolicy() {
    }

    static void check(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("The password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            throw ApiException.badRequest("The password must be at most " + MAX_LENGTH + " characters.");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            throw ApiException.badRequest("The password cannot be the same as the username.");
        }
    }
}
