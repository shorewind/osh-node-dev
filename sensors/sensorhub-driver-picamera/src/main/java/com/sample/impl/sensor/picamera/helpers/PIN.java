package com.sample.impl.sensor.picamera.helpers;

/**
 * Helper Class from pi4j components
 * https://pi4j.com/examples/components/servo/
 */
public enum PIN {
    PWM12(12),
    PWM13(13),
    PWM18(18),
    PWM19(19);

    private final int pin;

    PIN(int pin) {
        this.pin = pin;
    }

    public int getPin() {
        return pin;
    }
}