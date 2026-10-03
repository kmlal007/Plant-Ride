package com.plantride.notification;

final class Masking {

    private Masking() {
    }

    /** Keeps only the last 4 digits; phone numbers are personal data and stay out of logs. */
    static String phone(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }
}
