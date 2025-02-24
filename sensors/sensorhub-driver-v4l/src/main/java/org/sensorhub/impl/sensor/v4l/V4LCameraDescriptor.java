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

import org.sensorhub.api.module.IModule;
import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.module.ModuleConfig;
import org.sensorhub.impl.module.JarModuleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Descriptor of V4L driver module for automatic discovery
 * by the ModuleRegistry
 * </p>
 *
 * @author Alex Robin
 * @since Sep 7, 2013
 */
public class V4LCameraDescriptor extends JarModuleProvider implements IModuleProvider
{

    private static final Logger logger = LoggerFactory.getLogger(V4LCameraDescriptor.class);

    @Override
    public Class<? extends IModule<?>> getModuleClass()
    {
        logger.info("getModuleClass called, returning V4LCameraDriver.class");
        return V4LCameraDriver.class;
    }


    @Override
    public Class<? extends ModuleConfig> getModuleConfigClass()
    {
        logger.info("getModuleConfigClass called, returning V4LCameraConfig.class");
        return V4LCameraConfig.class;
    }

}
