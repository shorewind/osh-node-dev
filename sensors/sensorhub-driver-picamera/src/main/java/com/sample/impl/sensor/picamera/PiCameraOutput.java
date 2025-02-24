/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2020-2021 Botts Innovative Research, Inc. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/
package com.sample.impl.sensor.picamera;

import net.opengis.swe.v20.*;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import org.vast.data.SWEFactory;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;
import org.vast.swe.helper.RasterHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.Boolean;
import java.util.Arrays;

/**
 * Output specification and provider for {@link PiCameraSensor}.
 *
 * @author your_name
 * @since date
 */
public class PiCameraOutput extends AbstractSensorOutput<PiCameraSensor> implements Runnable {

    private static final String SENSOR_OUTPUT_NAME = "PiCameraOutput";
    private static final String SENSOR_OUTPUT_LABEL = "CameraSensor";
    private static final String SENSOR_OUTPUT_DESCRIPTION = "Pi Camera Video Feed";
    private static final String VIDEO_FORMAT = "h264";

    private FrameGrabber frameGrabber;

    private static final Logger logger = LoggerFactory.getLogger(PiCameraOutput.class);

    private DataComponent dataStruct;
    private DataEncoding dataEncoding;

    private Boolean stopProcessing = false;
    private final Object processingLock = new Object();

    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private int setCount = 0;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private final Object histogramLock = new Object();

    private Thread worker;

    /**
     * Constructor
     *
     * @param parentSensor Sensor driver providing this output
     */
    PiCameraOutput(PiCameraSensor parentSensor) {

        super(SENSOR_OUTPUT_NAME, parentSensor);

        logger.debug("PiCameraOutput created");
    }

    /**
     * Initializes the data structure for the output, defining the fields, their ordering,
     * and data types.
     */
    void doInit() throws SensorException {

        logger.debug("Initializing PiCameraOutput");

        try {

//            frameGrabber = FrameGrabber.createDefault(0);
            frameGrabber = FrameGrabber.createDefault("/dev/video0");

        } catch (Exception e) {

            logger.debug("Unable to connect to camera\n{}", e.getMessage());

            throw new SensorException("Failed to establish connection with camera", e);
        }

        frameGrabber.setFormat(VIDEO_FORMAT);
        frameGrabber.setImageHeight(parentSensor.getConfiguration().videoParameters.videoFrameHeight);
        frameGrabber.setImageWidth(parentSensor.getConfiguration().videoParameters.videoFrameWidth);

        int videoFrameHeight = frameGrabber.getImageHeight();
        int videoFrameWidth = frameGrabber.getImageWidth();

        // Get an instance of SWE Factory suitable to build components
        VideoCamHelper sweFactory = new VideoCamHelper();

        DataStream outputDef = sweFactory.newVideoOutputMJPEG(getName(), videoFrameWidth, videoFrameHeight);

        dataStruct = outputDef.getElementType();

        dataStruct.setLabel(SENSOR_OUTPUT_LABEL);

        dataStruct.setDescription(SENSOR_OUTPUT_DESCRIPTION);

        dataEncoding = outputDef.getEncoding();

//        DataStream videoStream = sweFactory.newVideoOutputMJPEG(getName(), videoFrameWidth, videoFrameHeight);
//        DataComponent videoStreamOutput = videoStream.getElementType();
//
//        // TODO: Create data record description
//
//        dataStruct = sweFactory.createRecord()
//                .name(getName())
//                .definition(SWEHelper.getPropertyUri(SENSOR_OUTPUT_LABEL))
//                .description("Video feed")
//                .addField("samplingTime", sweFactory.createTime()
//                        .asSamplingTimeIsoUTC())
//                .addField("videoFrame", videoStreamOutput)
//                .build();
//
//        dataEncoding = videoStream.getEncoding();

        logger.debug("Initializing Output Complete");
    }

    /**
     * Begins processing data for output
     */
    public void doStart() throws SensorException {

        // Instantiate a new worker thread
        worker = new Thread(this, this.name);

        // TODO: Perform other startup

        logger.info("Starting worker thread: {}", worker.getName());

        if (frameGrabber != null) {

            try {

                frameGrabber.start();
                worker.start();

            } catch (FrameGrabber.Exception e) {

                logger.error("Failed to start FrameGrabber: {}", e.getMessage());

                throw new SensorException("Failed to start FrameGrabber");
            }

        } else {

            logger.error("Failed to create FrameGrabber");

            throw new SensorException("Failed to create FrameGrabber");
        }

        // Start the worker thread
        //worker.start();
    }

    /**
     * Terminates processing data for output
     */
    public void doStop() {

        synchronized (processingLock) {

            stopProcessing = true;
        }

        if (frameGrabber != null) {

            try {

                frameGrabber.stop();

            } catch(FrameGrabber.Exception e) {

                logger.error("Failed to stop FrameGrabber: {}", e.getMessage());

            }

        }

        // TODO: Perform other shutdown procedures
    }

    /**
     * Check to validate data processing is still running
     *
     * @return true if worker thread is active, false otherwise
     */
    public boolean isAlive() {

        return worker.isAlive();
    }

    @Override
    public DataComponent getRecordDescription() {

        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {

        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {

        long accumulator = 0;

        synchronized (histogramLock) {

            for (int idx = 0; idx < MAX_NUM_TIMING_SAMPLES; ++idx) {

                accumulator += timingHistogram[idx];
            }
        }

        return accumulator / (double) MAX_NUM_TIMING_SAMPLES;
    }

    @Override
    public void run() {

        boolean processSets = true;

        long lastSetTimeMillis = System.currentTimeMillis();

        try {

            while (processSets) {

                Frame frame = frameGrabber.grab();

                DataBlock dataBlock;
                if (latestRecord == null) {

                    dataBlock = dataStruct.createDataBlock();

                } else {

                    dataBlock = latestRecord.renew();
                }

                synchronized (histogramLock) {

                    int setIndex = setCount % MAX_NUM_TIMING_SAMPLES;

                    // Get a sampling time for latest set based on previous set sampling time
                    timingHistogram[setIndex] = System.currentTimeMillis() - lastSetTimeMillis;

                    // Set latest sampling time to now
                    lastSetTimeMillis = timingHistogram[setIndex];
                }

                ++setCount;

                double timestamp = System.currentTimeMillis() / 1000d;

                // populate sample time
                dataBlock.setDoubleValue(0, timestamp);

                // extract frame data as byte array and populate videoFrame field
                AbstractDataBlock frameData = ((DataBlockMixed) dataBlock).getUnderlyingObject()[1];

                BufferedImage image = new Java2DFrameConverter().convert(frame);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                byte[] imageData;

                ImageIO.write(image,"jpg",byteArrayOutputStream);

                byteArrayOutputStream.flush();

                imageData = byteArrayOutputStream.toByteArray();

                byteArrayOutputStream.close();

                // publish byte array to data record

                frameData.setUnderlyingObject(imageData);

                latestRecord = dataBlock;

                latestRecordTime = System.currentTimeMillis();

                eventHandler.publish(new DataEvent(latestRecordTime, PiCameraOutput.this, dataBlock));

                synchronized (processingLock) {

                    processSets = !stopProcessing;
                }
            }

        } catch (Exception e) {

            logger.error("Error in worker thread: {}", Thread.currentThread().getName(), e);

        } finally {

            // Reset the flag so that when driver is restarted loop thread continues
            // until doStop called on the output again
            stopProcessing = false;

            logger.debug("Terminating worker thread: {}", this.name);
        }
    }
}
