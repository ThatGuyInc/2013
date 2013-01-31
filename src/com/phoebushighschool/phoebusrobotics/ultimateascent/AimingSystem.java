package com.PhoebusHighSchool.PhoebusRobotics.UltimateAscent;

import com.sun.squawk.util.MathUtils;
import edu.wpi.first.wpilibj.PIDSource;
import edu.wpi.first.wpilibj.camera.*;
import edu.wpi.first.wpilibj.image.*;

/*
 */
public class AimingSystem implements PIDSource {

    final int XMAXSIZE = 24;
    final int XMINSIZE = 24;
    final int YMAXSIZE = 24;
    final int YMINSIZE = 48;
    final double xMax[] = {1, 1, 1, 1, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, 1, 1, 1, 1};
    final double xMin[] = {.4, .6, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, .1, 0.6, 0};
    final double yMax[] = {1, 1, 1, 1, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, .5, 1, 1, 1, 1};
    final double yMin[] = {.4, .6, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05,
        .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05, .05,
        .05, .05, .6, 0};
    final int RECTANGULARITY_LIMIT = 60;
    final int ASPECT_RATIO_LIMIT = 75;
    final int X_EDGE_LIMIT = 40;
    final int Y_EDGE_LIMIT = 60;
    final double IMAGE_WIDTH = 640.0;
    final double TARGET_WIDTH = 60.0;
    int imageState = 0;
    AxisCamera camera;
    Ultrasonic ultrasonicSensor;
    CriteriaCollection cc;
    ColorImage image;
    BinaryImage thresholdImage;
    BinaryImage convexHullImage;
    BinaryImage filteredImage;
    ParticleAnalysisReport[] reports;
    ParticleAnalysisReport r;
    Scores score;
    AimingSystem.Target[] highTargets;
    AimingSystem.Target[] middleTargets;
    AimingSystem.Target target;

    public AimingSystem() {
        camera = AxisCamera.getInstance(Parameters.cameraIP);
        camera.writeResolution(AxisCamera.ResolutionT.k320x240);
        camera.writeExposurePriority(AxisCamera.ExposurePriorityT.imageQuality);
        camera.writeExposureControl(AxisCamera.ExposureT.hold);
        camera.writeWhiteBalance(AxisCamera.WhiteBalanceT.fixedIndoor);
        cc = new CriteriaCollection();
        cc.addCriteria(NIVision.MeasurementType.IMAQ_MT_AREA, 500, 65535, false);
        ultrasonicSensor = new Ultrasonic(Parameters.UltrasonicAnalogChannel);
    }

    public class Scores {

        double rectangularity;
        double aspectRatioHigh;
        double aspectRatioMiddle;
        double xEdge;
        double yEdge;
    }

    public class Target {

        double aspectRatio;
        boolean middle;
        double center_mass_x;
        double target_width;
    }

    /**
     * This method will find the target we are aiming at, and it's center of
     * mass in the x axis.
     */
    public void processImage() {
        r = null;
        try {
            if (reports == null) {
                image = camera.getImage();
                thresholdImage = image.thresholdRGB(0, 55, 200, 255, 165, 255);  // green values
//              thresholdImage = image.thresholdHSV(115, 125, 195, 255, 220, 255);
                convexHullImage = thresholdImage.convexHull(true);
                filteredImage = convexHullImage.particleFilter(cc);
                reports = filteredImage.getOrderedParticleAnalysisReports();
            } else if (reports != null) {
                switch (imageState) {
                    case 0:
                        image = camera.getImage();
                        imageState++;
                        break;
                    case 1:
                        thresholdImage = image.thresholdRGB(0, 25, 230, 255, 195, 225);  // green values
//                      thresholdImage = image.thresholdHSV(115, 125, 195, 255, 220, 255);
                        imageState++;
                        break;
                    case 2:
                        convexHullImage = thresholdImage.convexHull(true);
                        imageState++;
                        break;
                    case 3:
                        filteredImage = convexHullImage.particleFilter(cc);
                        imageState++;
                        break;
                    case 4:
                        reports = filteredImage.getOrderedParticleAnalysisReports();
                        scoreParticles();
                        imageState = 0;
                        break;
                }
            }

            if (reports == null) {
                filteredImage.free();
                convexHullImage.free();
                thresholdImage.free();
                image.free();
            } else if (reports != null) {
                switch (imageState) {
                    case 2:
                        image.free();
                        break;
                    case 4:
                        convexHullImage.free();
                        break;
                    case 0:
                        thresholdImage.free();
                        filteredImage.free();
                        break;
                }
            }
        } catch (NIVisionException e) {
        } catch (AxisCameraException e) {
        }
    }

