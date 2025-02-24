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

import net.opengis.swe.v20.*;
import org.bytedeco.javacv.*;
//import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.videocam.VideoCamHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.Boolean;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;


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
    private static final String VIDEO_STREAM_URL = "udp://192.168.1.185:8554";

    private FrameGrabber frameGrabber;
//    private OpenCVFrameGrabber frameGrabber;

    private static final Logger logger = LoggerFactory.getLogger(com.sample.impl.sensor.rpicam.PiCameraOutput.class);

    private DataComponent dataStruct;
    private DataEncoding dataEncoding;

    private Boolean stopProcessing = false;
    private final Object processingLock = new Object();

    private static final int MAX_NUM_TIMING_SAMPLES = 10;
    private int setCount = 0;
    private final long[] timingHistogram = new long[MAX_NUM_TIMING_SAMPLES];
    private final Object histogramLock = new Object();

    private Thread worker;

    private Process libcameraProcess;

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

//        try {
////            frameGrabber = FrameGrabber.createDefault(0);
////            frameGrabber = new OpenCVFrameGrabber("rtsp://192.168.2.136:8554/stream1");
//
////            frameGrabber = FrameGrabber.createDefault("/base/axi/pcie@120000.rp1/i2c@88000/imx219@10");
//
//            frameGrabber = new FFmpegFrameGrabber(VIDEO_STREAM_URL);
//
//            logger.info("Frame Grabber created {}", frameGrabber);
//
//        } catch (Exception e) {
//
//            logger.debug("Unable to connect to camera\n{}", e.getMessage());
//
//            throw new SensorException("Failed to establish connection with camera", e);
//        }
//
//        frameGrabber.setFormat(VIDEO_FORMAT);
//        frameGrabber.setVideoCodecName(VIDEO_FORMAT);
//        frameGrabber.setImageHeight(parentSensor.getConfiguration().videoParameters.videoFrameHeight);
//        frameGrabber.setImageWidth(parentSensor.getConfiguration().videoParameters.videoFrameWidth);
        logger.debug("Initializing PiCameraOutput with libcamera-vid");

        try {
            // Start libcamera-vid process
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(
                    "/usr/bin/libcamera-vid", "-n", "-t", "0",
                    "--framerate", String.valueOf(parentSensor.getConfiguration().videoParameters.videoFrameRate),
                    "--width", String.valueOf(parentSensor.getConfiguration().videoParameters.videoFrameWidth),
                    "--height", String.valueOf(parentSensor.getConfiguration().videoParameters.videoFrameHeight),
                    "--codec", VIDEO_FORMAT, "-o", VIDEO_STREAM_URL
            );
            builder.inheritIO();
            libcameraProcess = builder.start();

            logger.info("Started libcamera-vid process for video streaming");

            // Initialize FFmpegFrameGrabber
            frameGrabber = new OpenCVFrameGrabber(VIDEO_STREAM_URL);
            frameGrabber.setFrameRate(parentSensor.getConfiguration().videoParameters.videoFrameRate);
            frameGrabber.setFormat(VIDEO_FORMAT);
            frameGrabber.setImageWidth(parentSensor.getConfiguration().videoParameters.videoFrameWidth);
            frameGrabber.setImageHeight(parentSensor.getConfiguration().videoParameters.videoFrameHeight);
            frameGrabber.setPixelFormat(AV_PIX_FMT_BGR24);
//            frameGrabber.setOption("fifo_size", 1000000);
//            frameGrabber.setOption("overrun_nonfatal", "1");

            logger.info("Initialized OpenCVFrameGrabber");
        } catch (Exception e) {
            logger.error("Failed to initialize PiCameraOutput", e);
            throw new SensorException("Failed to initialize libcamera and FrameGrabber", e);
        }

        int videoFrameHeight = frameGrabber.getImageHeight();
        int videoFrameWidth = frameGrabber.getImageWidth();

        // Get an instance of SWE Factory suitable to build components
        VideoCamHelper sweFactory = new VideoCamHelper();

        DataStream outputDef = sweFactory.newVideoOutputMJPEG(getName(), videoFrameWidth, videoFrameHeight);

        dataStruct = outputDef.getElementType();

        dataStruct.setLabel(SENSOR_OUTPUT_LABEL);

        dataStruct.setDescription(SENSOR_OUTPUT_DESCRIPTION);

        dataEncoding = outputDef.getEncoding();

        DataStream videoStream = sweFactory.newVideoOutputMJPEG(getName(), videoFrameWidth, videoFrameHeight);
        DataComponent videoStreamOutput = videoStream.getElementType();

        // TODO: Create data record description

        dataStruct = sweFactory.createRecord()
                .name(getName())
                .definition(SENSOR_OUTPUT_LABEL)
                .description("Video feed")
                .addField("samplingTime", sweFactory.createTime()
                        .asSamplingTimeIsoUTC())
                .addField("videoFrame", videoStreamOutput)
                .build();

        dataEncoding = videoStream.getEncoding();

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
        logger.info("Starting frame grabber: {}", frameGrabber);
//        logger.info("Frame grabber video codec: {}", frameGrabber.getVideoCodecName());
//        logger.info("Frame grabber format: {}", frameGrabber.getFormat());

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

