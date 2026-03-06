package com.digital.menu.errors;

public final class ErrorMessages {
    private ErrorMessages() {}

    public static final String QR_ALREADY_EXISTS_TEMPLATE = "The QR for tabel number %d already exisits";
    public static final String INVALID_TABLE_NUMBER = "tableNumber must be greater than 0";
    public static final String ACTIVE_QR_NOT_FOUND = "Active QR not found for this table";
}