    /**
     * scoreParticles()
     *
     * This method takes the particle analysis that was produced in
     * getParticleAnalysis() and determines which particles are targets and then
     * which target is the one we are aiming for. The targets are selected for
     * by scoring the particles based on rectangularity, aspect ratio,
     * hollowness, and straightness of lines. After determining particles that
     * have characteristics of targets the particles are compared based on
     * aspect ratio, and the target with the best aspect ratio is selected.
     *
     * @throws NIVisionException
     */
    public void scoreParticles() throws NIVisionException {
        boolean middle = Parameters.GO_FOR_MIDDLE_TARGET;
                int nHigh = 0;
                int nMiddle = 0;

                for (int i = 0; i < reports.length; i++) {
                    r = reports[i];

                    score.rectangularity = scoreRectangularity(r);
                    score.aspectRatioHigh = scoreAspectRatio(filteredImage, r, i, false);
                    score.aspectRatioMiddle = scoreAspectRatio(filteredImage, r, i, true);
                    score.xEdge = scoreXEdge(thresholdImage, r);
                    score.yEdge = scoreYEdge(thresholdImage, r);

                    if (scoreCompare(score, false)) {
                        highTargets[nHigh].aspectRatio = score.aspectRatioHigh;
                        highTargets[nHigh].center_mass_x = r.center_mass_x;
                        highTargets[nHigh].target_width = r.boundingRectWidth;
                        highTargets[nHigh].middle = false;
                        nHigh++;
                    } else if (scoreCompare(score, true)) {
                        middleTargets[nMiddle].aspectRatio = score.aspectRatioMiddle;
                        middleTargets[nMiddle].center_mass_x = r.center_mass_x;
                        middleTargets[nMiddle].target_width = r.boundingRectWidth;
                        highTargets[nHigh].middle = true;
                        nMiddle++;
                    }
                }

                target = TargetCompare(highTargets, middleTargets, middle);
    }

    /**
     * scoreRectangularity()
     *
     * This method scores a particle from 0 - 100 based on the ratio of the area
     * of the particle to the area of the rectangle that bounds it. A score of
     * 100 means that the particle is perfectly rectangular.
     *
     * @param r the analysis report for the particle, used to determine the area
     * of the bounding rectangle.
     * @return the score of the particle.
     */
    public double scoreRectangularity(ParticleAnalysisReport r) {
        if ((r.boundingRectHeight * r.boundingRectWidth) != 0.0) {
            return 100 * (r.particleArea / (r.boundingRectHeight * r.boundingRectWidth));
        } else {
            return 0.0;
        }
    }

    /**
     * scoreAspectRatio()
     *
     * This method scores the particle from 0 - 100 based on how similar its
     * aspect ratio is to the aspect ratio of either the high target or the
     * middle target. A score of 100 means that the target has an aspect ratio
     * identical to either the middle or high target.
     *
     * @param image the image from which the particle originates.
     * @param report the analysis of the particle
     * @param middle true if aspect ratio to be compared to is the middle
     * target, false if it is the high target.
     * @return the score of the particle, from 0 - 100
     * @throws NIVisionException
     */
    public double scoreAspectRatio(BinaryImage image, ParticleAnalysisReport report,
            int particleNumber, boolean middle) throws NIVisionException {
        double rectLong, rectShort, aspectRatio, idealAspectRatio;

        rectLong = NIVision.MeasureParticle(image.image, particleNumber, false,
                NIVision.MeasurementType.IMAQ_MT_EQUIVALENT_RECT_LONG_SIDE);
        rectShort = NIVision.MeasureParticle(image.image, particleNumber, false,
                NIVision.MeasurementType.IMAQ_MT_EQUIVALENT_RECT_SHORT_SIDE);
        if (middle) {
            idealAspectRatio = 62 / 29;
        } else {
            idealAspectRatio = 62 / 20;
        }

        if (report.boundingRectWidth > report.boundingRectHeight) {
            aspectRatio = 100 * (1 - Math.abs((1 - ((rectLong / rectShort) / idealAspectRatio))));
        } else {
            aspectRatio = 100 * (1 - Math.abs((1 - ((rectShort / rectLong) / idealAspectRatio))));
        }

        return Math.max(0, Math.min(aspectRatio, 100.0));
    }