//        if (frameGrabber != null) {
//
//            try {
//
//                frameGrabber.stop();
//
//            } catch(FrameGrabber.Exception e) {
//
//                logger.error("Failed to stop FrameGrabber: {}", e.getMessage());
//
//            }
//
//        }

        try {
            if (frameGrabber != null) {
                frameGrabber.stop();
                logger.info("FrameGrabber stopped");
            }
            if (libcameraProcess != null) {
                libcameraProcess.destroy();
                logger.info("libcamera-vid process stopped");
            }
        } catch (Exception e) {
            logger.error("Error stopping PiCameraOutput", e);
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
        System.out.println("Running video feed display");
//        CanvasFrame canvasFrame = new CanvasFrame("Raspberry Pi Camera");

        // Set canvas size and make it visible
//        canvasFrame.setSize(parentSensor.getConfiguration().videoParameters.videoFrameWidth,
//                parentSensor.getConfiguration().videoParameters.videoFrameHeight);
//        canvasFrame.setVisible(true);

        boolean processSets = true;
        long lastSetTimeMillis = System.currentTimeMillis();

        try {
//            frameGrabber.start();
//            System.out.println("Frame grabber started successfully");

            while (processSets) {
                // Grab the next frame
                Frame frame = frameGrabber.grab();
                logger.debug("Frame grabber started successfully {}", frame);

                if (frame != null) {
                    // Show the frame on the CanvasFrame
//                    canvasFrame.showImage(frame);

                    // Publish data event
                    DataBlock dataBlock;
                    if (latestRecord == null) {
                        dataBlock = dataStruct.createDataBlock();
                        System.out.println("Created dataBlock");
                    } else {
                        dataBlock = latestRecord.renew();
                    }

                    synchronized (histogramLock) {
                        int setIndex = setCount % MAX_NUM_TIMING_SAMPLES;

                        // Record sampling time
                        timingHistogram[setIndex] = System.currentTimeMillis() - lastSetTimeMillis;

                        // Update last sampling time
                        lastSetTimeMillis = System.currentTimeMillis();
                    }

                    setCount++;

                    double timestamp = System.currentTimeMillis() / 1000d;

                    // Populate sampling time
                    dataBlock.setDoubleValue(0, timestamp);
                    System.out.println("Timestamp");


                    // Extract frame data as byte array and populate videoFrame field
                    AbstractDataBlock frameData = ((DataBlockMixed) dataBlock).getUnderlyingObject()[1];
                    System.out.println("Extracted frame data");

                    BufferedImage image = new Java2DFrameConverter().convert(frame);
                    System.out.println("Converted frame data");

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpg", byteArrayOutputStream);
                    byteArrayOutputStream.flush();
                    System.out.println("Write jpg");


                    byte[] imageData = byteArrayOutputStream.toByteArray();
                    byteArrayOutputStream.close();
                    System.out.println("Set byte array");

                    // Publish byte array to data record
                    frameData.setUnderlyingObject(imageData);

                    // Set latest record and publish the event
                    latestRecord = dataBlock;
                    latestRecordTime = System.currentTimeMillis();

                    eventHandler.publish(new DataEvent(latestRecordTime, this, dataBlock));

                    synchronized (processingLock) {
                        processSets = !stopProcessing;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error displaying video feed or processing data", e);
        } finally {
            try {
                frameGrabber.stop();
//                canvasFrame.dispose();
            } catch (Exception e) {
                logger.error("Error stopping FrameGrabber or disposing CanvasFrame", e);
            }

            // Reset the flag for restarting
            stopProcessing = false;
        }
    }

//    @Override
//    public void run() {
//
//        boolean processSets = true;
//
//        long lastSetTimeMillis = System.currentTimeMillis();
//
//        try {
//
//            while (processSets) {
//
//                Frame frame = frameGrabber.grab();
//
//                DataBlock dataBlock;
//                if (latestRecord == null) {
//
//                    dataBlock = dataStruct.createDataBlock();
//
//                } else {
//
//                    dataBlock = latestRecord.renew();
//                }
//
//                synchronized (histogramLock) {
//
//                    int setIndex = setCount % MAX_NUM_TIMING_SAMPLES;
//
//                    // Get a sampling time for latest set based on previous set sampling time
//                    timingHistogram[setIndex] = System.currentTimeMillis() - lastSetTimeMillis;
//
//                    // Set latest sampling time to now
//                    lastSetTimeMillis = timingHistogram[setIndex];
//                }
//
//                ++setCount;
//
//                double timestamp = System.currentTimeMillis() / 1000d;
//
//                // populate sample time
//                dataBlock.setDoubleValue(0, timestamp);
//
//                // extract frame data as byte array and populate videoFrame field
//                AbstractDataBlock frameData = ((DataBlockMixed) dataBlock).getUnderlyingObject()[1];
//
//                BufferedImage image = new Java2DFrameConverter().convert(frame);
//
//                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//
//                byte[] imageData;
//
//                ImageIO.write(image,"jpg",byteArrayOutputStream);
//
//                byteArrayOutputStream.flush();
//
//                imageData = byteArrayOutputStream.toByteArray();
//
//                byteArrayOutputStream.close();
//
//                // publish byte array to data record
//
//                frameData.setUnderlyingObject(imageData);
//
//                latestRecord = dataBlock;
//
//                latestRecordTime = System.currentTimeMillis();
//
//                eventHandler.publish(new DataEvent(latestRecordTime, com.sample.impl.sensor.rpicam.PiCameraOutput.this, dataBlock));
//
//                synchronized (processingLock) {
//
//                    processSets = !stopProcessing;
//                }
//            }
//
//        } catch (Exception e) {
//
//            logger.error("Error in worker thread: {}", Thread.currentThread().getName(), e);
//
//        } finally {
//
//            // Reset the flag so that when driver is restarted loop thread continues
//            // until doStop called on the output again
//            stopProcessing = false;
//
//            logger.debug("Terminating worker thread: {}", this.name);
//        }
//    }
}
