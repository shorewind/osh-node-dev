package com.sample.impl.sensor.picamera.helpers;

import java.util.List;

import com.pi4j.context.Context;
import com.pi4j.io.pwm.Pwm;
import com.pi4j.io.pwm.PwmConfig;

/**
 * Helper Class from pi4j components
 * https://pi4j.com/examples/components/servo/
 */
public class PwmActuator {
    protected static final List<PIN> AVAILABLE_PWM_PINS = List.of(PIN.PWM12, PIN.PWM13, PIN.PWM18, PIN.PWM19);

    protected final Pwm pwm;

    protected PwmActuator(Context pi4J, PwmConfig config) {
        if(AVAILABLE_PWM_PINS.stream().noneMatch(p -> p.getPin() == config.address())){
            throw new IllegalArgumentException("Pin " + config.address() + " is not a PWM Pin");
        }
        pwm = pi4J.create(config);
    }

    public void reset() {
        pwm.off();
    }
}