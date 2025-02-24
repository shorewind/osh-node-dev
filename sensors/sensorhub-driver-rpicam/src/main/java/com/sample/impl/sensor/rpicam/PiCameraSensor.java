/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020-2021 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package com.sample.impl.sensor.rpicam;

//import com.pi4j.Pi4J;
//import com.pi4j.context.Context;
//import com.pi4j.library.pigpio.PiGpio;
//import com.pi4j.plugin.pigpio.provider.gpio.digital.PiGpioDigitalInputProvider;
//import com.pi4j.plugin.pigpio.provider.pwm.PiGpioPwmProvider;
//import com.pi4j.plugin.raspberrypi.platform.RaspberryPiPlatform;
import net.opengis.sensorml.v20.PhysicalSystem;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.sensorML.SMLHelper;

import static com.pi4j.plugin.pigpio.provider.gpio.digital.PiGpioDigitalOutputProvider.*;

/**
 * Sensor driver providing sensor description, output registration, initialization and shutdown of driver and outputs.
 *
 * @author your_name
 * @since date
 */
public class PiCameraSensor extends AbstractSensorModule<PiCameraConfig> {

    private static final Logger logger = LoggerFactory.getLogger(com.sample.impl.sensor.rpicam.PiCameraSensor.class);

//    Context pi4j;
//    ServoMotor servoMotor;
    PiCameraOutput output;

    @Override
    protected void updateSensorDescription() {

        synchronized (sensorDescLock) {

            super.updateSensorDescription();

            if(!sensorDescription.isSetDescription()) {

                sensorDescription.setDescription("A camera attached to speed trap device " +
                        "hosted on a raspberry pi.");

                SMLHelper smlHelper = new SMLHelper();
                smlHelper.edit((PhysicalSystem) sensorDescription)
                        .addIdentifier(smlHelper.identifiers.serialNumber(config.serialNumber))
                        .addClassifier(smlHelper.classifiers.sensorType("Pi Camera"));
                // add characteristics of image pixels?

            }

        }

    }



    @Override
    public void doInit() throws SensorHubException {

        super.doInit();

        // Generate identifiers
        generateUniqueID("urn:osh:sensor:", config.serialNumber);
        generateXmlID("PI_CAMERA", config.serialNumber);

        // Create and initialize output
        output = new PiCameraOutput(this);

        addOutput(output, false);
        addLocationOutput(1.0);

        output.doInit();

        // check config for whether you connect to GPIO
//        if(config.isGPIOConnected) {
//
//        // initialize PI4J with PWM provider
//        final var piGpio = PiGpio.newNativeInstance();
//        pi4j = Pi4J.newContextBuilder()
//                .noAutoDetect()
//                .add(new RaspberryPiPlatform() {
//                    @Override
//                    protected String[] getProviders() {
//                        return new String[]{};
//                    }
//                })
//                .add(PiGpioDigitalInputProvider.newInstance(piGpio),
//                        newInstance(piGpio),
//                        PiGpioPwmProvider.newInstance(piGpio)
//                )
//                .build();
//
//        // Example servo implementation from
//        // https://pi4j.com/examples/components/servo/
//        servoMotor = new ServoMotor(pi4j, config.cameraPinConfig.pinConfig, 50, PiCameraControl.getMinTiltAngle(), PiCameraControl.getMaxTiltAngle(), 2.0f, 12f);
//
//        PiCameraControl control = new PiCameraControl(this);
//
//        addControlInput(control);
//
//        control.init();
//
//        }

    }

    @Override
    public void doStart() throws SensorHubException {

        if (null != output) {

            // Allocate necessary resources and start outputs
            output.doStart();
        }

    }

    @Override
    public void doStop() throws SensorHubException {

        if (null != output) {

            output.doStop();
        }

//        servoMotor.setAngle(PiCameraControl.getMaxTiltAngle());
//        pi4j.shutdown();

    }

    @Override
    public boolean isConnected() {

        // Determine if sensor is connected
        return output.isAlive();
    }

//    protected void tilt(float angle) {
//
//        if (servoMotor != null) {
//            logger.info("Tilting by " + angle + " degrees");
//            servoMotor.setAngle(angle);
//        } else {
//            logger.error("Servo Motor not initialized");
//        }
//
//    }

}