    /**
     * scoreXEdge()
     *
     * This method scores the particle from 0 - 100 based on how solid the
     * vertical edges are and how hollow the center of the particle are.
     *
     * @param image the image from which the particle originated, needs to be
     * pre-convex hull
     * @param report the analysis of the particle
     * @return the score of the particle from 0 - 100
     * @throws NIVisionException
     */
    public double scoreXEdge(BinaryImage image, ParticleAnalysisReport report) throws NIVisionException {
        double total = 0;
        LinearAverages averages;

        NIVision.Rect rect = new NIVision.Rect(report.boundingRectTop, report.boundingRectLeft, report.boundingRectHeight, report.boundingRectWidth);
        averages = NIVision.getLinearAverages(image.image, LinearAverages.LinearAveragesMode.IMAQ_COLUMN_AVERAGES, rect);
        float columnAverages[] = averages.getColumnAverages();
        for (int i = 0; i < (columnAverages.length); i++) {
            if (xMin[(i * (XMINSIZE - 1) / columnAverages.length)] < columnAverages[i]
                    && columnAverages[i] < xMax[i * (XMAXSIZE - 1) / columnAverages.length]) {
                total++;
            }
        }
        total = 100 * total / (columnAverages.length);
        return total;
    }

    /**
     * scoreYEdge()
     *
     * This method scores the particle from 0 - 100 based on how solid the
     * horizontal edges are and how hollow the center of the particle are.
     *
     * @param image the image from which the particle originated, needs to be
     * pre-convex hull
     * @param report the analysis of the particle
     * @return the score of the particle from 0 -100
     * @throws NIVisionException
     */
    public double scoreYEdge(BinaryImage image, ParticleAnalysisReport report) throws NIVisionException {
        double total = 0;
        LinearAverages averages;

        NIVision.Rect rect = new NIVision.Rect(report.boundingRectTop, report.boundingRectLeft, report.boundingRectHeight, report.boundingRectWidth);
        averages = NIVision.getLinearAverages(image.image, LinearAverages.LinearAveragesMode.IMAQ_ROW_AVERAGES, rect);
        float rowAverages[] = averages.getRowAverages();
        for (int i = 0; i < (rowAverages.length); i++) {
            if (yMin[(i * (YMINSIZE - 1) / rowAverages.length)] < rowAverages[i]
                    && rowAverages[i] < yMax[i * (YMAXSIZE - 1) / rowAverages.length]) {
                total++;
            }
        }
        total = 100 * total / (rowAverages.length);
        return total;
    }

    /**
     * scoreCompare()
     *
     * This method determines if a particle is a target based on if the
     * particles score compared to an given value.
     *
     * @param scores the score of the particle to be compared to
     * @param middle true if the score to be compared to is the middle target,
     * false if it is the high target
     * @return returns true if it qualifies as a target
     */
    boolean scoreCompare(AimingSystem.Scores scores, boolean middle) {
        boolean isTarget = true;

        isTarget &= scores.rectangularity > RECTANGULARITY_LIMIT;
        if (middle) {
            isTarget &= scores.aspectRatioMiddle > ASPECT_RATIO_LIMIT;
        } else {
            isTarget &= scores.aspectRatioHigh > ASPECT_RATIO_LIMIT;
        }
        isTarget &= scores.xEdge > X_EDGE_LIMIT;
        isTarget &= scores.yEdge > Y_EDGE_LIMIT;

        return isTarget;
    }

