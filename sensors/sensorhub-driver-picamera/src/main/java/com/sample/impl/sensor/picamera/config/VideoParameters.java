package com.sample.impl.sensor.picamera.config;

import org.sensorhub.api.config.DisplayInfo;

public class VideoParameters {

    @DisplayInfo.Required
    @DisplayInfo(desc = "Width of video frames")
    public int videoFrameWidth = 1280;

    @DisplayInfo.Required
    @DisplayInfo(desc = "Height of video frames")
    public int videoFrameHeight = 720;

}
