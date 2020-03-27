package es.ollbap.radiodownloader.util;

public class Time {
    int hour;
    int minute;

    public Time(int hour, int minute) throws IncorrectTimeFormatException {
        this.hour = hour;
        this.minute = minute;
        validate();
    }

    public Time(String text) throws IncorrectTimeFormatException {
        String[] parts = text.split(":");
        if (parts.length != 2) {
            throw new IncorrectTimeFormatException("Incorrect formatted time " + text);
        }

        try {
            hour = Integer.parseInt(parts[0]);
            minute = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IncorrectTimeFormatException("Incorrect formatted time " + text);
        }

        validate();
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    private void validate() throws IncorrectTimeFormatException {
        if (hour < 0 || hour > 23) {
            throw new IncorrectTimeFormatException("Incorrect formatted time " + toString());
        }
        if (minute < 0 || minute > 59) {
            throw new IncorrectTimeFormatException("Incorrect formatted time " + toString());
        }
    }

    public static class IncorrectTimeFormatException extends Exception {
        public IncorrectTimeFormatException(String message) {
            super(message);
        }
    }

    @Override
    public String toString() {
        return String.format("%02d:%02d", hour, minute);
    }
}