    /**
     * TargetCompare()
     *
     * This method identifies which target of all the targets scoreCompare()
     * identified is actually the target we are shooting at. if the aspect ratio
     * score of the current particle is larger than the former particle the
     * current particle is substituted for the former. *note: even thought there
     * are two middle targets, based on how the particles are analyzed the
     * target on our current side of the pyramid will be the better target.
     *
     * @param highT the list of all targets determined to be high targets
     * @param middleT the list of all targets determined to be middle targets
     * @param middle true if the target that we are shooting at is the middle
     * target, false if the target we are shooting at is the high target.
     * @return the target we are shooting at.
     */
    AimingSystem.Target TargetCompare(AimingSystem.Target[] highT, AimingSystem.Target[] middleT, boolean middle) {
        AimingSystem.Target t = null;
        if (middle) {
            t.middle = true;
            for (int i = 0; i < middleT.length; i++) {
                if (t == null) {
                    t.aspectRatio = middleT[i].aspectRatio;
                    t.center_mass_x = middleT[i].center_mass_x;
                    t.target_width = middleT[i].target_width;
                } else if (t.aspectRatio < middleT[i].aspectRatio) {
                    t.aspectRatio = middleT[i].aspectRatio;
                    t.center_mass_x = middleT[i].center_mass_x;
                    t.target_width = middleT[i].target_width;
                }
            }
        } else {
            t.middle = false;
            for (int i = 0; i < highT.length; i++) {
                if (t == null) {
                    t.aspectRatio = highT[i].aspectRatio;
                    t.center_mass_x = highT[i].center_mass_x;
                    t.target_width = highT[i].target_width;
                } else if (t.aspectRatio < highT[i].aspectRatio) {
                    t.aspectRatio = highT[i].aspectRatio;
                    t.center_mass_x = highT[i].center_mass_x;
                    t.target_width = highT[i].target_width;
                }
            }
        }
        return t;
    }

    /**
     * isAimedAtTarget()
     *
     * This method determines if we are aimed at the target.
     *
     * @return true if the target is within +/- 1 degree, false if outside of
     * that range
     */
    public boolean isAimedAtTarget() {
        if (getDegreesToTarget() < 1.0 && getDegreesToTarget() > -1.0) {
            return true;
        }
        return false;
    }

    public double pidGet() {
        processImage();
        return getDegreesToTarget();
    }

    /**
     * getDegreesToTarget()
     *
     * This method returns the angle to the target.
     *
     * @return the angle to the target, negative th robot needs to turn left,
     * positive, right
     */
    public double getDegreesToTarget() {
        double offset = 0.0;
        if (target != null) {
            offset = target.center_mass_x - (IMAGE_WIDTH / 2.0);
            offset = offset * ((TARGET_WIDTH * 12.0) / target.target_width);
            offset = MathUtils.atan(offset / getDistanceToTarget());
        }
        return ConvertRadiansToDegrees(offset);
    }

    /**
     * ConvertRadiansToDegrees()
     *
     * This method converts a radian measure to degrees.
     *
     * @param radians an angle in radians
     * @return an angle in degrees
     */
    public double ConvertRadiansToDegrees(double radians) {
        return (radians * 180.0) / 3.1415926535898;
    }

    /**
     * getDistanceWUltrasonic()
     *
     * This method returns the distance to what the robot is facing.
     *
     * @return the distance to what the robot is facing in inches
     */
    public double getDistanceWUltrasonic() {
        return ultrasonicSensor.getDistance();
    }

    /**
     * getDistanceWCamera()
     *
     * This method gets the distance to the target using the camera.
     *
     * @return the distance to what the camera thinks is the target in inches
     */
    public double getDistanceWCamera() {
        double w = 0.0;
        if (target != null) {
            w = (TARGET_WIDTH * IMAGE_WIDTH) / target.target_width;
        }
        return (w / Math.tan(23.5)) * 12.0;
    }

    /**
     * getDistanceToTarget()
     *
     * This method returns what we believe to be the distance to the target. If
     * the camera distance and the ultrasonic distance are outside of +/- 5% of
     * each other, we choose the larger value. If they are within 5% of each
     * other we take the average of both and return that.
     *
     * @return the distance to the target.
     */
    public double getDistanceToTarget() {
        double cameraD = getDistanceWCamera();
        double ultrasonicD = getDistanceWUltrasonic();
        if ((cameraD / ultrasonicD) > 1.05
                || (cameraD / ultrasonicD) < 0.95) {
            return Math.max(cameraD, ultrasonicD);
        } else {
            return (cameraD + ultrasonicD) / 2.0;
        }
    }
}