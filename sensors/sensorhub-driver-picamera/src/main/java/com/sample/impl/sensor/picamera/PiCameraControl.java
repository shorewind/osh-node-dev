package com.sample.impl.sensor.picamera;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.command.CommandException;
import org.sensorhub.api.command.ICommandReceiver;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorControl;
import org.vast.swe.SWEHelper;

import javax.validation.constraints.Max;

public class PiCameraControl extends AbstractSensorControl<PiCameraSensor> {

    DataRecord commandDataStruct;
    private static final String SENSOR_CONTROL_NAME = "PiCameraControl";

    private static final float MAX_TILT_ANGLE = 120f;
    private static final float MIN_TILT_ANGLE = 0f;

    protected PiCameraControl(PiCameraSensor parentSensor) {
        super(SENSOR_CONTROL_NAME, parentSensor);
    }

    protected static float getMinTiltAngle() {
        return MIN_TILT_ANGLE;
    }

    protected static float getMaxTiltAngle() {
        return MAX_TILT_ANGLE;
    }

    @Override
    public DataComponent getCommandDescription() {
        return commandDataStruct;
    }

    @Override
    protected boolean execCommand(DataBlock cmdData) {

        try {

            DataRecord commandData = commandDataStruct.copy();

            commandData.setData(cmdData);

            DataComponent tiltComponent = commandData.getField("Angle");

            DataBlock data = tiltComponent.getData();

            float angle = data.getIntValue();

            angle = (angle <= MIN_TILT_ANGLE) ? MIN_TILT_ANGLE : Math.min(angle, MAX_TILT_ANGLE);

//            parentSensor.tilt(angle);

        } catch (Exception e) {

            getLogger().error("Failed to command PiCameraSensor module: " + e.getMessage());

        }

        return true;
    }

    protected void init() {

        SWEHelper sweFactory = new SWEHelper();
        commandDataStruct = sweFactory.createRecord()
                .name(getName())
                .label("PiCameraSensor")
                .updatable(true)
                .definition(SWEHelper.getPropertyUri("CameraSensor"))
                .description("Controls camera's servo motor")
                .addField("Angle",
                        sweFactory.createQuantity()
                                .name("Tilt")
                                .label("Tilt")
                                .definition(SWEHelper.getPropertyUri("servo-angle"))
                                .description("Control the angle of tilt for camera servo motor")
                                .addAllowedInterval(MIN_TILT_ANGLE, MAX_TILT_ANGLE)
                                .uomCode("deg")
                                .value(0.0)
                                .build())
                .build();

    }
}
