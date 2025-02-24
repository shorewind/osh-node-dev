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

import com.sample.impl.sensor.rpicam.config.VideoParameters;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.sensor.PositionConfig;
import org.sensorhub.api.sensor.SensorConfig;

/**
 * Configuration settings for the {@link PiCameraSensor} driver exposed via the OpenSensorHub Admin panel.
 * <p>
 * Configuration settings take the form of
 * <code>
 * DisplayInfo(desc="Description of configuration field to show in UI")
 * public Type configOption;
 * </code>
 * <p>
 * Containing an annotation describing the setting and if applicable its range of values
 * as well as a public access variable of the given Type
 *
 * @author your_name
 * @since date
 */
public class PiCameraConfig extends SensorConfig {

    /**
     * The unique identifier for the configured sensor (or sensor platform).
     */
    @DisplayInfo.Required
    @DisplayInfo(desc = "Serial number or unique identifier")
    public String serialNumber = "picamera001";

    /**
     * Video camera configuration
     */
    @DisplayInfo(label = "Video Camera Parameters", desc = "Parameters for camera configuration")
    public VideoParameters videoParameters = new VideoParameters();

//    /**
//     * Pin configuration
//     */
//    @DisplayInfo.Required
//    @DisplayInfo(label = "PinConfig", desc = "Pin configuration for tilt servo")
//    public CameraPinConfig cameraPinConfig = new CameraPinConfig();
//
//    @DisplayInfo.Required
//    @DisplayInfo(label = "Connect to GPIO", desc = "Choose whether or not to connect to pi GPIO")
//    public boolean isGPIOConnected = true;

    @DisplayInfo.Required
    @DisplayInfo(desc = "PiCamera Location")
    public PositionConfig.LLALocation location = new PositionConfig.LLALocation();

    public PiCameraConfig() {
        location.lat = 34.735915156141196;
        location.lon = -86.72325187317927;
        location.alt = 0.000;
    }

    @Override
    public PositionConfig.LLALocation getLocation() {
        return location;
    }

}
