/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.bno08x;

import com.pi4j.io.i2c.I2CConfig;
import org.sensorhub.api.comm.CommProviderConfig;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.impl.sensor.bno08x.Bno08xSensor;

public class Bno08xConfig extends SensorConfig
{
    
    @Required
    @DisplayInfo(desc="Sensor serial number (used as suffix to generate unique identifier URI)")
    public String serialNumber = "01";

//    @Required
//    @DisplayInfo(desc="GPIO Pin for SDA")
//    public String gpioSda = "2";  // GPIO2 = SDA1 3
//
//    @Required
//    @DisplayInfo(desc="GPIO Pin for SCL")
//    public String gpioScl = "3";  // GPIO3 = SCL1 5

    @Required
    @DisplayInfo(desc="I2C Bus")
    public int i2cBus = 0x01;

    @Required
    @DisplayInfo(desc="I2C Address")
    public int i2cAddress = 0x4a;
    
//    @DisplayInfo(desc="Communication settings to connect to IMU data stream")
//    public CommProviderConfig<?> commSettings;

//    @DisplayInfo(desc="I2C communication settings to connect to BNO sensor")
//    public CommProviderConfig<I2CConfig> commSettings;

//    public String command = String.valueOf(true);
    
    public Bno08xConfig()
    {
        this.moduleClass = Bno08xSensor.class.getCanonicalName();
    }
}
