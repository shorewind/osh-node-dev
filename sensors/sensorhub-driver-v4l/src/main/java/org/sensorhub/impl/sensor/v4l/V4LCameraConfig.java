/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.v4l;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.sensor.PositionConfig;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.api.sensor.PositionConfig.EulerOrientation;
import org.sensorhub.api.sensor.PositionConfig.LLALocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>
 * Configuration class for the generic Video4Linux camera driver
 * </p>
 *
 * @author Alex Robin
 * @since Sep 6, 2013
 */
public class V4LCameraConfig extends SensorConfig
{
    private static final Logger logger = LoggerFactory.getLogger(V4LCameraConfig.class);

    @Required
    @DisplayInfo(desc="Camera serial number (used as suffix to generate unique identifier URI)")
    public String serialNumber = "01";
    
    @DisplayInfo(desc="Name of video device to use (e.g. /dev/video0)")
    public String deviceName = "/dev/video0";
//    public String deviceName = "/base/axi/pcie@120000.rp1/i2c@88000/imx219@10";

    @DisplayInfo(desc="Default camera params to use on startup. These can then be changed with the control interface")
    public V4LCameraParams defaultParams = new V4LCameraParams();
    
    @DisplayInfo(desc="Camera geographic position")
    public PositionConfig position = new PositionConfig();
    
    
    @Override
    public LLALocation getLocation()
    {
        logger.info("Getting location " + position.location);
        return position.location;
    }
    
    
    @Override
    public EulerOrientation getOrientation()
    {
        logger.info("Getting orientation" + position.orientation);
        return position.orientation;
    }

}
